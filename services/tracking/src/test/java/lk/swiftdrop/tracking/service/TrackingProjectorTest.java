package lk.swiftdrop.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.tracking.config.TrackingProperties;
import lk.swiftdrop.tracking.domain.GeoPoint;
import lk.swiftdrop.tracking.domain.ParcelInfo;
import lk.swiftdrop.tracking.domain.ParcelStatus;
import lk.swiftdrop.tracking.domain.ParcelTracking;
import lk.swiftdrop.tracking.domain.Place;
import lk.swiftdrop.tracking.domain.TimelineEntry;
import lk.swiftdrop.tracking.event.TrackingEvent;
import lk.swiftdrop.tracking.event.TrackingEvent.Kind;
import org.junit.jupiter.api.Test;

class TrackingProjectorTest {

    private static final String NO = "SD-2641-7788";
    private static final Instant T0 = Instant.parse("2026-11-13T02:00:00Z");
    private static final Instant NOW = T0.plus(Duration.ofHours(6));

    private final TrackingProperties properties = new TrackingProperties();
    private final TrackingProjector projector = new TrackingProjector(properties);

    private static Instant at(int minutes) {
        return T0.plus(Duration.ofMinutes(minutes));
    }

    private static TrackingEvent created(Long sequence, int minutes) {
        return TrackingEvent.builder(UUID.randomUUID().toString(), Kind.PARCEL_CREATED, at(minutes))
                .trackingNo(NO).parcelId("parcel-1").status(ParcelStatus.CREATED).sequence(sequence)
                .origin(new Place("Colombo", new GeoPoint(6.93, 79.86)))
                .destination(new Place("Kandy", new GeoPoint(7.29, 80.63)))
                .parcel(new ParcelInfo("SMALL", new BigDecimal("2.00"), "STANDARD"))
                .build();
    }

    private static TrackingEvent status(ParcelStatus status, Long sequence, int minutes) {
        Kind kind = status == ParcelStatus.CANCELLED ? Kind.CANCELLED : Kind.STATUS_CHANGED;
        return TrackingEvent.builder(UUID.randomUUID().toString(), kind, at(minutes))
                .trackingNo(NO).status(status).sequence(sequence).build();
    }

    private static TrackingEvent failed(String reason, Long sequence, int minutes) {
        return TrackingEvent.builder(UUID.randomUUID().toString(), Kind.STATUS_CHANGED, at(minutes))
                .trackingNo(NO).status(ParcelStatus.FAILED).sequence(sequence).failureReason(reason).build();
    }

    private static TrackingEvent assigned(String courierId, int minutes) {
        return TrackingEvent.builder(UUID.randomUUID().toString(), Kind.ASSIGNED, at(minutes))
                .trackingNo(NO).status(ParcelStatus.ASSIGNED).courierId(courierId).sequence(99L).build();
    }

    private static TrackingEvent location(String courierId, double lat, double lng, int minutes) {
        return TrackingEvent.builder(UUID.randomUUID().toString(), Kind.COURIER_LOCATION, at(minutes))
                .trackingNo(NO).courierId(courierId).position(new GeoPoint(lat, lng)).build();
    }

    /** Applies events like the store does: each event sees the result of the previous ones. */
    private ParcelTracking play(TrackingEvent... events) {
        Optional<ParcelTracking> state = Optional.empty();
        for (TrackingEvent event : events) {
            Optional<ParcelTracking> next = projector.apply(state, event, NOW);
            if (next.isPresent()) {
                state = next;
            }
        }
        return state.orElseThrow();
    }

    private static ParcelStatus[] timelineStatuses(ParcelTracking parcel) {
        return parcel.getTimeline().stream().map(TimelineEntry::status).toArray(ParcelStatus[]::new);
    }

