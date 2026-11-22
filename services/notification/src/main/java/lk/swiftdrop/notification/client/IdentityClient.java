package lk.swiftdrop.notification.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lk.swiftdrop.notification.client.dto.ContactDetails;
import lk.swiftdrop.notification.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Contact details from the Identity Service ({@code GET /internal/users/{id}}, internal token). Events carry ids only,
 * so this is the one place personal data enters the service. Results are cached for 10 minutes (configurable) to
 * keep a burst of status events for the same customer from hammering Identity.
 */
@Component
public class IdentityClient extends InternalServiceClient {

    private static final int MAX_CACHE_ENTRIES = 10_000;

    private final Clock clock;
    private final Duration ttl;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public IdentityClient(AppProperties properties, Clock clock) {
        super("Identity Service", properties.clients().identityUrl(), properties);
        this.clock = clock;
        this.ttl = Duration.ofSeconds(properties.clients().contactCacheSeconds());
    }

    public ContactDetails contactOf(String userId) {
        Instant now = clock.instant();
        Cached cached = cache.get(userId);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.contact();
        }
        ContactDetails contact = get("/internal/users/{id}", ContactDetails.class, userId);
        if (!ttl.isZero()) {
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.clear();
            }
            cache.put(userId, new Cached(contact, now.plus(ttl)));
        }
        return contact;
    }

    private record Cached(ContactDetails contact, Instant expiresAt) {
    }
}
