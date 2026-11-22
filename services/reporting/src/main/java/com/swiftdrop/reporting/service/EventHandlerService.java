package com.swiftdrop.reporting.service;

import com.swiftdrop.reporting.common.ParcelFactNotFoundException;
import com.swiftdrop.reporting.config.AppProperties;
import com.swiftdrop.reporting.domain.CourierState;
import com.swiftdrop.reporting.domain.ParcelFact;
import com.swiftdrop.reporting.dto.EventEnvelope;
import com.swiftdrop.reporting.event.EventPayloadHelper;
import com.swiftdrop.reporting.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Core event handling logic for the reporting service.
 * All updates for a single incoming event execute within ONE database transaction.
 *
 * Each event type has a dedicated method to facilitate direct unit testing.
 */
@Service
public class EventHandlerService {

    private static final Logger log = LoggerFactory.getLogger(EventHandlerService.class);
    private static final String CONSUMER_NAME = "reporting";

    private final AppProperties appProperties;
    private final ProcessedEventRepository processedEventRepository;
    private final ParcelFactRepository parcelFactRepository;
    private final DailyDeliveryStatsRepository dailyDeliveryStatsRepository;
    private final CourierDailyStatsRepository courierDailyStatsRepository;
    private final ZoneDailyStatsRepository zoneDailyStatsRepository;
    private final MerchantDailyStatsRepository merchantDailyStatsRepository;
    private final CourierStateRepository courierStateRepository;

    public EventHandlerService(
            AppProperties appProperties,
            ProcessedEventRepository processedEventRepository,
            ParcelFactRepository parcelFactRepository,
            DailyDeliveryStatsRepository dailyDeliveryStatsRepository,
            CourierDailyStatsRepository courierDailyStatsRepository,
            ZoneDailyStatsRepository zoneDailyStatsRepository,
            MerchantDailyStatsRepository merchantDailyStatsRepository,
            CourierStateRepository courierStateRepository) {
        this.appProperties = appProperties;
        this.processedEventRepository = processedEventRepository;
        this.parcelFactRepository = parcelFactRepository;
        this.dailyDeliveryStatsRepository = dailyDeliveryStatsRepository;
        this.courierDailyStatsRepository = courierDailyStatsRepository;
        this.zoneDailyStatsRepository = zoneDailyStatsRepository;
        this.merchantDailyStatsRepository = merchantDailyStatsRepository;
        this.courierStateRepository = courierStateRepository;
    }

    /**
     * Entry point for processing an event envelope.
     * Executes in ONE transaction: guest-list check -> update parcel_fact -> atomic UPSERT counters.
     *
     * @return true if the event was processed, false if it was a duplicate and skipped.
     */
    @Transactional
    public boolean handle(EventEnvelope envelope) {
        if (envelope == null || !envelope.isValid()) {
            throw new IllegalArgumentException("Invalid event envelope");
        }

        // 1. Guest-list check: insert into processed_event with ON CONFLICT DO NOTHING
        int inserted = processedEventRepository.insertIfAbsent(envelope.eventId(), CONSUMER_NAME);
        if (inserted == 0) {
            log.info("Duplicate event {} ({}), skipping processing", envelope.eventId(), envelope.eventType());
            return false;
        }

        // 2. Derive day in reporting.timezone (Asia/Colombo)
        ZoneId zoneId = ZoneId.of(appProperties.getTimezone());
        LocalDate day = envelope.occurredAt().atZone(zoneId).toLocalDate();
        OffsetDateTime occurredAtOffset = envelope.occurredAt().atZone(zoneId).toOffsetDateTime();

        // 3. Dispatch to handler based on eventType
        String eventType = envelope.eventType().trim();
        switch (eventType) {
            case "parcel.created" -> handleParcelCreated(envelope, day, occurredAtOffset);
            case "parcel.assigned" -> handleParcelAssigned(envelope, day, occurredAtOffset);
            case "parcel.reassigned" -> handleParcelReassigned(envelope, day, occurredAtOffset);
            case "parcel.status.picked_up" -> handleParcelStatusPickedUp(envelope, day, occurredAtOffset);
            case "parcel.status.in_transit" -> handleParcelStatusInTransit(envelope, day, occurredAtOffset);
            case "parcel.status.out_for_delivery" -> handleParcelStatusOutOfDelivery(envelope, day, occurredAtOffset);
            case "parcel.status.delivered" -> handleParcelStatusDelivered(envelope, day, occurredAtOffset);
            case "parcel.status.failed" -> handleParcelStatusFailed(envelope, day, occurredAtOffset);
            case "parcel.status.rescheduled" -> handleParcelStatusRescheduled(envelope, day, occurredAtOffset);
            case "parcel.status.returned" -> handleParcelStatusReturned(envelope, day, occurredAtOffset);
            case "parcel.cancelled" -> handleParcelCancelled(envelope, day, occurredAtOffset);
            case "courier.availability.changed" -> handleCourierAvailabilityChanged(envelope, day, occurredAtOffset);
            default -> log.debug("Unhandled event type: {}", eventType);
        }

        return true;
    }

