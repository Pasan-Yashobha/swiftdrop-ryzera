package lk.swiftdrop.tracking.push;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lk.swiftdrop.tracking.api.PublicTrackingMapper;
import lk.swiftdrop.tracking.api.PublicTrackingResponse;
import lk.swiftdrop.tracking.common.ApiException;
import lk.swiftdrop.tracking.common.ErrorCode;
import lk.swiftdrop.tracking.config.TrackingProperties;
import lk.swiftdrop.tracking.domain.ParcelTracking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Open server-sent event streams of this instance, grouped by tracking number.
 * <ul>
 *   <li>Every stream gets the current state immediately, then one {@code tracking} event per change.</li>
 *   <li>A comment line every {@code heartbeat-interval-ms} keeps proxies from closing idle streams.</li>
 *   <li>Connection limits per instance and per parcel protect the service; a refused client falls back to polling.</li>
 *   <li>On shutdown every stream is completed first, so clients reconnect to another instance at once.</li>
 * </ul>
 */
@Service
public class TrackingStreamService implements SmartLifecycle {

    public static final String EVENT_NAME = "tracking";

    private static final Logger log = LoggerFactory.getLogger(TrackingStreamService.class);

    private final ConcurrentMap<String, Set<Subscriber>> streams = new ConcurrentHashMap<>();
    private final AtomicInteger openStreams = new AtomicInteger();
    private final PublicTrackingMapper mapper;
    private final TrackingProperties properties;
    private final Clock clock;
    private volatile boolean running;

    public TrackingStreamService(PublicTrackingMapper mapper, TrackingProperties properties, Clock clock,
                                 MeterRegistry meterRegistry) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
        Gauge.builder("tracking.sse.connections", openStreams, AtomicInteger::get)
                .description("Open live tracking streams on this instance")
                .register(meterRegistry);
    }

    /** Opens a stream for a known parcel and sends its current state as the first event. */
    public SseEmitter open(ParcelTracking current) {
        TrackingProperties.Sse limits = properties.getSse();
        if (openStreams.get() >= limits.getMaxConnections()) {
            throw new ApiException(ErrorCode.STREAM_LIMIT_REACHED, "Live updates are busy. The page will refresh itself.");
        }
        String trackingNo = current.getTrackingNo();
        SseEmitter emitter = new SseEmitter(limits.getTimeout().toMillis());
        Subscriber subscriber = new Subscriber(trackingNo, emitter);
        AtomicBoolean added = new AtomicBoolean();
        streams.compute(trackingNo, (key, set) -> {
            Set<Subscriber> subscribers = set != null ? set : ConcurrentHashMap.newKeySet();
            if (subscribers.size() < limits.getMaxConnectionsPerParcel()) {
                subscribers.add(subscriber);
                added.set(true);
            }
            return subscribers.isEmpty() ? null : subscribers;
        });
        if (!added.get()) {
            throw new ApiException(ErrorCode.STREAM_LIMIT_REACHED, "Live updates are busy. The page will refresh itself.");
        }
        openStreams.incrementAndGet();

        emitter.onCompletion(() -> remove(subscriber));
        emitter.onTimeout(() -> {
            remove(subscriber);
            subscriber.complete();
        });
        emitter.onError(error -> remove(subscriber));

        subscriber.send(trackingEvent(mapper.toResponse(current, clock.instant())));
        return emitter;
    }

    /** Sends the new state of a parcel to every stream open for it on this instance. */
    public void push(ParcelTracking tracking) {
        Set<Subscriber> subscribers = streams.get(tracking.getTrackingNo());
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        PublicTrackingResponse response = mapper.toResponse(tracking, clock.instant());
        for (Subscriber subscriber : subscribers) {
            subscriber.send(trackingEvent(response));
        }
    }

    @Scheduled(fixedDelayString = "${swiftdrop.tracking.sse.heartbeat-interval-ms:20000}")
    public void heartbeat() {
        for (Set<Subscriber> subscribers : streams.values()) {
            for (Subscriber subscriber : subscribers) {
                subscriber.send(SseEmitter.event().comment("keep-alive"));
            }
        }
    }

    public int openStreams() {
        return openStreams.get();
    }

    public int openStreams(String trackingNo) {
        Set<Subscriber> subscribers = streams.get(trackingNo);
        return subscribers == null ? 0 : subscribers.size();
    }

    private SseEmitter.SseEventBuilder trackingEvent(PublicTrackingResponse response) {
        return SseEmitter.event()
                .name(EVENT_NAME)
                .id(Long.toString(response.version()))
                .reconnectTime(properties.getSse().getReconnectDelayMs())
                .data(response, MediaType.APPLICATION_JSON);
    }

    private void remove(Subscriber subscriber) {
        if (!subscriber.close()) {
            return;
        }
        streams.computeIfPresent(subscriber.trackingNo, (key, set) -> {
            set.remove(subscriber);
            return set.isEmpty() ? null : set;
        });
        openStreams.decrementAndGet();
    }

    @Override
    public void start() {
        running = true;
    }

    /** Runs before the web server's graceful shutdown (higher phase stops first), so streams do not hold it up. */
    @Override
    public void stop() {
        running = false;
        List<Subscriber> all = new ArrayList<>();
        streams.values().forEach(all::addAll);
        all.forEach(subscriber -> {
            remove(subscriber);
            subscriber.complete();
        });
        if (!all.isEmpty()) {
            log.info("Closed {} live tracking streams for shutdown", all.size());
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** One open stream. {@link SseEmitter} is not safe for concurrent sends, so sends are serialised. */
    private final class Subscriber {

        private final String trackingNo;
        private final SseEmitter emitter;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Subscriber(String trackingNo, SseEmitter emitter) {
            this.trackingNo = trackingNo;
            this.emitter = emitter;
        }

        /** Marks the subscriber closed; true only for the first call. */
        private boolean close() {
            return closed.compareAndSet(false, true);
        }

        private synchronized void send(SseEmitter.SseEventBuilder event) {
            if (closed.get()) {
                return;
            }
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException e) {
                // The browser went away; the container also calls onError/onCompletion.
                remove(this);
                complete();
            }
        }

        private void complete() {
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // already completed or the connection is gone
            }
        }
    }
}
