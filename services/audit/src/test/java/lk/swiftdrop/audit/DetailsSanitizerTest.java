package lk.swiftdrop.audit;

import lk.swiftdrop.audit.service.DetailsSanitizer;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DetailsSanitizerTest {

    private final DetailsSanitizer sanitizer = new DetailsSanitizer();

    @Test
    void secretsAndPersonalDataAreDropped() {
        Map<String, Object> data = new HashMap<>();
        data.put("merchantId", "m-1");
        data.put("password", "x");
        data.put("accessToken", "t");
        data.put("clientSecret", "s");
        data.put("contactEmail", "a@b.c");
        data.put("phone", "0770000000");
        data.put("pickupAddress", "25 Galle Rd");
        data.put("apiKey", "sd_live_123");
        data.put("keyHash", "abc");

        assertThat(sanitizer.sanitize(data)).containsOnlyKeys("merchantId");
    }

    @Test
    void valuesBecomeTextAndNestedStructuresAreSummarised() {
        Map<String, Object> data = new HashMap<>();
        data.put("count", 3);
        data.put("approved", true);
        data.put("nested", Map.of("a", 1));
        data.put("items", List.of(1, 2));
        data.put("nothing", null);

        Map<String, String> clean = sanitizer.sanitize(data);

        assertThat(clean).containsEntry("count", "3").containsEntry("approved", "true")
                .containsEntry("nested", "[object]").containsEntry("items", "[list]")
                .doesNotContainKey("nothing");
    }

    @Test
    void longValuesAndLargeMapsAreLimited() {
        Map<String, Object> data = new HashMap<>();
        data.put("reason", "x".repeat(500));
        for (int i = 0; i < 50; i++) {
            data.put("k" + i, "v");
        }

        Map<String, String> clean = sanitizer.sanitize(data);

        assertThat(clean).hasSize(30);
        assertThat(clean.getOrDefault("reason", "")).hasSizeLessThanOrEqualTo(200);
    }

    @Test
    void missingDataGivesEmptyDetails() {
        assertThat(sanitizer.sanitize(null)).isEmpty();
    }
}
