package lk.swiftdrop.tracking.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import lk.swiftdrop.tracking.config.TrackingProperties;
import lk.swiftdrop.tracking.domain.ParcelTracking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis implementation of the read model.
 * <pre>
 * tracking:parcel:&lt;trackingNo&gt;   JSON of {@link ParcelTracking}            (TTL snapshot-ttl, refreshed on write)
 * tracking:pid:&lt;parcelId&gt;         trackingNo                                 (TTL snapshot-ttl)
 * tracking:courier:&lt;courierId&gt;    set of trackingNos the courier carries     (TTL snapshot-ttl)
 * tracking:event:&lt;eventId&gt;        "1" once the event was handled            (TTL processed-event-ttl)
 * </pre>
 * Writes use WATCH/MULTI/EXEC (optimistic locking), so two instances handling events of the same parcel at the
 * same time never overwrite each other's change.
 */
@Component
@ConditionalOnProperty(prefix = "swiftdrop.tracking", name = "redis-enabled", havingValue = "true",
        matchIfMissing = true)
public class RedisTrackingStore implements TrackingStore {

    private static final Logger log = LoggerFactory.getLogger(RedisTrackingStore.class);
    private static final int MAX_ATTEMPTS = 10;
    private static final List<Object> NO_CHANGE = List.of("no-change");

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final TrackingProperties properties;

    public RedisTrackingStore(StringRedisTemplate redis, ObjectMapper objectMapper, TrackingProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Optional<ParcelTracking> find(String trackingNo) {
        return Optional.ofNullable(redis.opsForValue().get(parcelKey(trackingNo))).flatMap(this::read);
    }

    @Override
    public Optional<ParcelTracking> compute(String trackingNo,
                                            Function<Optional<ParcelTracking>, Optional<ParcelTracking>> change) {
        String key = parcelKey(trackingNo);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            AtomicReference<ParcelTracking> next = new AtomicReference<>();
            List<Object> result = redis.execute(new SessionCallback<List<Object>>() {
                @Override
                @SuppressWarnings("unchecked")
                public <K, V> List<Object> execute(RedisOperations<K, V> operations) throws DataAccessException {
                    RedisOperations<String, String> ops = (RedisOperations<String, String>) operations;
                    ops.watch(key);
                    String json = ops.opsForValue().get(key);
                    Optional<ParcelTracking> changed = change.apply(Optional.ofNullable(json).flatMap(j -> read(j)));
                    if (changed.isEmpty()) {
                        ops.unwatch();
                        return NO_CHANGE;
                    }
                    next.set(changed.get());
                    ops.multi();
                    ops.opsForValue().set(key, write(changed.get()), properties.getSnapshotTtl());
                    return ops.exec();
                }
            });
            if (result == NO_CHANGE) {
                return Optional.empty();
            }
            if (result != null && !result.isEmpty()) {
                return Optional.of(next.get());
            }
            log.debug("Concurrent update of parcel {}, retrying (attempt {})", trackingNo, attempt);
        }
        throw new ConcurrencyFailureException("Could not update parcel " + trackingNo + " after retries");
    }

    @Override
    public void indexParcelId(String parcelId, String trackingNo) {
        redis.opsForValue().set(parcelIdKey(parcelId), trackingNo, properties.getSnapshotTtl());
    }

    @Override
    public Optional<String> findTrackingNo(String parcelId) {
        return Optional.ofNullable(redis.opsForValue().get(parcelIdKey(parcelId)));
    }

    @Override
    public void linkCourier(String courierId, String trackingNo) {
        String key = courierKey(courierId);
        redis.opsForSet().add(key, trackingNo);
        redis.expire(key, properties.getSnapshotTtl());
    }

    @Override
    public void unlinkCourier(String courierId, String trackingNo) {
        redis.opsForSet().remove(courierKey(courierId), trackingNo);
    }

    @Override
    public Set<String> parcelsOfCourier(String courierId) {
        Set<String> members = redis.opsForSet().members(courierKey(courierId));
        return members != null ? members : Set.of();
    }

    @Override
    public boolean markProcessed(String eventId) {
        return Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(eventKey(eventId), "1", properties.getProcessedEventTtl()));
    }

    @Override
    public void unmarkProcessed(String eventId) {
        redis.delete(eventKey(eventId));
    }

    private Optional<ParcelTracking> read(String json) {
        try {
            return Optional.of(objectMapper.readValue(json, ParcelTracking.class));
        } catch (JsonProcessingException e) {
            // A record written by an incompatible version: start again from the next events instead of failing them all.
            log.error("Unreadable tracking record ignored: {}", e.getOriginalMessage());
            return Optional.empty();
        }
    }

    private String write(ParcelTracking tracking) {
        try {
            return objectMapper.writeValueAsString(tracking);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Tracking record could not be serialised", e);
        }
    }

    private String parcelKey(String trackingNo) {
        return properties.getKeyPrefix() + "parcel:" + trackingNo;
    }

    private String parcelIdKey(String parcelId) {
        return properties.getKeyPrefix() + "pid:" + parcelId;
    }

    private String courierKey(String courierId) {
        return properties.getKeyPrefix() + "courier:" + courierId;
    }

    private String eventKey(String eventId) {
        return properties.getKeyPrefix() + "event:" + eventId;
    }
}