    public void handleParcelCreated(EventEnvelope envelope, LocalDate day, OffsetDateTime occurredAt) {
        UUID parcelId = EventPayloadHelper.getUuid(envelope.payload(), "parcelId");
        String trackingNo = EventPayloadHelper.getString(envelope.payload(), "trackingNo");
        if (trackingNo == null) {
            trackingNo = EventPayloadHelper.getString(envelope.payload(), "trackingNumber");
        }
        UUID merchantId = EventPayloadHelper.getUuid(envelope.payload(), "merchantId");
        UUID pickupZoneId = EventPayloadHelper.getUuid(envelope.payload(), "pickupZoneId");
        UUID dropoffZoneId = EventPayloadHelper.getUuid(envelope.payload(), "dropoffZoneId");
        BigDecimal fee = EventPayloadHelper.getBigDecimal(envelope.payload(), "fee");

        // Insert parcel_fact
        ParcelFact fact = parcelFactRepository.findById(parcelId).orElse(new ParcelFact());
        fact.setParcelId(parcelId);
        fact.setTrackingNo(trackingNo);
        fact.setMerchantId(merchantId);
        fact.setPickupZoneId(pickupZoneId);
        fact.setDropoffZoneId(dropoffZoneId);
        fact.setFee(fee);
        fact.setCurrentStatus("CREATED");
        fact.setLastEventAt(occurredAt);
        parcelFactRepository.save(fact);

        // Atomic counters: created +1 in daily, zone (dropoff zone) and merchant tables; add fee to zone fee_total
        dailyDeliveryStatsRepository.incrementCreated(day);
        if (dropoffZoneId != null) {
            zoneDailyStatsRepository.incrementCreated(day, dropoffZoneId, fee != null ? fee : BigDecimal.ZERO);
        }
        if (merchantId != null) {
            merchantDailyStatsRepository.incrementCreated(day, merchantId);
        }
    }

    public void handleParcelAssigned(EventEnvelope envelope, LocalDate day, OffsetDateTime occurredAt) {
        UUID parcelId = EventPayloadHelper.getUuid(envelope.payload(), "parcelId");
        UUID courierId = EventPayloadHelper.getUuid(envelope.payload(), "courierId");

        ParcelFact fact = parcelFactRepository.findById(parcelId)
                .orElseThrow(() -> new ParcelFactNotFoundException(parcelId));

        if (courierId != null) {
            fact.setCourierId(courierId);
        }
        if (fact.getLastEventAt() == null || occurredAt.isAfter(fact.getLastEventAt())) {
            fact.setCurrentStatus("ASSIGNED");
            fact.setLastEventAt(occurredAt);
        }
        parcelFactRepository.save(fact);

        if (courierId != null) {
            courierDailyStatsRepository.incrementAssigned(day, courierId);
        }
    }

    public void handleParcelReassigned(EventEnvelope envelope, LocalDate day, OffsetDateTime occurredAt) {
        UUID parcelId = EventPayloadHelper.getUuid(envelope.payload(), "parcelId");
        UUID courierId = EventPayloadHelper.getUuid(envelope.payload(), "courierId");
        UUID previousCourierId = EventPayloadHelper.getUuid(envelope.payload(), "previousCourierId");

        ParcelFact fact = parcelFactRepository.findById(parcelId)
                .orElseThrow(() -> new ParcelFactNotFoundException(parcelId));

        if (previousCourierId == null) {
            previousCourierId = fact.getCourierId();
        }
        if (courierId != null) {
            fact.setCourierId(courierId);
        }
        if (fact.getLastEventAt() == null || occurredAt.isAfter(fact.getLastEventAt())) {
            fact.setCurrentStatus("ASSIGNED");
            fact.setLastEventAt(occurredAt);
        }
        parcelFactRepository.save(fact);

        if (previousCourierId != null) {
            courierDailyStatsRepository.incrementReassignedAway(day, previousCourierId);
        }
        if (courierId != null) {
            courierDailyStatsRepository.incrementAssigned(day, courierId);
        }
    }

