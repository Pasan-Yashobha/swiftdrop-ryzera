package lk.swiftdrop.tracking.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lk.swiftdrop.tracking.config.TrackingProperties;
import lk.swiftdrop.tracking.domain.CourierFix;
import lk.swiftdrop.tracking.domain.FailureReason;
import lk.swiftdrop.tracking.domain.GeoPoint;
import lk.swiftdrop.tracking.domain.ParcelStatus;
import lk.swiftdrop.tracking.domain.ParcelTracking;
import lk.swiftdrop.tracking.domain.TimelineEntry;
import lk.swiftdrop.tracking.event.TrackingEvent;
import lk.swiftdrop.tracking.event.TrackingEvent.Kind;
import org.springframework.stereotype.Component;

/**
 * Applies one event to the read model of a parcel. Pure logic, no I/O, so every ordering rule is unit tested.
 * <ul>
 *   <li><b>Ordering.</b> When the producer sends a per-parcel {@code sequence}, an event whose sequence is not newer
 *       than the last one applied does not change the status. Without a sequence, {@code occurredAt} decides.
 *       A late event is still added to the history, so the timeline is complete, but never moves the status back.</li>
 *   <li><b>Duplicates.</b> The ids of recently applied events are kept; a duplicate changes nothing.</li>
 *   <li><b>Courier position.</b> Kept only while the status is in {@code share-location-statuses}
 *       and only from the courier the parcel is assigned to; dropped at every other status.</li>
 * </ul>
 */
@Component
public class TrackingProjector {

    private static final List<ParcelStatus> ASSIGNABLE = List.of(ParcelStatus.CREATED, ParcelStatus.ASSIGNED);

    private final TrackingProperties properties;

    public TrackingProjector(TrackingProperties properties) {
        this.properties = properties;
    }

    /**
     * @param current the stored state, empty for a parcel not seen yet
     * @return the changed state (version incremented), or empty when the event changes nothing
     */
    public Optional<ParcelTracking> apply(Optional<ParcelTracking> current, TrackingEvent event, Instant now) {
        if (event.kind() == Kind.COURIER_LOCATION) {
            // A position never creates a parcel record.
            return current.filter(tracking -> applyLocation(tracking, event)).map(tracking -> touch(tracking, now));
        }

        boolean isNew = current.isEmpty();
        ParcelTracking tracking = current.orElseGet(() -> newRecord(event));
        if (tracking.hasApplied(event.eventId())) {
            return Optional.empty();
        }

        boolean changed = switch (event.kind()) {
            case PARCEL_CREATED -> applyCreated(tracking, event);
            case STATUS_CHANGED, CANCELLED -> applyStatus(tracking, event);
            case ASSIGNED, REASSIGNED -> applyAssignment(tracking, event);
            case COURIER_LOCATION -> false;
        };
        if (!changed && !isNew) {
            return Optional.empty();
        }
        tracking.rememberEvent(event.eventId());
        return Optional.of(touch(tracking, now));
    }

    public boolean sharesLocation(ParcelStatus status) {
        return status != null && properties.getShareLocationStatuses().contains(status);
    }

    private ParcelTracking newRecord(TrackingEvent event) {
        ParcelTracking tracking = new ParcelTracking(event.trackingNo());
        tracking.setParcelId(event.parcelId());
        tracking.setCreatedAt(event.occurredAt());
        return tracking;
    }

    private boolean applyCreated(ParcelTracking tracking, TrackingEvent event) {
        boolean changed = false;
        if (event.parcelId() != null && !event.parcelId().equals(tracking.getParcelId())) {
            tracking.setParcelId(event.parcelId());
            changed = true;
        }
        if (event.origin() != null) {
            tracking.setOrigin(event.origin());
            changed = true;
        }
        if (event.destination() != null) {
            tracking.setDestination(event.destination());
            changed = true;
        }
        if (event.parcel() != null) {
            tracking.setParcel(event.parcel());
            changed = true;
        }
        if (tracking.getCreatedAt() == null || event.occurredAt().isBefore(tracking.getCreatedAt())) {
            tracking.setCreatedAt(event.occurredAt());
            changed = true;
        }
        changed |= addToTimeline(tracking, event, ParcelStatus.CREATED);
        // parcel.created is the first event by definition: it sets a status only when none is known yet.
        if (tracking.getStatus() == null) {
            tracking.setStatus(ParcelStatus.CREATED);
            tracking.setStatusChangedAt(event.occurredAt());
            changed = true;
        }
        recordOrdering(tracking, event);
        return changed;
    }

