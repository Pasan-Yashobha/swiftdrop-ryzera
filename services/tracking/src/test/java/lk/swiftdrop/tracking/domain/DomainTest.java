package lk.swiftdrop.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DomainTest {

    @Test
    void trackingNumbersAreNormalisedToUpperCase() {
        assertThat(TrackingNumbers.normalize("  sd-2641-7788 ")).contains("SD-2641-7788");
        assertThat(TrackingNumbers.normalize("SD24000123")).contains("SD24000123");
        assertThat(TrackingNumbers.normalize("SBX123456789")).contains("SBX123456789");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "-SD-1234", "SD-1234-", "SD 1234 5678", "SD_1234", "SD-1234-7788<script>",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void invalidTrackingNumbersAreRejected(String value) {
        assertThat(TrackingNumbers.normalize(value)).isEmpty();
    }

    @Test
    void nullTrackingNumberIsRejected() {
        assertThat(TrackingNumbers.normalize(null)).isEmpty();
    }

    @Test
    void statusTokensFromRoutingKeysAndPayloads() {
        assertThat(ParcelStatus.fromToken("out_for_delivery")).contains(ParcelStatus.OUT_FOR_DELIVERY);
        assertThat(ParcelStatus.fromToken("Picked-Up")).contains(ParcelStatus.PICKED_UP);
        assertThat(ParcelStatus.fromToken("in transit")).contains(ParcelStatus.IN_TRANSIT);
        assertThat(ParcelStatus.fromToken("lost")).isEmpty();
        assertThat(ParcelStatus.fromToken(" ")).isEmpty();
        assertThat(ParcelStatus.fromToken(null)).isEmpty();
    }

    @Test
    void assignedIsInternalAndShownAsCreated() {
        assertThat(ParcelStatus.ASSIGNED.publicStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(ParcelStatus.DELIVERED.publicStatus()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(ParcelStatus.DELIVERED.terminal()).isTrue();
        assertThat(ParcelStatus.FAILED.terminal()).isFalse();
        assertThat(ParcelStatus.FAILED.stop()).isEqualTo(4);
        assertThat(ParcelStatus.RESCHEDULED.tone()).isEqualTo(ParcelStatus.Tone.ATTENTION);
    }

    @Test
    void failureReasonsNeverEchoFreeText() {
        assertThat(FailureReason.fromCode("recipient_unavailable")).isEqualTo(FailureReason.NOT_HOME);
        assertThat(FailureReason.fromCode("Wrong address")).isEqualTo(FailureReason.ADDRESS_NOT_FOUND);
        assertThat(FailureReason.fromCode("REFUSED")).isEqualTo(FailureReason.REFUSED);
        assertThat(FailureReason.fromCode("parcel-damaged")).isEqualTo(FailureReason.DAMAGED);
        assertThat(FailureReason.fromCode("gate locked")).isEqualTo(FailureReason.ACCESS_RESTRICTED);
        assertThat(FailureReason.fromCode("bad weather")).isEqualTo(FailureReason.WEATHER);
        assertThat(FailureReason.fromCode("Nimali was out, call 0771234567")).isEqualTo(FailureReason.OTHER);
        assertThat(FailureReason.fromCode(null)).isEqualTo(FailureReason.OTHER);
        assertThat(FailureReason.NOT_HOME.label()).isEqualTo("Recipient not at home");
    }

    @Test
    void geoPointsAreRoundedAndValidated() {
        assertThat(GeoPoint.roundedOrNull(7.290612, 80.633712, GeoPoint.CITY_PRECISION))
                .isEqualTo(new GeoPoint(7.29, 80.63));
        assertThat(GeoPoint.roundedOrNull(7.290612, 80.633712, GeoPoint.COURIER_PRECISION))
                .isEqualTo(new GeoPoint(7.291, 80.634));
        assertThat(GeoPoint.roundedOrNull(91.0, 80.0, 2)).isNull();
        assertThat(GeoPoint.roundedOrNull(7.0, null, 2)).isNull();
        assertThat(GeoPoint.roundedOrNull(Double.NaN, 80.0, 2)).isNull();
        assertThat(new GeoPoint(7.2906, 80.6337).rounded(1)).isEqualTo(new GeoPoint(7.3, 80.6));
        assertThatThrownBy(() -> new GeoPoint(0, 200)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expressIsRecognised() {
        assertThat(new ParcelInfo("SMALL", BigDecimal.ONE, "EXPRESS").express()).isTrue();
        assertThat(new ParcelInfo("SMALL", BigDecimal.ONE, "STANDARD").express()).isFalse();
        assertThat(new ParcelInfo("SMALL", BigDecimal.ONE, null).express()).isFalse();
    }

    @Test
    void recentEventIdsAreBounded() {
        ParcelTracking parcel = new ParcelTracking("SD-1000-0001");
        for (int i = 0; i < ParcelTracking.RECENT_EVENT_IDS + 10; i++) {
            parcel.rememberEvent("event-" + i);
        }
        parcel.rememberEvent(null);
        assertThat(parcel.getRecentEventIds()).hasSize(ParcelTracking.RECENT_EVENT_IDS);
        assertThat(parcel.hasApplied("event-0")).isFalse();
        assertThat(parcel.hasApplied("event-" + (ParcelTracking.RECENT_EVENT_IDS + 9))).isTrue();
        assertThat(parcel.hasApplied(null)).isFalse();
    }
}