    public void handleParcelStatusPickedUp(EventEnvelope envelope, LocalDate day, OffsetDateTime occurredAt) {
        UUID parcelId = EventPayloadHelper.getUuid(envelope.payload(), "parcelId");
        ParcelFact fact = parcelFactRepository.findById(parcelId)
                .orElseThrow(() -> new ParcelFactNotFoundException(parcelId));

        UUID courierId = EventPayloadHelper.getUuid(envelope.payload(), "courierId");
        if (courierId != null) {
            fact.setCourierId(courierId);
        }
        if (fact.getLastEventAt() == null || occurredAt.isAfter(fact.getLastEventAt())) {
            fact.setCurrentStatus("PICKED_UP");
            fact.setLastEventAt(occurredAt);
            parcelFactRepository.save(fact);
        }
    }

    public void handleParcelStatusInTransit(EventEnvelope envelope, LocalDate day, OffsetDateTime occurredAt) {
        UUID parcelId = EventPayloadHelper.getUuid(envelope.payload(), "parcelId");
        ParcelFact fact = parcelFactRepository.findById(parcelId)
                .orElseThrow(() -> new ParcelFactNotFoundException(parcelId));

        UUID courierId = EventPayloadHelper.getUuid(envelope.payload(), "courierId");
        if (courierId != null) {
            fact.setCourierId(courierId);
        }
        if (fact.getLastEventAt() == null || occurredAt.isAfter(fact.getLastEventAt())) {
            fact.setCurrentStatus("IN_TRANSIT");
            fact.setLastEventAt(occurredAt);
            parcelFactRepository.save(fact);
        }
    }

    public void handleParcelStatusOutOfDelivery(EventEnvelope envelope, LocalDate day, OffsetDateTime occurredAt) {
        UUID parcelId = EventPayloadHelper.getUuid(envelope.payload(), "parcelId");
        ParcelFact fact = parcelFactRepository.findById(parcelId)
                .orElseThrow(() -> new ParcelFactNotFoundException(parcelId));

        UUID courierId = EventPayloadHelper.getUuid(envelope.payload(), "courierId");
        if (courierId != null) {
            fact.setCourierId(courierId);
        }
        if (fact.getLastEventAt() == null || occurredAt.isAfter(fact.getLastEventAt())) {
            fact.setCurrentStatus("OUT_FOR_DELIVERY");
            fact.setLastEventAt(occurredAt);
        }

        boolean ofdWasNull = (fact.getOfdAt() == null);
        if (ofdWasNull || occurredAt.isAfter(fact.getOfdAt())) {
            fact.setOfdAt(occurredAt);
        }

        // If delivered_at already known and ofd was previously unknown (out-of-order delivery), compute duration now
        if (ofdWasNull && fact.getDeliveredAt() != null) {
            UUID cid = fact.getCourierId() != null ? fact.getCourierId() : courierId;
            if (cid != null) {
                long minutes = Math.max(0, Duration.between(occurredAt, fact.getDeliveredAt()).toMinutes());
                ZoneId zoneId = ZoneId.of(appProperties.getTimezone());
                LocalDate deliveryDay = fact.getDeliveredAt().atZoneSameInstant(zoneId).toLocalDate();
                courierDailyStatsRepository.addDeliveryMinutes(deliveryDay, cid, minutes);
            }
        }

        parcelFactRepository.save(fact);
    }

    public void handleParcelStatusDelivered(EventEnvelope envelope, LocalDate day, OffsetDateTime occurredAt) {
        UUID parcelId = EventPayloadHelper.getUuid(envelope.payload(), "parcelId");
        ParcelFact fact = parcelFactRepository.findById(parcelId)
                .orElseThrow(() -> new ParcelFactNotFoundException(parcelId));

        UUID courierId = EventPayloadHelper.getUuid(envelope.payload(), "courierId");
        if (courierId != null) {
            fact.setCourierId(courierId);
        } else {
            courierId = fact.getCourierId();
        }

        if (fact.getLastEventAt() == null || occurredAt.isAfter(fact.getLastEventAt())) {
            fact.setCurrentStatus("DELIVERED");
            fact.setLastEventAt(occurredAt);
        }
        fact.setDeliveredAt(occurredAt);
        parcelFactRepository.save(fact);

        // delivered +1 in daily, zone, merchant, courier
        dailyDeliveryStatsRepository.incrementDelivered(day);
        if (fact.getDropoffZoneId() != null) {
            zoneDailyStatsRepository.incrementDelivered(day, fact.getDropoffZoneId());
        }
        if (fact.getMerchantId() != null) {
            merchantDailyStatsRepository.incrementDelivered(day, fact.getMerchantId());
        }
        if (courierId != null) {
            courierDailyStatsRepository.incrementDelivered(day, courierId);
            // If ofd_at known: courier total_minutes += minutes, timed_count +1
            if (fact.getOfdAt() != null) {
                long minutes = Math.max(0, Duration.between(fact.getOfdAt(), occurredAt).toMinutes());
                courierDailyStatsRepository.addDeliveryMinutes(day, courierId, minutes);
            }
        }
    }

