package lk.swiftdrop.tracking.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lk.swiftdrop.tracking.domain.GeoPoint;
import lk.swiftdrop.tracking.domain.ParcelStatus;
import lk.swiftdrop.tracking.event.TrackingEvent.Kind;
import lk.swiftdrop.tracking.testsupport.Events;
import org.junit.jupiter.api.Test;

class EventMapperTest {

    private static final Instant AT = Instant.parse("2026-11-13T08:30:00Z");

    private final EnvelopeParser parser = new EnvelopeParser(Events.MAPPER);
    private final EventMapper mapper = new EventMapper();

    private Optional<TrackingEvent> map(Events.Builder event) {
        return mapper.map(parser.parse(event.bytes()));
    }

    @Test
    void createdKeepsOnlyWhitelistedFields() {
        Events.Builder created = Events.created("sd-2641-7788", "parcel-1", 1, AT)
                .data("recipient", Map.of("name", "Nimali Perera", "phone", "+94771234567"))
                .data("recipientName", "Nimali Perera")
                .data("dropoff", Map.of("addressLine", "45 Temple Street", "postalCode", "20000"))
                .data("notes", "Call Nimali on +94771234567");

        TrackingEvent event = map(created).orElseThrow();

        assertThat(event.kind()).isEqualTo(Kind.PARCEL_CREATED);
        assertThat(event.status()).isEqualTo(ParcelStatus.CREATED);
        assertThat(event.trackingNo()).isEqualTo("SD-2641-7788");
        assertThat(event.parcelId()).isEqualTo("parcel-1");
        assertThat(event.sequence()).isEqualTo(1L);
        assertThat(event.origin().city()).isEqualTo("Colombo");
        assertThat(event.origin().point()).isEqualTo(new GeoPoint(6.93, 79.86));
        assertThat(event.destination().city()).isEqualTo("Kandy");
        assertThat(event.destination().point()).isEqualTo(new GeoPoint(7.29, 80.63));
        assertThat(event.parcel().size()).isEqualTo("SMALL");
        assertThat(event.parcel().weightKg()).isEqualByComparingTo(new BigDecimal("2.00"));
        assertThat(event.parcel().serviceType()).isEqualTo("STANDARD");
        assertThat(event.toString()).doesNotContain("Nimali", "+9477", "Temple", "20000");
    }

    @Test
    void nestedPlaceFieldsAndAlternativeNamesAreUnderstood() {
        Events.Builder created = Events.event("parcel.created").at(AT)
                .data("trackingNumber", "SD-1000-0001")
                .data("pickup", Map.of("city", "Galle", "lat", 6.0535, "lng", 80.221))
                .data("destination", Map.of("city", "Jaffna"))
                .data("parcel", Map.of("size", "large", "weightKg", "3.456"))
                .data("deliveryType", "express");

        TrackingEvent event = map(created).orElseThrow();

        assertThat(event.trackingNo()).isEqualTo("SD-1000-0001");
        assertThat(event.origin().city()).isEqualTo("Galle");
        assertThat(event.origin().point()).isEqualTo(new GeoPoint(6.05, 80.22));
        assertThat(event.destination().city()).isEqualTo("Jaffna");
        assertThat(event.destination().point()).isNull();
        assertThat(event.parcel().size()).isEqualTo("LARGE");
        assertThat(event.parcel().weightKg()).isEqualByComparingTo(new BigDecimal("3.46"));
        assertThat(event.parcel().express()).isTrue();
    }

    @Test
    void implausibleParcelValuesAreDropped() {
        Events.Builder created = Events.event("parcel.created").at(AT)
                .data("trackingNo", "SD-1000-0001")
                .data("weightKg", -2)
                .data("size", "a very long size description that is not a size");

        assertThat(map(created).orElseThrow().parcel()).isNull();
    }

    @Test
    void statusComesFromTheRoutingKey() {
        TrackingEvent event = map(Events.status("out_for_delivery", "SD-1000-0001", 5, AT)
                .data("hub", "Kandy hub")
                .data("courierId", "courier-1")).orElseThrow();

        assertThat(event.kind()).isEqualTo(Kind.STATUS_CHANGED);
        assertThat(event.status()).isEqualTo(ParcelStatus.OUT_FOR_DELIVERY);
        assertThat(event.hub()).isEqualTo("Kandy hub");
        assertThat(event.courierId()).isEqualTo("courier-1");
        assertThat(event.origin()).isNull();
    }

    @Test
    void statusFallsBackToThePayload() {
        TrackingEvent event = map(Events.event("parcel.status.changed").at(AT)
                .data("trackingNo", "SD-1000-0001")
                .data("toStatus", "IN_TRANSIT")).orElseThrow();

        assertThat(event.status()).isEqualTo(ParcelStatus.IN_TRANSIT);
    }

    @Test
    void unknownStatusIsIgnored() {
        assertThat(map(Events.event("parcel.status.lost").at(AT).data("trackingNo", "SD-1000-0001"))).isEmpty();
    }