    @Test
    void eventsInOrderMoveTheParcelAlong() {
        ParcelTracking parcel = play(
                created(1L, 0),
                assigned("courier-1", 5),
                status(ParcelStatus.PICKED_UP, 3L, 120),
                status(ParcelStatus.IN_TRANSIT, 4L, 180),
                status(ParcelStatus.OUT_FOR_DELIVERY, 5L, 300));

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.OUT_FOR_DELIVERY);
        assertThat(parcel.getStatusChangedAt()).isEqualTo(at(300));
        assertThat(parcel.getVersion()).isEqualTo(5);
        assertThat(parcel.getUpdatedAt()).isEqualTo(NOW);
        assertThat(parcel.getLastSequence()).isEqualTo(5L);
        assertThat(parcel.getCourierId()).isEqualTo("courier-1");
        assertThat(parcel.getOrigin().city()).isEqualTo("Colombo");
        assertThat(parcel.getParcelId()).isEqualTo("parcel-1");
        assertThat(timelineStatuses(parcel)).containsExactly(ParcelStatus.CREATED, ParcelStatus.PICKED_UP,
                ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY);
    }

    @Test
    void aDuplicateChangesNothing() {
        TrackingEvent pickedUp = status(ParcelStatus.PICKED_UP, 2L, 60);
        ParcelTracking parcel = play(created(1L, 0), pickedUp);

        assertThat(projector.apply(Optional.of(parcel), pickedUp, NOW)).isEmpty();
    }

    @Test
    void anOlderSequenceIsKeptInHistoryButDoesNotMoveTheStatusBack() {
        ParcelTracking parcel = play(
                created(1L, 0),
                status(ParcelStatus.IN_TRANSIT, 4L, 180),
                status(ParcelStatus.PICKED_UP, 3L, 120));

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(parcel.getLastSequence()).isEqualTo(4L);
        assertThat(timelineStatuses(parcel)).containsExactly(ParcelStatus.CREATED, ParcelStatus.PICKED_UP,
                ParcelStatus.IN_TRANSIT);
    }

    @Test
    void theSequenceWinsOverTheClock() {
        ParcelTracking parcel = play(
                created(1L, 0),
                status(ParcelStatus.OUT_FOR_DELIVERY, 5L, 60),
                status(ParcelStatus.IN_TRANSIT, 4L, 120));

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.OUT_FOR_DELIVERY);
    }

    @Test
    void withoutSequencesTheEventTimeDecides() {
        ParcelTracking parcel = play(
                created(null, 0),
                status(ParcelStatus.OUT_FOR_DELIVERY, null, 300),
                status(ParcelStatus.IN_TRANSIT, null, 180));

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.OUT_FOR_DELIVERY);
        assertThat(parcel.getLastEventAt()).isEqualTo(at(300));
    }

    @Test
    void aStatusBeforeTheCreatedEventStillCreatesTheRecord() {
        ParcelTracking parcel = play(
                status(ParcelStatus.PICKED_UP, 2L, 60),
                created(1L, 0));

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(parcel.getDestination().city()).isEqualTo("Kandy");
        assertThat(parcel.getCreatedAt()).isEqualTo(at(0));
        assertThat(timelineStatuses(parcel)).containsExactly(ParcelStatus.CREATED, ParcelStatus.PICKED_UP);
    }

    @Test
    void assignmentIsInternalAndCannotMoveAPickedUpParcelBack() {
        ParcelTracking assignedOnly = play(created(1L, 0), assigned("courier-1", 5));
        assertThat(assignedOnly.getStatus()).isEqualTo(ParcelStatus.ASSIGNED);
        assertThat(timelineStatuses(assignedOnly)).containsExactly(ParcelStatus.CREATED);

        ParcelTracking parcel = play(
                created(1L, 0),
                assigned("courier-1", 5),
                status(ParcelStatus.PICKED_UP, 3L, 60),
                assigned("courier-2", 90));

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(parcel.getCourierId()).isEqualTo("courier-2");
    }

    @Test
    void reassignmentChangesTheCourierAndCanReturnTheParcelToCreated() {
        TrackingEvent reassigned = TrackingEvent.builder("re-1", Kind.REASSIGNED, at(30))
                .trackingNo(NO).courierId("courier-2").build();
        TrackingEvent unassigned = TrackingEvent.builder("re-2", Kind.REASSIGNED, at(40))
                .trackingNo(NO).status(ParcelStatus.CREATED).build();

        ParcelTracking parcel = play(created(1L, 0), assigned("courier-1", 5), reassigned);
        assertThat(parcel.getCourierId()).isEqualTo("courier-2");
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.ASSIGNED);

        ParcelTracking back = play(created(1L, 0), assigned("courier-1", 5), unassigned);
        assertThat(back.getStatus()).isEqualTo(ParcelStatus.CREATED);
    }

    @Test
    void aLateAssignmentWithoutNewInformationChangesNothing() {
        ParcelTracking parcel = play(created(1L, 0), assigned("courier-1", 60));
        TrackingEvent older = TrackingEvent.builder("old", Kind.ASSIGNED, at(10)).trackingNo(NO)
                .status(ParcelStatus.ASSIGNED).courierId("courier-1").build();

        assertThat(projector.apply(Optional.of(parcel), older, NOW)).isEmpty();
    }

    @Test
    void aFailedAttemptKeepsItsReasonUntilTheParcelMovesOn() {
        ParcelTracking failed = play(
                created(1L, 0),
                status(ParcelStatus.OUT_FOR_DELIVERY, 2L, 60),
                failed("NOT_HOME", 3L, 120));
        assertThat(failed.getStatus()).isEqualTo(ParcelStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("NOT_HOME");
        assertThat(failed.getTimeline().get(failed.getTimeline().size() - 1).reason()).isEqualTo("NOT_HOME");

        ParcelTracking rescheduled = play(
                created(1L, 0),
                status(ParcelStatus.OUT_FOR_DELIVERY, 2L, 60),
                failed("NOT_HOME", 3L, 120),
                status(ParcelStatus.RESCHEDULED, 4L, 130));
        assertThat(rescheduled.getFailureReason()).isEqualTo("NOT_HOME");

        ParcelTracking retried = play(
                created(1L, 0),
                status(ParcelStatus.OUT_FOR_DELIVERY, 2L, 60),
                failed("NOT_HOME", 3L, 120),
                status(ParcelStatus.RESCHEDULED, 4L, 130),
                status(ParcelStatus.OUT_FOR_DELIVERY, 5L, 1500));
        assertThat(retried.getStatus()).isEqualTo(ParcelStatus.OUT_FOR_DELIVERY);
        assertThat(retried.getFailureReason()).isNull();
    }

    @Test
    void aFailureWithoutReasonIsOther() {
        ParcelTracking parcel = play(created(1L, 0), failed(null, 2L, 60));

        assertThat(parcel.getFailureReason()).isEqualTo("OTHER");
        assertThat(parcel.getTimeline().get(1).reason()).isEqualTo("OTHER");
    }

    @Test
    void theCourierPositionIsSharedOnlyWhileTheParcelIsOnItsWay() {
        ParcelTracking waiting = play(created(1L, 0), assigned("courier-1", 5));
        assertThat(projector.apply(Optional.of(waiting), location("courier-1", 7.0, 80.0, 6), NOW)).isEmpty();

        ParcelTracking moving = play(
                created(1L, 0),
                assigned("courier-1", 5),
                status(ParcelStatus.OUT_FOR_DELIVERY, 3L, 60),
                location("courier-1", 7.25, 80.6, 61));
        assertThat(moving.getCourierPosition().point()).isEqualTo(new GeoPoint(7.25, 80.6));
        assertThat(moving.getCourierPosition().at()).isEqualTo(at(61));
        assertThat(moving.getCourierTrail()).containsExactly(new GeoPoint(7.25, 80.6));

        ParcelTracking delivered = play(
                created(1L, 0),
                assigned("courier-1", 5),
                status(ParcelStatus.OUT_FOR_DELIVERY, 3L, 60),
                location("courier-1", 7.25, 80.6, 61),
                status(ParcelStatus.DELIVERED, 4L, 90));
        assertThat(delivered.getCourierPosition()).isNull();
        assertThat(delivered.getCourierTrail()).isEmpty();
        assertThat(delivered.getCourierId()).isNull();
    }

    @Test
    void positionsFromAnotherCourierOrOlderThanTheLastAreIgnored() {
        ParcelTracking parcel = play(
                created(1L, 0),
                assigned("courier-1", 5),
                status(ParcelStatus.IN_TRANSIT, 3L, 60),
                location("courier-1", 7.1, 80.1, 70));

        assertThat(projector.apply(Optional.of(parcel), location("courier-2", 7.2, 80.2, 75), NOW)).isEmpty();
        assertThat(projector.apply(Optional.of(parcel), location("courier-1", 7.0, 80.0, 65), NOW)).isEmpty();
    }

    @Test
    void theTrailIsCapped() {
        properties.setTrailLimit(3);
        ParcelTracking parcel = play(
                created(1L, 0),
                status(ParcelStatus.IN_TRANSIT, 2L, 10),
                location("courier-1", 7.01, 80.01, 11),
                location("courier-1", 7.02, 80.02, 12),
                location("courier-1", 7.03, 80.03, 13),
                location("courier-1", 7.04, 80.04, 14),
                location("courier-1", 7.04, 80.04, 15));

        assertThat(parcel.getCourierTrail()).containsExactly(
                new GeoPoint(7.02, 80.02), new GeoPoint(7.03, 80.03), new GeoPoint(7.04, 80.04));
        assertThat(parcel.getCourierPosition().at()).isEqualTo(at(15));
    }

    @Test
    void aPositionNeverCreatesAParcel() {
        assertThat(projector.apply(Optional.empty(), location("courier-1", 7.0, 80.0, 1), NOW)).isEmpty();
    }

    @Test
    void cancellationIsFinal() {
        ParcelTracking parcel = play(created(1L, 0), assigned("courier-1", 5), status(ParcelStatus.CANCELLED, 3L, 30));

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CANCELLED);
        assertThat(parcel.getStatus().terminal()).isTrue();
        assertThat(parcel.getCourierId()).isNull();
    }

    @Test
    void theTimelineIsCapped() {
        properties.setTimelineLimit(2);
        ParcelTracking parcel = play(
                created(1L, 0),
                status(ParcelStatus.PICKED_UP, 2L, 10),
                status(ParcelStatus.IN_TRANSIT, 3L, 20));

        assertThat(timelineStatuses(parcel)).containsExactly(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT);
    }

    @Test
    void theSameStatusAtTheSameTimeIsRecordedOnce() {
        ParcelTracking parcel = play(created(1L, 0), status(ParcelStatus.PICKED_UP, null, 10));
        TrackingEvent again = status(ParcelStatus.PICKED_UP, null, 10);

        Optional<ParcelTracking> result = projector.apply(Optional.of(parcel), again, NOW);

        assertThat(result).isPresent();
        assertThat(timelineStatuses(result.get())).containsExactly(ParcelStatus.CREATED, ParcelStatus.PICKED_UP);
    }
}