    public void handleParcelStatusFailed(EventEnvelope envelope, LocalDate day, OffsetDateTime occurredAt) {
        UUID parcelId = EventPayloadHelper.getUuid(envelope.payload(), "parcelId");
        ParcelFact fact = parcelFactRepository.findById(parcelId)
                .orElseThrow(() -> new ParcelFactNotFoundException(parcelId));

        UUID courierId = EventPayloadHelper.getUuid(envelope.payload(), "courierId");
        if (courierId != null) {
            fact.setCourierId(courierId);
        } else {
            courierId = fact.getCourierId();
        }

        if (fact.getLastEventAt() == null || occurredAt.isAfter(fact.getLastEventAt())) {
            fact.setCurrentStatus("FAILED");
            fact.setLastEventAt(occurredAt);
            parcelFactRepository.save(fact);
        }

        // failed +1 in daily, zone, merchant; courier failed_attempts +1
        dailyDeliveryStatsRepository.incrementFailed(day);
        if (fact.getDropoffZoneId() != null) {
            zoneDailyStatsRepository.incrementFailed(day, fact.getDropoffZoneId());
        }
        if (fact.getMerchantId() != null) {
            merchantDailyStatsRepository.incrementFailed(day, fact.getMerchantId());
        }
        if (courierId != null) {
            courierDailyStatsRepository.incrementFailedAttempts(day, courierId);
        }
    }

    public void handleParcelStatusRescheduled(EventEnvelope envelope, LocalDate day, OffsetDateTime occurredAt) {
        UUID parcelId = EventPayloadHelper.getUuid(envelope.payload(), "parcelId");
        ParcelFact fact = parcelFactRepository.findById(parcelId)
                .orElseThrow(() -> new ParcelFactNotFoundException(parcelId));

        if (fact.getLastEventAt() == null || occurredAt.isAfter(fact.getLastEventAt())) {
            fact.setCurrentStatus("RESCHEDULED");
            fact.setLastEventAt(occurredAt);
            parcelFactRepository.save(fact);
        }

        dailyDeliveryStatsRepository.incrementRescheduled(day);
    }

    public void handleParcelStatusReturned(EventEnvelope envelope, LocalDate day, OffsetDateTime occurredAt) {
        UUID parcelId = EventPayloadHelper.getUuid(envelope.payload(), "parcelId");
        ParcelFact fact = parcelFactRepository.findById(parcelId)
                .orElseThrow(() -> new ParcelFactNotFoundException(parcelId));

        if (fact.getLastEventAt() == null || occurredAt.isAfter(fact.getLastEventAt())) {
            fact.setCurrentStatus("RETURNED");
            fact.setLastEventAt(occurredAt);
            parcelFactRepository.save(fact);
        }

        dailyDeliveryStatsRepository.incrementReturned(day);
    }

    public void handleParcelCancelled(EventEnvelope envelope, LocalDate day, OffsetDateTime occurredAt) {
        UUID parcelId = EventPayloadHelper.getUuid(envelope.payload(), "parcelId");
        ParcelFact fact = parcelFactRepository.findById(parcelId)
                .orElseThrow(() -> new ParcelFactNotFoundException(parcelId));

        if (fact.getLastEventAt() == null || occurredAt.isAfter(fact.getLastEventAt())) {
            fact.setCurrentStatus("CANCELLED");
            fact.setLastEventAt(occurredAt);
            parcelFactRepository.save(fact);
        }

        dailyDeliveryStatsRepository.incrementCancelled(day);
    }

    public void handleCourierAvailabilityChanged(EventEnvelope envelope, LocalDate day, OffsetDateTime occurredAt) {
        UUID courierId = EventPayloadHelper.getUuid(envelope.payload(), "courierId");
        if (courierId == null) {
            log.warn("courier.availability.changed event missing courierId");
            return;
        }

        boolean available = EventPayloadHelper.getBoolean(envelope.payload(), "available");
        int activeDeliveries = EventPayloadHelper.getInt(envelope.payload(), "activeDeliveries");

        CourierState state = new CourierState();
        state.setCourierId(courierId);
        state.setAvailable(available);
        state.setActiveDeliveries(activeDeliveries);
        state.setUpdatedAt(occurredAt);
        courierStateRepository.save(state);
    }
}
