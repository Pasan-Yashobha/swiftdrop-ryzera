package lk.swiftdrop.tracking.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lk.swiftdrop.tracking.domain.ParcelTracking;
import lk.swiftdrop.tracking.event.EventEnvelope;
import lk.swiftdrop.tracking.event.EventMapper;
import lk.swiftdrop.tracking.event.TrackingEvent;
import lk.swiftdrop.tracking.event.TrackingEvent.Kind;
import lk.swiftdrop.tracking.push.TrackingBroadcaster;
import lk.swiftdrop.tracking.store.TrackingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Event to read model to push: maps the event, ignores duplicates, applies it to the parcel in the store and
 * announces the new state on the update channel, from where every instance pushes it to its open streams.
 */
@Service
public class TrackingUpdateService {

    private static final Logger log = LoggerFactory.getLogger(TrackingUpdateService.class);

    private final EventMapper mapper;
    private final TrackingProjector projector;
    private final TrackingStore store;
    private final TrackingBroadcaster broadcaster;
    private final Clock clock;

    public TrackingUpdateService(EventMapper mapper, TrackingProjector projector, TrackingStore store,
                                 TrackingBroadcaster broadcaster, Clock clock) {
        this.mapper = mapper;
        this.projector = projector;
        this.store = store;
        this.broadcaster = broadcaster;
        this.clock = clock;
    }

    public void handle(EventEnvelope envelope) {
        Optional<TrackingEvent> mapped = mapper.map(envelope);
        if (mapped.isEmpty()) {
            log.debug("Event type {} is not used by tracking", envelope.eventType());
            return;
        }
        TrackingEvent event = mapped.get();
        if (!store.markProcessed(event.eventId())) {
            log.debug("Duplicate event {} ignored", event.eventId());
            return;
        }
        try {
            if (event.kind() == Kind.COURIER_LOCATION) {
                applyLocation(event);
            } else {
                applyParcelEvent(event, envelope.eventType());
            }
        } catch (RuntimeException e) {
            try {
                store.unmarkProcessed(event.eventId());
            } catch (RuntimeException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    private void applyParcelEvent(TrackingEvent event, String eventType) {
        String trackingNo = event.trackingNo() != null
                ? event.trackingNo()
                : store.findTrackingNo(event.parcelId()).orElseThrow(() -> new UnresolvedParcelException(
                        eventType + " for a parcel id without a known tracking number"));
        TrackingEvent resolved = event.trackingNo() != null ? event : event.withTrackingNo(trackingNo);
        Instant now = clock.instant();

        AtomicReference<String> courierBefore = new AtomicReference<>();
        Optional<ParcelTracking> updated = store.compute(trackingNo, current -> {
            courierBefore.set(current.map(ParcelTracking::getCourierId).orElse(null));
            return projector.apply(current, resolved, now);
        });
        if (updated.isEmpty()) {
            return;
        }

        ParcelTracking tracking = updated.get();
        if (tracking.getParcelId() != null) {
            store.indexParcelId(tracking.getParcelId(), trackingNo);
        }
        String before = courierBefore.get();
        String after = tracking.getCourierId();
        if (before != null && !before.equals(after)) {
            store.unlinkCourier(before, trackingNo);
        }
        if (after != null && !after.equals(before)) {
            store.linkCourier(after, trackingNo);
        }
        broadcaster.publish(tracking);
        log.info("Parcel {} {} after {} (version {})", trackingNo, tracking.getStatus(), eventType,
                tracking.getVersion());
    }

    private void applyLocation(TrackingEvent event) {
        Set<String> targets;
        if (event.trackingNo() != null) {
            targets = Set.of(event.trackingNo());
        } else if (event.parcelId() != null) {
            targets = store.findTrackingNo(event.parcelId()).map(no -> Set.of(no)).orElseGet(Set::of);
        } else {
            targets = store.parcelsOfCourier(event.courierId());
        }
        Instant now = clock.instant();
        for (String trackingNo : targets) {
            TrackingEvent forParcel = event.withTrackingNo(trackingNo);
            store.compute(trackingNo, current -> projector.apply(current, forParcel, now))
                    .ifPresent(broadcaster::publish);
        }
    }
}
