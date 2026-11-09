package lk.swiftdrop.dispatch.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lk.swiftdrop.dispatch.common.ConflictException;
import lk.swiftdrop.dispatch.common.ErrorCode;
import lk.swiftdrop.dispatch.config.AppProperties;
import org.springframework.stereotype.Service;

/**
 * In-memory idempotency cache for assignment requests.
 * Prevents double assignment on double-clicks and network retries.
 * Cached responses are retained for 24 hours.
 */
@Service
public class IdempotencyService {

    private record Entry(Object response, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final Duration ttl;

    public IdempotencyService(AppProperties properties) {
        int hours = properties.dispatch() != null ? properties.dispatch().idempotencyTtlHours() : 24;
        this.ttl = Duration.ofHours(hours);
    }

    public Object get(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        Entry entry = cache.get(idempotencyKey);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(Instant.now())) {
            cache.remove(idempotencyKey);
            return null;
        }
        return entry.response();
    }

    public void put(String idempotencyKey, Object response) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        cache.put(idempotencyKey, new Entry(response, Instant.now().plus(ttl)));
    }
}

