package lk.swiftdrop.tracking.push;

import lk.swiftdrop.tracking.domain.ParcelTracking;

/**
 * Announces a changed parcel to every Tracking instance (Redis pub/sub in production), so a browser connected
 * to instance B sees an event that instance A consumed.
 */
public interface TrackingBroadcaster {

    void publish(ParcelTracking tracking);
}