    private boolean applyStatus(ParcelTracking tracking, TrackingEvent event) {
        ParcelStatus target = event.status();
        boolean newer = isNewer(tracking, event);
        boolean changed = addToTimeline(tracking, event, target);
        if (!newer) {
            return changed;
        }

        if (tracking.getStatus() != target) {
            tracking.setStatus(target);
            tracking.setStatusChangedAt(event.occurredAt());
        }
        if (target == ParcelStatus.FAILED) {
            tracking.setFailureReason(event.failureReason() != null ? event.failureReason() : FailureReason.OTHER.name());
        } else if (target != ParcelStatus.RESCHEDULED && target != ParcelStatus.RETURNED) {
            // RESCHEDULED and RETURNED keep the reason of the failed attempt; any forward status clears it.
            tracking.setFailureReason(null);
        }
        if (event.courierId() != null) {
            tracking.setCourierId(event.courierId());
        }
        if (target.terminal()) {
            tracking.setCourierId(null);
        }
        if (!sharesLocation(target)) {
            tracking.setCourierPosition(null);
            tracking.getCourierTrail().clear();
        }
        recordOrdering(tracking, event);
        return true;
    }

    private boolean applyAssignment(ParcelTracking tracking, TrackingEvent event) {
        boolean newer = isNewer(tracking, event);
        boolean changed = false;
        if (event.parcelId() != null && tracking.getParcelId() == null) {
            tracking.setParcelId(event.parcelId());
            changed = true;
        }
        if (event.courierId() != null && !event.courierId().equals(tracking.getCourierId())
                && (newer || tracking.getCourierId() == null)) {
            tracking.setCourierId(event.courierId());
            changed = true;
        }
        ParcelStatus target = event.kind() == Kind.ASSIGNED ? ParcelStatus.ASSIGNED : event.status();
        if (newer && target != null && canMoveTo(tracking.getStatus(), target) && tracking.getStatus() != target) {
            tracking.setStatus(target);
            tracking.setStatusChangedAt(event.occurredAt());
            changed = true;
        }
        if (newer) {
            recordOrdering(tracking, event);
            changed = true;
        }
        return changed;
    }

    private boolean applyLocation(ParcelTracking tracking, TrackingEvent event) {
        if (!sharesLocation(tracking.getStatus())) {
            return false;
        }
        if (tracking.getCourierId() != null && event.courierId() != null
                && !tracking.getCourierId().equals(event.courierId())) {
            return false;
        }
        CourierFix current = tracking.getCourierPosition();
        if (current != null && !event.occurredAt().isAfter(current.at())) {
            return false;
        }
        GeoPoint point = event.position();
        tracking.setCourierPosition(new CourierFix(point, event.occurredAt()));
        List<GeoPoint> trail = tracking.getCourierTrail();
        if (trail.isEmpty() || !trail.get(trail.size() - 1).equals(point)) {
            trail.add(point);
        }
        while (trail.size() > properties.getTrailLimit()) {
            trail.remove(0);
        }
        return true;
    }

    /** A plain (re)assignment can only move a parcel that has not been picked up yet. */
    private static boolean canMoveTo(ParcelStatus current, ParcelStatus target) {
        if (ASSIGNABLE.contains(target)) {
            return current == null || ASSIGNABLE.contains(current);
        }
        return true;
    }

    private boolean addToTimeline(ParcelTracking tracking, TrackingEvent event, ParcelStatus status) {
        if (status == null || status == ParcelStatus.ASSIGNED) {
            return false;
        }
        for (TimelineEntry entry : tracking.getTimeline()) {
            if (event.eventId().equals(entry.eventId())
                    || (entry.status() == status && entry.at().equals(event.occurredAt()))) {
                return false;
            }
        }
        String reason = status == ParcelStatus.FAILED
                ? (event.failureReason() != null ? event.failureReason() : FailureReason.OTHER.name())
                : null;
        List<TimelineEntry> timeline = tracking.getTimeline();
        timeline.add(new TimelineEntry(event.eventId(), status, event.occurredAt(), event.hub(), reason));
        timeline.sort(Comparator.comparing(TimelineEntry::at));
        while (timeline.size() > properties.getTimelineLimit()) {
            timeline.remove(0);
        }
        return true;
    }

    private static boolean isNewer(ParcelTracking tracking, TrackingEvent event) {
        Long sequence = sequenceOf(event);
        if (sequence != null && tracking.getLastSequence() != null) {
            return sequence > tracking.getLastSequence();
        }
        return tracking.getLastEventAt() == null || !event.occurredAt().isBefore(tracking.getLastEventAt());
    }

    private static void recordOrdering(ParcelTracking tracking, TrackingEvent event) {
        Long sequence = sequenceOf(event);
        if (sequence != null && (tracking.getLastSequence() == null || sequence > tracking.getLastSequence())) {
            tracking.setLastSequence(sequence);
        }
        if (tracking.getLastEventAt() == null || event.occurredAt().isAfter(tracking.getLastEventAt())) {
            tracking.setLastEventAt(event.occurredAt());
        }
    }

    /**
     * Assignment events come from Dispatch (M6), not from the Parcel service that numbers the parcel's events,
     * so their sequence (if any) is not comparable and only occurredAt is used for them.
     */
    private static Long sequenceOf(TrackingEvent event) {
        return event.kind() == Kind.ASSIGNED || event.kind() == Kind.REASSIGNED ? null : event.sequence();
    }

    private static ParcelTracking touch(ParcelTracking tracking, Instant now) {
        tracking.setVersion(tracking.getVersion() + 1);
        tracking.setUpdatedAt(now);
        return tracking;
    }
}