    @Test
    void assignmentAndCancellationKinds() {
        assertThat(map(Events.event("parcel.assigned").data("parcelId", "p-1").data("courierId", "c-1"))
                .orElseThrow().kind()).isEqualTo(Kind.ASSIGNED);
        TrackingEvent reassigned = map(Events.event("parcel.reassigned").data("parcelId", "p-1")
                .data("newCourierId", "c-2")).orElseThrow();
        assertThat(reassigned.kind()).isEqualTo(Kind.REASSIGNED);
        assertThat(reassigned.courierId()).isEqualTo("c-2");
        assertThat(reassigned.status()).isNull();
        assertThat(map(Events.event("parcel.cancelled").data("trackingNo", "SD-1000-0001")).orElseThrow().status())
                .isEqualTo(ParcelStatus.CANCELLED);
        assertThat(map(Events.event("parcel.status.cancelled").data("trackingNo", "SD-1000-0001")).orElseThrow()
                .kind()).isEqualTo(Kind.CANCELLED);
        assertThat(map(Events.event("parcel.status.assigned").data("trackingNo", "SD-1000-0001")).orElseThrow()
                .kind()).isEqualTo(Kind.ASSIGNED);
    }

    @Test
    void failureReasonIsMappedToAPublicCode() {
        TrackingEvent coded = map(Events.status("failed", "SD-1000-0001", 6, AT)
                .data("failureReason", "RECIPIENT_UNAVAILABLE")).orElseThrow();
        TrackingEvent freeText = map(Events.status("failed", "SD-1000-0001", 6, AT)
                .data("reason", "Nimali was out, call 0771234567")).orElseThrow();
        TrackingEvent delivered = map(Events.status("delivered", "SD-1000-0001", 6, AT)
                .data("failureReason", "NOT_HOME")).orElseThrow();

        assertThat(coded.failureReason()).isEqualTo("NOT_HOME");
        assertThat(freeText.failureReason()).isEqualTo("OTHER");
        assertThat(delivered.failureReason()).isNull();
    }

    @Test
    void parcelIdFallsBackToTheAggregateId() {
        TrackingEvent event = map(Events.event("parcel.assigned").aggregateId("parcel-9").data("courierId", "c-1"))
                .orElseThrow();

        assertThat(event.parcelId()).isEqualTo("parcel-9");
        assertThat(event.trackingNo()).isNull();
    }

    @Test
    void parcelEventWithoutAnyIdIsInvalid() {
        assertThatThrownBy(() -> map(Events.status("delivered", "x", 1, AT)))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void courierLocationIsRoundedToAbout100Metres() {
        TrackingEvent event = map(Events.location("courier-1", 7.281234, 80.612345, AT)).orElseThrow();

        assertThat(event.kind()).isEqualTo(Kind.COURIER_LOCATION);
        assertThat(event.courierId()).isEqualTo("courier-1");
        assertThat(event.position()).isEqualTo(new GeoPoint(7.281, 80.612));
        assertThat(event.occurredAt()).isEqualTo(AT);
    }

    @Test
    void courierLocationAcceptsNestedCoordinatesAndAggregateId() {
        TrackingEvent event = map(Events.event("courier.location.updated").at(AT).aggregateId("courier-2")
                .data("location", Map.of("latitude", 7.0, "longitude", 80.0))).orElseThrow();

        assertThat(event.courierId()).isEqualTo("courier-2");
        assertThat(event.position()).isEqualTo(new GeoPoint(7.0, 80.0));
    }

    @Test
    void courierLocationWithoutCoordinatesOrTargetIsInvalid() {
        assertThatThrownBy(() -> map(Events.event("courier.location.updated").data("courierId", "c-1")))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> map(Events.event("courier.location.updated").data("lat", 7.0).data("lng", 80.0)))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void otherEventTypesAreNotUsed() {
        assertThat(map(Events.event("merchant.approved").data("merchantId", "m-1"))).isEmpty();
        assertThat(map(Events.event("parcel.label.printed").data("trackingNo", "SD-1000-0001"))).isEmpty();
    }

    @Test
    void namesThatLookLikeContactDetailsAreDropped() {
        assertThat(EventMapper.safeName("Kandy hub")).isEqualTo("Kandy hub");
        assertThat(EventMapper.safeName("  Colombo   03 ")).isEqualTo("Colombo 03");
        assertThat(EventMapper.safeName("Call 0771234567")).isNull();
        assertThat(EventMapper.safeName("nimali@example.com")).isNull();
        assertThat(EventMapper.safeName("x".repeat(61))).isNull();
        assertThat(EventMapper.safeName("   ")).isNull();
        assertThat(EventMapper.safeName(null)).isNull();
        assertThat(EventMapper.safeId("bad id")).isNull();
        assertThat(EventMapper.safeToken("same-day")).isEqualTo("SAME_DAY");
        assertThat(EventMapper.safeToken("2kg")).isNull();
        assertThat(EventMapper.safeToken(null)).isNull();
    }
}
