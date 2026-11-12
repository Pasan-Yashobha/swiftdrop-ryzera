package lk.swiftdrop.dispatch.service;

import lk.swiftdrop.dispatch.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyServiceTest {

    private final AppProperties properties = new AppProperties(
            new AppProperties.Dispatch(8, 10, 24),
            new AppProperties.Integration("http://parcel", "http://pricing"),
            new AppProperties.Security(null, null, null)
    );

    private final IdempotencyService service = new IdempotencyService(properties);

    @Test
    @DisplayName("Returns null for null or blank key")
    void blankKeyReturnsNull() {
        assertThat(service.get(null)).isNull();
        assertThat(service.get("  ")).isNull();
        service.put(null, "response");
        service.put("  ", "response");
        assertThat(service.get("test")).isNull();
    }

    @Test
    @DisplayName("Caches and retrieves response by idempotency key")
    void storesAndRetrieves() {
        String key = "test-key-1";
        String payload = "{\"status\":\"OK\"}";

        service.put(key, payload);
        Object cached = service.get(key);

        assertThat(cached).isEqualTo(payload);
    }
}

