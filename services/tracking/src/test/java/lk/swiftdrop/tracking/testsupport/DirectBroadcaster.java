package lk.swiftdrop.tracking.testsupport;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lk.swiftdrop.tracking.domain.ParcelTracking;
import lk.swiftdrop.tracking.push.TrackingBroadcaster;
import lk.swiftdrop.tracking.push.TrackingStreamService;

/** Test double of the Redis pub/sub round trip: records each update and hands it straight to the streams. */
public class DirectBroadcaster implements TrackingBroadcaster {

    private final TrackingStreamService streams;
    private final List<ParcelTracking> published = new CopyOnWriteArrayList<>();

    public DirectBroadcaster(TrackingStreamService streams) {
        this.streams = streams;
    }

    @Override
    public void publish(ParcelTracking tracking) {
        published.add(tracking);
        if (streams != null) {
            streams.push(tracking);
        }
    }

    public List<ParcelTracking> published() {
        return published;
    }

    public void clear() {
        published.clear();
    }
}
