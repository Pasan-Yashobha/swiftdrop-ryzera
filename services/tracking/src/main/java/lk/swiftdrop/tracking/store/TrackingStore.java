package lk.swiftdrop.tracking.store;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lk.swiftdrop.tracking.domain.ParcelTracking;

/**
 * The tracking read model. The production implementation is {@link RedisTrackingStore}; tests use an in-memory
 * double behind this interface.
 */
public interface TrackingStore {

    Optional<ParcelTracking> find(String trackingNo);

    /**
     * Atomically reads, changes and writes one parcel. {@code change} receives the current state (empty when the
     * parcel is unknown) and returns the new state, or empty for "no change". It may be called more than once when
     * another instance writes the same parcel concurrently, so it must not have side effects.
     *
     * @return the stored new state, or empty when nothing changed
     */
    Optional<ParcelTracking> compute(String trackingNo,
                                     Function<Optional<ParcelTracking>, Optional<ParcelTracking>> change);

    /** Remembers which tracking number belongs to a parcel id (events from some producers carry only the id). */
    void indexParcelId(String parcelId, String trackingNo);

    Optional<String> findTrackingNo(String parcelId);

    /** Remembers that a courier carries a parcel, so courier location events can be routed to it. */
    void linkCourier(String courierId, String trackingNo);

    void unlinkCourier(String courierId, String trackingNo);

    Set<String> parcelsOfCourier(String courierId);

    /** Records an event id; returns false when it was already recorded (a duplicate delivery). */
    boolean markProcessed(String eventId);

    /** Forgets an event id after a failed attempt so the retry is not mistaken for a duplicate. */
    void unmarkProcessed(String eventId);
}
