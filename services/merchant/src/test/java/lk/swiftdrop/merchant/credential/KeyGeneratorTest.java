package lk.swiftdrop.merchant.credential;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import org.junit.jupiter.api.Test;

class KeyGeneratorTest {

    private final KeyGenerator generator = new KeyGenerator();

    @Test
    void apiKeysHaveTheContractFormat() {
        assertThat(generator.apiKey(ApiEnvironment.LIVE)).matches("sd_live_[a-z0-9]{8}_[A-Za-z0-9_-]{43}").hasSize(60);
        assertThat(generator.apiKey(ApiEnvironment.TEST)).matches("sd_test_[a-z0-9]{8}_[A-Za-z0-9_-]{43}").hasSize(60);
    }

    @Test
    void generatedApiKeysAreWellFormedForTheParser() {
        String key = generator.apiKey(ApiEnvironment.TEST);

        assertThat(ApiKeyFormat.parse(key)).hasValueSatisfying(parsed -> {
            assertThat(parsed.environment()).isEqualTo(ApiEnvironment.TEST);
            assertThat(parsed.prefix()).isEqualTo(key.substring(0, 16));
        });
    }

    @Test
    void ten_thousand_keys_and_ids_are_all_different() {
        Set<String> keys = new HashSet<>();
        Set<String> prefixes = new HashSet<>();
        Set<String> clientIds = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            String key = generator.apiKey(ApiEnvironment.LIVE);
            keys.add(key);
            prefixes.add(key.substring(0, 16));
            clientIds.add(generator.clientId());
        }

        assertThat(keys).hasSize(10_000);
        assertThat(prefixes).hasSize(10_000);
        assertThat(clientIds).hasSize(10_000);
    }

    @Test
    void secretsAre32RandomBytesInBase64Url() {
        boolean[] seen = new boolean[256];
        for (int i = 0; i < 1_000; i++) {
            String secret = generator.secret();
            assertThat(secret).hasSize(43).matches("[A-Za-z0-9_-]{43}");
            for (byte value : Base64.getUrlDecoder().decode(secret)) {
                seen[value & 0xFF] = true;
            }
        }

        // 32,000 uniformly random bytes miss a given value with probability below 1e-54.
        assertThat(seen).containsOnly(true);
    }

    @Test
    void clientIdsHaveTheContractFormat() {
        assertThat(generator.clientId()).matches("mc_[a-z0-9]{20}");
    }

    @Test
    void recognisesOnlyWellFormedClientIds() {
        assertThat(KeyGenerator.isClientId(generator.clientId())).isTrue();
        assertThat(KeyGenerator.isClientId(null)).isFalse();
        assertThat(KeyGenerator.isClientId("")).isFalse();
        assertThat(KeyGenerator.isClientId("mc_short")).isFalse();
        assertThat(KeyGenerator.isClientId("mc_" + "A".repeat(20))).isFalse();
        assertThat(KeyGenerator.isClientId("mc_" + "a".repeat(21))).isFalse();
        assertThat(KeyGenerator.isClientId("xx_" + "a".repeat(20))).isFalse();
        assertThat(KeyGenerator.isClientId("mc_" + "a".repeat(19) + "\n")).isFalse();
    }

    @Test
    void usesTheInjectedRandomSource() {
        SecureRandom constant = new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                Arrays.fill(bytes, (byte) 7);
            }

            @Override
            public int nextInt(int bound) {
                return 1;
            }
        };
        KeyGenerator fixed = new KeyGenerator(constant);
        byte[] sevens = new byte[32];
        Arrays.fill(sevens, (byte) 7);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(sevens);

        assertThat(fixed.secret()).isEqualTo(secret);
        assertThat(fixed.apiKey(ApiEnvironment.LIVE)).isEqualTo("sd_live_bbbbbbbb_" + secret);
        assertThat(fixed.clientId()).isEqualTo("mc_" + "b".repeat(20));
    }
}
