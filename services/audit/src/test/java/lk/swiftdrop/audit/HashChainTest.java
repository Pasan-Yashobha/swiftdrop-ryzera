package lk.swiftdrop.audit;

import lk.swiftdrop.audit.service.HashChain;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HashChainTest {

    private static final UUID EVENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant WHEN = Instant.parse("2026-11-10T08:30:00.123456Z");

    private static String hash(String prev, String action, Map<String, String> details) {
        return HashChain.hash(prev, EVENT, WHEN, "admin-1", "ADMIN", action, "MERCHANT", "m-1", "corr-1", details);
    }

    @Test
    void hashIsDeterministicAndHex() {
        String a = hash(HashChain.GENESIS, "MERCHANT_APPROVED", Map.of("merchantId", "m-1"));
        String b = hash(HashChain.GENESIS, "MERCHANT_APPROVED", Map.of("merchantId", "m-1"));

        assertThat(a).isEqualTo(b).matches("[0-9a-f]{64}");
    }

    @Test
    void anyChangedFieldChangesTheHash() {
        String base = hash(HashChain.GENESIS, "MERCHANT_APPROVED", Map.of("merchantId", "m-1"));

        assertThat(hash(HashChain.GENESIS, "MERCHANT_REJECTED", Map.of("merchantId", "m-1"))).isNotEqualTo(base);
        assertThat(hash(HashChain.GENESIS, "MERCHANT_APPROVED", Map.of("merchantId", "m-2"))).isNotEqualTo(base);
        assertThat(hash("1".repeat(64), "MERCHANT_APPROVED", Map.of("merchantId", "m-1"))).isNotEqualTo(base);
    }

    @Test
    void orderOfDetailsDoesNotMatter() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("a", "1");
        first.put("b", "2");
        Map<String, String> second = new TreeMap<>(Map.of("b", "2", "a", "1"));

        assertThat(hash(HashChain.GENESIS, "X", first)).isEqualTo(hash(HashChain.GENESIS, "X", second));
    }

    @Test
    void fieldsCannotBeShiftedIntoEachOther() {
        // "ab" + "c" must not hash like "a" + "bc"
        String one = HashChain.hash(HashChain.GENESIS, EVENT, WHEN, "ab", "c", "X", null, null, null, Map.of());
        String two = HashChain.hash(HashChain.GENESIS, EVENT, WHEN, "a", "bc", "X", null, null, null, Map.of());

        assertThat(one).isNotEqualTo(two);
    }
}
