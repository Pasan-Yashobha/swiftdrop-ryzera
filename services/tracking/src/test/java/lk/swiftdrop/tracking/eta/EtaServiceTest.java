package lk.swiftdrop.tracking.eta;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lk.swiftdrop.tracking.config.TrackingProperties;
import lk.swiftdrop.tracking.domain.CourierFix;
import lk.swiftdrop.tracking.domain.GeoPoint;
import lk.swiftdrop.tracking.domain.ParcelInfo;
import lk.swiftdrop.tracking.domain.ParcelStatus;
import lk.swiftdrop.tracking.domain.ParcelTracking;
import lk.swiftdrop.tracking.domain.Place;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EtaServiceTest {

    /** 11:30 in Colombo. */
    private static final Instant NOW = Instant.parse("2026-11-13T06:00:00Z");
    private static final GeoPoint KANDY = new GeoPoint(7.29, 80.63);

    private final TrackingProperties properties = new TrackingProperties();
    private final EtaService eta = new EtaService(
            List.of(new GpsEtaStrategy(properties), new ScheduleEtaStrategy(properties)));

    private static ParcelTracking parcel(ParcelStatus status, Instant since, String service) {
        ParcelTracking parcel = new ParcelTracking("SD-1000-0001");
        parcel.setStatus(status);
        parcel.setStatusChangedAt(since);
        parcel.setDestination(new Place("Kandy", KANDY));
        parcel.setParcel(new ParcelInfo("SMALL", BigDecimal.ONE, service));
        return parcel;
    }

    @ParameterizedTest
    @EnumSource(value = ParcelStatus.class, names = {"DELIVERED", "RETURNED", "CANCELLED", "FAILED"})
    void finishedOrFailedParcelsHaveNoEstimate(ParcelStatus status) {
        assertThat(eta.estimate(parcel(status, NOW, "STANDARD"), NOW)).isEmpty();
    }

    @Test
    void unknownStatusHasNoEstimate() {
        assertThat(eta.estimate(parcel(null, NOW, "STANDARD"), NOW)).isEmpty();
    }

    @Test
    void outForDeliveryWithAFreshPositionUsesGps() {
        ParcelTracking parcel = parcel(ParcelStatus.OUT_FOR_DELIVERY, NOW.minus(Duration.ofHours(1)), "STANDARD");
        parcel.setCourierPosition(new CourierFix(new GeoPoint(7.25, 80.60), NOW.minus(Duration.ofMinutes(1))));

        EtaWindow window = eta.estimate(parcel, NOW).orElseThrow();

        assertThat(window.basis()).isEqualTo(EtaWindow.Basis.GPS);
        assertThat(window.from()).isAfterOrEqualTo(NOW);
        assertThat(window.to()).isBefore(NOW.plus(Duration.ofHours(1)));
        assertThat(Duration.between(window.from(), window.to())).isGreaterThanOrEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void aStalePositionFallsBackToTheSchedule() {
        ParcelTracking parcel = parcel(ParcelStatus.OUT_FOR_DELIVERY, NOW.minus(Duration.ofMinutes(30)), "STANDARD");
        parcel.setCourierPosition(new CourierFix(new GeoPoint(7.25, 80.60), NOW.minus(Duration.ofMinutes(30))));

        assertThat(eta.estimate(parcel, NOW).orElseThrow().basis()).isEqualTo(EtaWindow.Basis.SCHEDULE);
    }

    @Test
    void scheduleCountsFromTheStartOfTheCurrentStage() {
        // Out for delivery since 05:30Z: typical 3 h, so expected 08:30Z with a 45 minute window.
        EtaWindow window = eta.estimate(
                parcel(ParcelStatus.OUT_FOR_DELIVERY, NOW.minus(Duration.ofMinutes(30)), "STANDARD"), NOW)
                .orElseThrow();

        assertThat(window.basis()).isEqualTo(EtaWindow.Basis.SCHEDULE);
        assertThat(window.from()).isEqualTo(Instant.parse("2026-11-13T08:05:00Z"));
        assertThat(window.to()).isEqualTo(Instant.parse("2026-11-13T08:55:00Z"));
    }

    @Test
    void expressParcelsTakeHalfAsLong() {
        EtaWindow window = eta.estimate(
                parcel(ParcelStatus.OUT_FOR_DELIVERY, NOW.minus(Duration.ofMinutes(30)), "EXPRESS"), NOW)
                .orElseThrow();

        assertThat(window.from()).isEqualTo(Instant.parse("2026-11-13T06:45:00Z"));
        assertThat(window.to()).isEqualTo(Instant.parse("2026-11-13T07:15:00Z"));
    }

    @Test
    void aLateParcelIsExpectedFromNow() {
        EtaWindow window = eta.estimate(
                parcel(ParcelStatus.OUT_FOR_DELIVERY, NOW.minus(Duration.ofHours(5)), "STANDARD"), NOW)
                .orElseThrow();

        assertThat(window.from()).isEqualTo(NOW);
        assertThat(window.to()).isEqualTo(NOW.plus(Duration.ofMinutes(45)));
    }

    @Test
    void anEstimateAfterDeliveryHoursMovesToTheNextMorning() {
        // In transit since 15:30 local: typical 8 h lands at 23:30, so next day 08:00-10:00 local.
        Instant evening = Instant.parse("2026-11-13T10:00:00Z");
        EtaWindow window = eta.estimate(parcel(ParcelStatus.IN_TRANSIT, evening, "STANDARD"), evening).orElseThrow();

        assertThat(window.from()).isEqualTo(Instant.parse("2026-11-14T02:30:00Z"));
        assertThat(window.to()).isEqualTo(Instant.parse("2026-11-14T04:30:00Z"));
    }

    @Test
    void anEstimateBeforeDeliveryHoursMovesToTheMorning() {
        // Out for delivery since 03:00 local: typical 3 h lands at 06:00 local, so 08:00 local + half window.
        Instant night = Instant.parse("2026-11-12T21:30:00Z");
        EtaWindow window = eta.estimate(parcel(ParcelStatus.OUT_FOR_DELIVERY, night, "STANDARD"), night)
                .orElseThrow();

        assertThat(window.from()).isEqualTo(Instant.parse("2026-11-13T02:30:00Z"));
        assertThat(window.to()).isEqualTo(Instant.parse("2026-11-13T03:15:00Z"));
    }

    @Test
    void createdAndRescheduledParcelsGetADayLongEstimate() {
        assertThat(eta.estimate(parcel(ParcelStatus.CREATED, NOW, "STANDARD"), NOW)).isPresent();
        assertThat(eta.estimate(parcel(ParcelStatus.ASSIGNED, NOW, "STANDARD"), NOW)).isPresent();
        assertThat(eta.estimate(parcel(ParcelStatus.RESCHEDULED, NOW, "STANDARD"), NOW)).isPresent();
        assertThat(eta.estimate(parcel(ParcelStatus.PICKED_UP, null, null), NOW)).isPresent();
    }

    @Test
    void windowsAreRoundedOutwardsToFiveMinutes() {
        EtaWindow window = EtaWindow.around(Instant.parse("2026-11-13T08:31:10Z"), Duration.ofMinutes(20),
                Instant.parse("2026-11-13T06:00:00Z"), EtaWindow.Basis.GPS);

        assertThat(window.from()).isEqualTo(Instant.parse("2026-11-13T08:20:00Z"));
        assertThat(window.to()).isEqualTo(Instant.parse("2026-11-13T08:45:00Z"));
    }

    @Test
    void distanceIsHaversine() {
        double km = Geo.distanceKm(new GeoPoint(6.93, 79.86), KANDY);

        assertThat(km).isBetween(90.0, 100.0);
        assertThat(Geo.distanceKm(KANDY, KANDY)).isZero();
    }
}
