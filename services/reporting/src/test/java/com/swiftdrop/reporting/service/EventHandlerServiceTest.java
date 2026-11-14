package com.swiftdrop.reporting.service;

import com.swiftdrop.reporting.common.ParcelFactNotFoundException;
import com.swiftdrop.reporting.config.AppProperties;
import com.swiftdrop.reporting.domain.CourierState;
import com.swiftdrop.reporting.domain.ParcelFact;
import com.swiftdrop.reporting.dto.EventEnvelope;
import com.swiftdrop.reporting.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventHandlerServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private ParcelFactRepository parcelFactRepository;
    @Mock
    private DailyDeliveryStatsRepository dailyDeliveryStatsRepository;
    @Mock
    private CourierDailyStatsRepository courierDailyStatsRepository;
    @Mock
    private ZoneDailyStatsRepository zoneDailyStatsRepository;
    @Mock
    private MerchantDailyStatsRepository merchantDailyStatsRepository;
    @Mock
    private CourierStateRepository courierStateRepository;

    private AppProperties appProperties;
    private EventHandlerService service;

    private static final String TIMEZONE = "Asia/Colombo";
    private static final ZoneId ZONE = ZoneId.of(TIMEZONE);

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.setTimezone(TIMEZONE);
        service = new EventHandlerService(
                appProperties,
                processedEventRepository,
                parcelFactRepository,
                dailyDeliveryStatsRepository,
                courierDailyStatsRepository,
                zoneDailyStatsRepository,
                merchantDailyStatsRepository,
                courierStateRepository
        );
    }

    @Test
    @DisplayName("Guest-list idempotency: duplicate event is skipped")
    void testDuplicateEventSkipped() {
        UUID eventId = UUID.randomUUID();
        EventEnvelope envelope = new EventEnvelope(
                eventId, "parcel.created", Instant.parse("2026-11-05T09:00:00Z"),
                "corr-1", Map.of("parcelId", UUID.randomUUID().toString())
        );

        when(processedEventRepository.insertIfAbsent(eventId, "reporting")).thenReturn(0);

        boolean processed = service.handle(envelope);

        assertThat(processed).isFalse();
        verify(dailyDeliveryStatsRepository, never()).incrementCreated(any());
        verify(parcelFactRepository, never()).save(any());
    }

    @Test
    @DisplayName("parcel.created inserts parcel_fact and increments daily, zone, merchant counters")
    void testParcelCreated() {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        UUID pickupZoneId = UUID.randomUUID();
        UUID dropoffZoneId = UUID.randomUUID();
        BigDecimal fee = new BigDecimal("450.00");

        Map<String, Object> payload = Map.of(
                "parcelId", parcelId.toString(),
                "trackingNo", "TRK-001",
                "merchantId", merchantId.toString(),
                "pickupZoneId", pickupZoneId.toString(),
                "dropoffZoneId", dropoffZoneId.toString(),
                "fee", fee
        );
        Instant occurredAt = Instant.parse("2026-11-05T08:00:00Z");
        LocalDate expectedDay = occurredAt.atZone(ZONE).toLocalDate();

        EventEnvelope envelope = new EventEnvelope(eventId, "parcel.created", occurredAt, "corr-1", payload);
        when(processedEventRepository.insertIfAbsent(eventId, "reporting")).thenReturn(1);
        when(parcelFactRepository.findById(parcelId)).thenReturn(Optional.empty());

        boolean processed = service.handle(envelope);

        assertThat(processed).isTrue();

        ArgumentCaptor<ParcelFact> factCaptor = ArgumentCaptor.forClass(ParcelFact.class);
        verify(parcelFactRepository).save(factCaptor.capture());
        ParcelFact savedFact = factCaptor.getValue();
        assertThat(savedFact.getParcelId()).isEqualTo(parcelId);
        assertThat(savedFact.getTrackingNo()).isEqualTo("TRK-001");
        assertThat(savedFact.getCurrentStatus()).isEqualTo("CREATED");
        assertThat(savedFact.getFee()).isEqualTo(fee);

        verify(dailyDeliveryStatsRepository).incrementCreated(expectedDay);
        verify(zoneDailyStatsRepository).incrementCreated(expectedDay, dropoffZoneId, fee);
        verify(merchantDailyStatsRepository).incrementCreated(expectedDay, merchantId);
    }

    @Test
    @DisplayName("parcel.assigned sets courier on fact and increments courier assigned_count")
    void testParcelAssigned() {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-11-05T08:30:00Z");
        LocalDate expectedDay = occurredAt.atZone(ZONE).toLocalDate();

        ParcelFact fact = new ParcelFact();
        fact.setParcelId(parcelId);
        fact.setCurrentStatus("CREATED");
        fact.setLastEventAt(OffsetDateTime.parse("2026-11-05T08:00:00Z"));

        EventEnvelope envelope = new EventEnvelope(eventId, "parcel.assigned", occurredAt, "corr-1",
                Map.of("parcelId", parcelId.toString(), "courierId", courierId.toString()));

        when(processedEventRepository.insertIfAbsent(eventId, "reporting")).thenReturn(1);
        when(parcelFactRepository.findById(parcelId)).thenReturn(Optional.of(fact));

        service.handle(envelope);

        assertThat(fact.getCourierId()).isEqualTo(courierId);
        assertThat(fact.getCurrentStatus()).isEqualTo("ASSIGNED");
        verify(parcelFactRepository).save(fact);
        verify(courierDailyStatsRepository).incrementAssigned(expectedDay, courierId);
    }

    @Test
    @DisplayName("parcel.reassigned increments old courier reassigned_away and new courier assigned")
    void testParcelReassigned() {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        UUID oldCourierId = UUID.randomUUID();
        UUID newCourierId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-11-05T08:45:00Z");
        LocalDate expectedDay = occurredAt.atZone(ZONE).toLocalDate();

        ParcelFact fact = new ParcelFact();
        fact.setParcelId(parcelId);
        fact.setCourierId(oldCourierId);
        fact.setCurrentStatus("ASSIGNED");
        fact.setLastEventAt(OffsetDateTime.parse("2026-11-05T08:30:00Z"));

        EventEnvelope envelope = new EventEnvelope(eventId, "parcel.reassigned", occurredAt, "corr-1",
                Map.of("parcelId", parcelId.toString(),
                        "courierId", newCourierId.toString(),
                        "previousCourierId", oldCourierId.toString()));

        when(processedEventRepository.insertIfAbsent(eventId, "reporting")).thenReturn(1);
        when(parcelFactRepository.findById(parcelId)).thenReturn(Optional.of(fact));

        service.handle(envelope);

        assertThat(fact.getCourierId()).isEqualTo(newCourierId);
        verify(courierDailyStatsRepository).incrementReassignedAway(expectedDay, oldCourierId);
        verify(courierDailyStatsRepository).incrementAssigned(expectedDay, newCourierId);
    }

    @Test
    @DisplayName("parcel.status.delivered computes delivery duration when ofd_at is known")
    void testParcelDeliveredWithDuration() {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        Instant ofdTime = Instant.parse("2026-11-05T09:00:00Z");
        Instant deliveredTime = Instant.parse("2026-11-05T09:40:00Z"); // 40 minutes duration
        LocalDate expectedDay = deliveredTime.atZone(ZONE).toLocalDate();

        ParcelFact fact = new ParcelFact();
        fact.setParcelId(parcelId);
        fact.setCourierId(courierId);
        fact.setDropoffZoneId(zoneId);
        fact.setMerchantId(merchantId);
        fact.setOfdAt(ofdTime.atZone(ZONE).toOffsetDateTime());
        fact.setCurrentStatus("OUT_FOR_DELIVERY");
        fact.setLastEventAt(ofdTime.atZone(ZONE).toOffsetDateTime());

        EventEnvelope envelope = new EventEnvelope(eventId, "parcel.status.delivered", deliveredTime, "corr-1",
                Map.of("parcelId", parcelId.toString(), "courierId", courierId.toString()));

        when(processedEventRepository.insertIfAbsent(eventId, "reporting")).thenReturn(1);
        when(parcelFactRepository.findById(parcelId)).thenReturn(Optional.of(fact));

        service.handle(envelope);

        verify(dailyDeliveryStatsRepository).incrementDelivered(expectedDay);
        verify(zoneDailyStatsRepository).incrementDelivered(expectedDay, zoneId);
        verify(merchantDailyStatsRepository).incrementDelivered(expectedDay, merchantId);
        verify(courierDailyStatsRepository).incrementDelivered(expectedDay, courierId);
        verify(courierDailyStatsRepository).addDeliveryMinutes(expectedDay, courierId, 40L);
    }

    @Test
    @DisplayName("Status event arriving before parcel.created throws ParcelFactNotFoundException")
    void testStatusEventBeforeCreatedThrowsRetryableException() {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        EventEnvelope envelope = new EventEnvelope(
                eventId, "parcel.status.delivered", Instant.now(), "corr-1",
                Map.of("parcelId", parcelId.toString())
        );

        when(processedEventRepository.insertIfAbsent(eventId, "reporting")).thenReturn(1);
        when(parcelFactRepository.findById(parcelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(envelope))
                .isInstanceOf(ParcelFactNotFoundException.class)
                .hasMessageContaining(parcelId.toString());
    }

    @Test
    @DisplayName("courier.availability.changed updates courier_state table")
    void testCourierAvailabilityChanged() {
        UUID eventId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-11-05T14:00:00Z");

        EventEnvelope envelope = new EventEnvelope(eventId, "courier.availability.changed", occurredAt, "corr-1",
                Map.of("courierId", courierId.toString(), "available", true, "activeDeliveries", 3));

        when(processedEventRepository.insertIfAbsent(eventId, "reporting")).thenReturn(1);

        service.handle(envelope);

        ArgumentCaptor<CourierState> stateCaptor = ArgumentCaptor.forClass(CourierState.class);
        verify(courierStateRepository).save(stateCaptor.capture());
        CourierState state = stateCaptor.getValue();
        assertThat(state.getCourierId()).isEqualTo(courierId);
        assertThat(state.isAvailable()).isTrue();
        assertThat(state.getActiveDeliveries()).isEqualTo(3);
    }
}
