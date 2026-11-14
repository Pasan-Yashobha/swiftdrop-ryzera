package lk.swiftdrop.tracking.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lk.swiftdrop.tracking.api.PublicTrackingResponse.StopView;
import lk.swiftdrop.tracking.config.TrackingProperties;
import lk.swiftdrop.tracking.domain.CourierFix;
import lk.swiftdrop.tracking.domain.GeoPoint;
import lk.swiftdrop.tracking.domain.ParcelInfo;
import lk.swiftdrop.tracking.domain.ParcelStatus;
import lk.swiftdrop.tracking.domain.ParcelTracking;
import lk.swiftdrop.tracking.domain.Place;
import lk.swiftdrop.tracking.domain.TimelineEntry;
import lk.swiftdrop.tracking.eta.EtaService;
import lk.swiftdrop.tracking.eta.GpsEtaStrategy;
import lk.swiftdrop.tracking.eta.ScheduleEtaStrategy;
import org.junit.jupiter.api.Test;

class PublicTrackingMapperTest {

    private static final Instant T0 = Instant.parse("2026-11-13T02:42:00Z");
    private static final Instant NOW = T0.plus(Duration.ofHours(6));

    private final TrackingProperties properties = new TrackingProperties();
    private final PublicTrackingMapper mapper = new PublicTrackingMapper(
            new EtaService(List.of(new GpsEtaStrategy(properties), new ScheduleEtaStrategy(properties))), properties);

    private static ParcelTracking parcel(ParcelStatus status, ParcelStatus... history) {
        ParcelTracking parcel = new ParcelTracking("SD-2641-7788");
        parcel.setParcelId("parcel-1");
        parcel.setCourierId("courier-1");
        parcel.setStatus(status);
        parcel.setOrigin(new Place("Colombo", new GeoPoint(6.93, 79.86)));
        parcel.setDestination(new Place("Kandy", new GeoPoint(7.29, 80.63)));
        parcel.setParcel(new ParcelInfo("SMALL", new BigDecimal("2.00"), "EXPRESS"));
        Instant at = T0;
        for (ParcelStatus step : history) {
            parcel.getTimeline().add(new TimelineEntry("e-" + step, step, at, null,
                    step == ParcelStatus.FAILED ? "NOT_HOME" : null));
            at = at.plus(Duration.ofHours(1));
        }
        parcel.setStatusChangedAt(at.minus(Duration.ofHours(1)));
        parcel.setVersion(history.length);
        parcel.setUpdatedAt(at);
        return parcel;
    }

    private static List<String> states(PublicTrackingResponse response) {
        return response.stops().stream().map(StopView::state).toList();
    }

    @Test
    void outForDeliveryShowsThreeDoneStopsAndTheCurrentOne() {
        PublicTrackingResponse response = mapper.toResponse(parcel(ParcelStatus.OUT_FOR_DELIVERY,
                ParcelStatus.CREATED, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT,
                ParcelStatus.OUT_FOR_DELIVERY), NOW);

        assertThat(response.trackingNo()).isEqualTo("SD-2641-7788");
        assertThat(response.status()).isEqualTo("OUT_FOR_DELIVERY");
        assertThat(response.statusLabel()).isEqualTo("Out for delivery");
        assertThat(response.tone()).isEqualTo("PROGRESS");
        assertThat(response.stop()).isEqualTo(4);
        assertThat(response.totalStops()).isEqualTo(5);
        assertThat(response.terminal()).isFalse();
        assertThat(states(response)).containsExactly("DONE", "DONE", "DONE", "CURRENT", "PENDING");
        assertThat(response.stops().get(1).at()).isEqualTo(T0.plus(Duration.ofHours(1)));
        assertThat(response.stops().get(4).at()).isNull();
        assertThat(response.origin().city()).isEqualTo("Colombo");
        assertThat(response.destination().point().lat()).isEqualTo(7.29);
        assertThat(response.parcel().serviceType()).isEqualTo("EXPRESS");
        assertThat(response.eta()).isNotNull();
        assertThat(response.deliveredAt()).isNull();
        assertThat(response.failure()).isNull();
        assertThat(response.version()).isEqualTo(4);
    }

    @Test
    void historyIsNewestFirstWithTheObviousCityFilledIn() {
        PublicTrackingResponse response = mapper.toResponse(parcel(ParcelStatus.OUT_FOR_DELIVERY,
                ParcelStatus.CREATED, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT,
                ParcelStatus.OUT_FOR_DELIVERY), NOW);

        assertThat(response.history()).extracting(PublicTrackingResponse.HistoryView::status)
                .containsExactly("OUT_FOR_DELIVERY", "IN_TRANSIT", "PICKED_UP", "CREATED");
        assertThat(response.history().get(0).location()).isEqualTo("Kandy");
        assertThat(response.history().get(2).location()).isEqualTo("Colombo");
        assertThat(response.history().get(1).location()).isNull();
    }

    @Test
    void assignedIsShownAsCreated() {
        PublicTrackingResponse response = mapper.toResponse(
                parcel(ParcelStatus.ASSIGNED, ParcelStatus.CREATED, ParcelStatus.ASSIGNED), NOW);

        assertThat(response.status()).isEqualTo("CREATED");
        assertThat(response.stop()).isEqualTo(1);
        assertThat(states(response)).containsExactly("CURRENT", "PENDING", "PENDING", "PENDING", "PENDING");
        assertThat(response.history()).extracting(PublicTrackingResponse.HistoryView::status)
                .containsExactly("CREATED");
    }

