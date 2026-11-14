package lk.swiftdrop.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import lk.swiftdrop.tracking.config.TrackingProperties;
import lk.swiftdrop.tracking.domain.ParcelStatus;
import lk.swiftdrop.tracking.domain.ParcelTracking;
import lk.swiftdrop.tracking.event.EnvelopeParser;
import lk.swiftdrop.tracking.event.EventMapper;
import lk.swiftdrop.tracking.testsupport.DirectBroadcaster;
import lk.swiftdrop.tracking.testsupport.Events;
import lk.swiftdrop.tracking.testsupport.InMemoryTrackingStore;
import org.junit.jupiter.api.Test;

class TrackingUpdateServiceTest {

    private static final String NO = "SD-2641-7788";
    private static final Instant T0 = Instant.parse("2026-11-13T02:00:00Z");

    private final InMemoryTrackingStore store = new InMemoryTrackingStore(Events.MAPPER);
    private final DirectBroadcaster broadcaster = new DirectBroadcaster(null);
    private final EnvelopeParser parser = new EnvelopeParser(Events.MAPPER);
    private final TrackingUpdateService service = new TrackingUpdateService(new EventMapper(),
            new TrackingProjector(new TrackingProperties()), store, broadcaster,
            Clock.fixed(T0.plus(Duration.ofHours(1)), ZoneOffset.UTC));

    private void handle(Events.Builder event) {
        service.handle(parser.parse(event.bytes()));
    }

    private ParcelTracking parcel() {
        return store.find(NO).orElseThrow();
    }

    @Test
    void eachChangeIsStoredAndPublished() {
        handle(Events.created(NO, "parcel-1", 1, T0));
        handle(Events.status("picked_up", NO, 2, T0.plusSeconds(60)));

        assertThat(parcel().getStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(broadcaster.published()).hasSize(2);
        assertThat(broadcaster.published().get(1).getVersion()).isEqualTo(2);
        assertThat(store.findTrackingNo("parcel-1")).contains(NO);
    }

    @Test
    void aDuplicateDeliveryIsIgnored() {
        Events.Builder created = Events.created(NO, "parcel-1", 1, T0);

        handle(created);
        handle(created);

        assertThat(broadcaster.published()).hasSize(1);
        assertThat(parcel().getVersion()).isEqualTo(1);
    }

    @Test
    void eventsThatOnlyNameTheParcelIdAreResolvedThroughTheIndex() {
        handle(Events.created(NO, "parcel-1", 1, T0));
        handle(Events.event("parcel.assigned").at(T0.plusSeconds(30)).data("parcelId", "parcel-1")
                .data("courierId", "courier-1"));

        assertThat(parcel().getStatus()).isEqualTo(ParcelStatus.ASSIGNED);
        assertThat(parcel().getCourierId()).isEqualTo("courier-1");
        assertThat(store.parcelsOfCourier("courier-1")).containsExactly(NO);
    }

    @Test
    void anUnknownParcelIdIsRetriedLater() {
        Events.Builder assigned = Events.event("parcel.assigned").data("parcelId", "parcel-404")
                .data("courierId", "courier-1");

        assertThatThrownBy(() -> handle(assigned)).isInstanceOf(UnresolvedParcelException.class);
        // The failed attempt must not be remembered, or the retry would be dropped as a duplicate.
        assertThat(store.isProcessed(assigned.eventId())).isFalse();
    }

    @Test
    void reassignmentMovesTheParcelToTheNewCourier() {
        handle(Events.created(NO, "parcel-1", 1, T0));
        handle(Events.event("parcel.assigned").at(T0.plusSeconds(30)).data("trackingNo", NO)
                .data("courierId", "courier-1"));
        handle(Events.event("parcel.reassigned").at(T0.plusSeconds(60)).data("trackingNo", NO)
                .data("courierId", "courier-2"));

        assertThat(store.parcelsOfCourier("courier-1")).isEmpty();
        assertThat(store.parcelsOfCourier("courier-2")).containsExactly(NO);
    }

    @Test
    void courierPositionsReachTheParcelsTheCourierCarries() {
        handle(Events.created(NO, "parcel-1", 1, T0));
        handle(Events.event("parcel.assigned").at(T0.plusSeconds(30)).data("trackingNo", NO)
                .data("courierId", "courier-1"));
        handle(Events.status("out_for_delivery", NO, 3, T0.plusSeconds(60)));
        broadcaster.clear();

        handle(Events.location("courier-1", 7.25, 80.6, T0.plusSeconds(90)));

        assertThat(parcel().getCourierPosition()).isNotNull();
        assertThat(broadcaster.published()).hasSize(1);
    }

    @Test
    void aPositionNamingTheParcelDirectlyIsAppliedToIt() {
        handle(Events.created(NO, "parcel-1", 1, T0));
        handle(Events.status("in_transit", NO, 2, T0.plusSeconds(60)));

        handle(Events.location("courier-9", 7.1, 80.1, T0.plusSeconds(90)).data("parcelId", "parcel-1"));
        assertThat(parcel().getCourierPosition().point().lat()).isEqualTo(7.1);

        handle(Events.location("courier-9", 7.2, 80.2, T0.plusSeconds(120)).data("trackingNo", NO));
        assertThat(parcel().getCourierPosition().point().lat()).isEqualTo(7.2);
    }

    @Test
    void deliveryReleasesTheCourier() {
        handle(Events.created(NO, "parcel-1", 1, T0));
        handle(Events.event("parcel.assigned").at(T0.plusSeconds(30)).data("trackingNo", NO)
                .data("courierId", "courier-1"));
        handle(Events.status("delivered", NO, 3, T0.plusSeconds(60)));

        assertThat(store.parcelsOfCourier("courier-1")).isEmpty();
    }

    @Test
    void eventsOtherServicesCareAboutAreIgnored() {
        handle(Events.event("merchant.approved").data("merchantId", "m-1"));

        assertThat(broadcaster.published()).isEmpty();
    }

    @Test
    void anEventThatChangesNothingIsNotPublished() {
        handle(Events.created(NO, "parcel-1", 1, T0));
        handle(Events.status("in_transit", NO, 3, T0.plusSeconds(120)));
        broadcaster.clear();

        handle(Events.location("courier-1", 7.25, 80.6, T0.plusSeconds(130)));

        assertThat(broadcaster.published()).isEmpty();
    }
}