    @Test
    void aFailedAttemptReplacesStopFourAndExplainsWhy() {
        PublicTrackingResponse response = mapper.toResponse(parcel(ParcelStatus.FAILED,
                ParcelStatus.CREATED, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT,
                ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED), NOW);
        ParcelTracking withReason = parcel(ParcelStatus.FAILED, ParcelStatus.CREATED, ParcelStatus.FAILED);
        withReason.setFailureReason("NOT_HOME");

        assertThat(states(response)).containsExactly("DONE", "DONE", "DONE", "EXCEPTION", "PENDING");
        assertThat(response.stops().get(3).status()).isEqualTo("FAILED");
        assertThat(response.stops().get(3).label()).isEqualTo("Delivery attempt failed");
        assertThat(response.tone()).isEqualTo("EXCEPTION");
        assertThat(response.eta()).isNull();
        assertThat(response.history().get(0).note()).isEqualTo("Recipient not at home");
        assertThat(mapper.toResponse(withReason, NOW).failure().code()).isEqualTo("NOT_HOME");
    }

    @Test
    void aRescheduledParcelNeedsAttention() {
        ParcelTracking parcel = parcel(ParcelStatus.RESCHEDULED, ParcelStatus.CREATED, ParcelStatus.FAILED,
                ParcelStatus.RESCHEDULED);
        parcel.setFailureReason("NOT_HOME");

        PublicTrackingResponse response = mapper.toResponse(parcel, NOW);

        assertThat(response.stops().get(3).state()).isEqualTo("ATTENTION");
        assertThat(response.failure().label()).isEqualTo("Recipient not at home");
        assertThat(response.eta()).isNotNull();
    }

    @Test
    void deliveredIsDoneWithoutEstimateOrCourier() {
        ParcelTracking parcel = parcel(ParcelStatus.DELIVERED, ParcelStatus.CREATED, ParcelStatus.PICKED_UP,
                ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED);
        parcel.setCourierPosition(new CourierFix(new GeoPoint(7.29, 80.63), NOW));
        parcel.setFailureReason("NOT_HOME");

        PublicTrackingResponse response = mapper.toResponse(parcel, NOW);

        assertThat(states(response)).containsOnly("DONE");
        assertThat(response.terminal()).isTrue();
        assertThat(response.deliveredAt()).isEqualTo(parcel.getStatusChangedAt());
        assertThat(response.eta()).isNull();
        assertThat(response.courier()).isNull();
        assertThat(response.failure()).isNull();
    }

    @Test
    void cancelledAndReturnedAreExceptions() {
        PublicTrackingResponse cancelled = mapper.toResponse(
                parcel(ParcelStatus.CANCELLED, ParcelStatus.CREATED, ParcelStatus.CANCELLED), NOW);
        PublicTrackingResponse returned = mapper.toResponse(parcel(ParcelStatus.RETURNED, ParcelStatus.CREATED,
                ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED,
                ParcelStatus.RETURNED), NOW);

        assertThat(states(cancelled)).containsExactly("EXCEPTION", "PENDING", "PENDING", "PENDING", "PENDING");
        assertThat(cancelled.stops().get(0).label()).isEqualTo("Cancelled");
        assertThat(states(returned)).containsExactly("DONE", "DONE", "DONE", "DONE", "EXCEPTION");
        assertThat(returned.stops().get(4).label()).isEqualTo("Returned to sender");
    }

    @Test
    void theCourierIsShownWhileOnTheWayWithDistanceToTheDestination() {
        ParcelTracking parcel = parcel(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.CREATED,
                ParcelStatus.OUT_FOR_DELIVERY);
        parcel.setCourierPosition(new CourierFix(new GeoPoint(7.27, 80.61), NOW.minus(Duration.ofMinutes(2))));
        parcel.getCourierTrail().add(new GeoPoint(7.26, 80.60));
        parcel.getCourierTrail().add(new GeoPoint(7.27, 80.61));

        PublicTrackingResponse.CourierView courier = mapper.toResponse(parcel, NOW).courier();

        assertThat(courier.position().lat()).isEqualTo(7.27);
        assertThat(courier.trail()).hasSize(2);
        assertThat(courier.distanceKm()).isBetween(2.0, 4.0);
        assertThat(mapper.toResponse(parcel, NOW).eta().basis()).isEqualTo("GPS");
    }

    @Test
    void aStalePositionIsNotShown() {
        ParcelTracking parcel = parcel(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.CREATED,
                ParcelStatus.OUT_FOR_DELIVERY);
        parcel.setCourierPosition(new CourierFix(new GeoPoint(7.27, 80.61), NOW.minus(Duration.ofHours(1))));

        assertThat(mapper.toResponse(parcel, NOW).courier()).isNull();
    }

    @Test
    void aBareRecordStillMaps() {
        ParcelTracking parcel = new ParcelTracking("SD-1000-0001");

        PublicTrackingResponse response = mapper.toResponse(parcel, NOW);

        assertThat(response.status()).isEqualTo("CREATED");
        assertThat(response.origin()).isNull();
        assertThat(response.parcel()).isNull();
        assertThat(response.history()).isEmpty();
        assertThat(response.stops()).hasSize(5);
    }
}
