package lk.swiftdrop.merchant.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.List;
import lk.swiftdrop.merchant.merchant.MerchantStatus;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class WebhookApiTest extends AbstractWebhookApiTest {

    private static final String SECRET_PATTERN = "^whsec_[A-Za-z0-9_-]{43}$";

    @Autowired
    private WebhookConfigRepository repository;

    @Test
    void anUnconfiguredWebhookIsReportedAsNotConfigured() throws Exception {
        fixtures.approvedMerchant("user-1");

        getWebhook("user-1")
                .andExpect(status().isOk())
                .andExpect(content().json("{\"configured\":false}", true));
    }

    @Test
    void theFirstPutCreatesTheConfigurationAndShowsTheSecretOnce() throws Exception {
        fixtures.approvedMerchant("user-1");

        MvcResult created = putWebhook("user-1", URL, true, List.of("*"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.url").value(URL))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.events", hasSize(1)))
                .andExpect(jsonPath("$.events[0]").value("*"))
                .andExpect(jsonPath("$.secret", matchesPattern(SECRET_PATTERN)))
                .andExpect(jsonPath("$.lastTestAt").value(nullValue()))
                .andExpect(jsonPath("$.lastTestStatus").value(nullValue()))
                .andReturn();

        JsonNode response = objectMapper.readTree(created.getResponse().getContentAsString());
        String secret = response.get("secret").asText();
        assertThat(response.get("secretHint").asText()).isEqualTo(secret.substring(0, 10) + "...");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void laterPutsKeepTheSecretAndDoNotShowIt() throws Exception {
        String secret = createWebhook("user-1", URL);
        String ciphertextBefore = storedCiphertext();

        putWebhook("user-1", "https://" + FakeDnsConfig.OTHER_PUBLIC_HOST + "/new", false,
                List.of("parcel.created", "parcel.status.delivered"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.url").value("https://" + FakeDnsConfig.OTHER_PUBLIC_HOST + "/new"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.events[0]").value("parcel.created"))
                .andExpect(jsonPath("$.events[1]").value("parcel.status.delivered"))
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.secretHint").value(secret.substring(0, 10) + "..."));

        assertThat(storedCiphertext()).isEqualTo(ciphertextBefore);
        assertThat(cipher.decrypt(storedCiphertext())).isEqualTo(secret);
    }

    @Test
    void getNeverReturnsTheSecret() throws Exception {
        String secret = createWebhook("user-1", URL);

        String body = getWebhook("user-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.url").value(URL))
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.secretHint").value(secret.substring(0, 10) + "..."))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(secret);
    }

    @Test
    void theDatabaseHoldsCiphertextNotTheSecret() throws Exception {
        String secret = createWebhook("user-1", URL);

        String stored = storedCiphertext();

        assertThat(stored).doesNotContain(secret).doesNotContain("whsec_");
        assertThat(cipher.decrypt(stored)).isEqualTo(secret);
        assertThat(jdbc.queryForObject("select secret_hint from webhook_configs", String.class))
                .isEqualTo(secret.substring(0, 10) + "...");
    }

    @Test
    void eventsAreStoredCommaSeparated() throws Exception {
        fixtures.approvedMerchant("user-1");

        putWebhook("user-1", URL, true, List.of(" parcel.created", "parcel.status.delivered", "parcel.created"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events", hasSize(2)));

        assertThat(jdbc.queryForObject("select events from webhook_configs", String.class))
                .isEqualTo("parcel.created,parcel.status.delivered");
        getWebhook("user-1")
                .andExpect(jsonPath("$.events[0]").value("parcel.created"))
                .andExpect(jsonPath("$.events[1]").value("parcel.status.delivered"));
    }

    @Test
    void rotatingReturnsANewSecretAndReplacesTheOldOne() throws Exception {
        String oldSecret = createWebhook("user-1", URL);
        String oldCiphertext = storedCiphertext();

        MvcResult rotated = rotate("user-1")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.secret", matchesPattern(SECRET_PATTERN)))
                .andReturn();

        String newSecret = objectMapper.readTree(rotated.getResponse().getContentAsString()).get("secret").asText();
        assertThat(newSecret).isNotEqualTo(oldSecret);
        assertThat(storedCiphertext()).isNotEqualTo(oldCiphertext);
        assertThat(cipher.decrypt(storedCiphertext())).isEqualTo(newSecret);
        getWebhook("user-1")
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.secretHint").value(newSecret.substring(0, 10) + "..."));
    }

    @Test
    void rotatingWithoutAConfigurationIsAConflict() throws Exception {
        fixtures.approvedMerchant("user-1");

        rotate("user-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WEBHOOK_NOT_CONFIGURED"));
    }

    @Test
    void testingWithoutAConfigurationIsAConflict() throws Exception {
        fixtures.approvedMerchant("user-1");

        sendTest("user-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WEBHOOK_NOT_CONFIGURED"));
    }

    @Test
    void deletingRemovesTheConfigurationAndIsIdempotent() throws Exception {
        createWebhook("user-1", URL);

        deleteWebhook("user-1").andExpect(status().isNoContent());

        assertThat(repository.count()).isZero();
        getWebhook("user-1").andExpect(content().json("{\"configured\":false}", true));
        deleteWebhook("user-1").andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @EnumSource(value = MerchantStatus.class, names = {"PENDING", "REJECTED", "SUSPENDED"})
    void merchantsThatAreNotApprovedCannotCreateOrChangeAWebhook(MerchantStatus status) throws Exception {
        fixtures.merchant("user-1", status);

        putWebhook("user-1", URL, true, List.of("*"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
        rotate("user-1")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
        sendTest("user-1")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
        assertThat(repository.count()).isZero();
        getWebhook("user-1").andExpect(content().json("{\"configured\":false}", true));
    }

    @Test
    void aSuspendedMerchantKeepsItsConfigurationButCannotChangeIt() throws Exception {
        createWebhook("user-1", URL);
        jdbc.update("update merchants set status = 'SUSPENDED'");

        putWebhook("user-1", "https://" + FakeDnsConfig.OTHER_PUBLIC_HOST + "/", true, List.of("*"))
                .andExpect(status().isForbidden());
        getWebhook("user-1").andExpect(jsonPath("$.url").value(URL));
        deleteWebhook("user-1").andExpect(status().isNoContent());
        assertThat(repository.count()).isZero();
    }

    @Test
    void usersWithoutAMerchantAreToldToRegister() throws Exception {
        for (var request : List.of(
                get(WEBHOOK_PATH),
                put(WEBHOOK_PATH).contentType(MediaType.APPLICATION_JSON).content(body(URL, true, List.of("*"))),
                post(WEBHOOK_PATH + "/rotate-secret"),
                post(WEBHOOK_PATH + "/test"),
                delete(WEBHOOK_PATH),
                get(WEBHOOK_PATH + "/deliveries"))) {
            mockMvc.perform(request.with(TestAuth.merchant("nobody")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MERCHANT_NOT_REGISTERED"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[\"parcel.assigned\"]",
            "[\"parcel.created\",\"nonsense\"]",
            "[\"*\",\"parcel.created\"]",
            "[\"\"]",
            "[null]",
            "[]"})
    void invalidEventListsAreRejectedWithTheEventsField(String events) throws Exception {
        fixtures.approvedMerchant("user-1");

        putRaw("user-1", "{\"url\":\"" + URL + "\",\"active\":true,\"events\":" + events + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("events"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void aMissingOrOversizedEventListIsRejected() throws Exception {
        fixtures.approvedMerchant("user-1");
        String tooMany = String.join(",", Collections.nCopies(21, "\"parcel.created\""));

        putRaw("user-1", "{\"url\":\"" + URL + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("events"));
        putRaw("user-1", "{\"url\":\"" + URL + "\",\"events\":[" + tooMany + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("events"));
    }

    @Test
    void activeDefaultsToTrue() throws Exception {
        fixtures.approvedMerchant("user-1");

        putRaw("user-1", "{\"url\":\"" + URL + "\",\"events\":[\"*\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void aBlankUrlAndAMalformedBodyAreRejected() throws Exception {
        fixtures.approvedMerchant("user-1");

        putRaw("user-1", "{\"url\":\"  \",\"events\":[\"*\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("url"));
        putRaw("user-1", "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://hooks.example.com/hook",
            "ftp://hooks.example.com/hook",
            "https://user:pw@hooks.example.com/hook",
            "https://127.0.0.1/hook",
            "https://[::1]/hook",
            "https://10.0.0.8/hook",
            "https://192.168.0.8/hook",
            "https://169.254.169.254/latest/meta-data",
            "https://[::ffff:127.0.0.1]/hook",
            "https://2130706433/hook",
            "https://rebind.example.com/hook",
            "https://internal.example.com/hook",
            "https://unknown.example.net/hook",
            "https://hooks.example.com:70000/hook",
            "not a url"})
    void unsafeUrlsAreRejectedAndNothingIsStored(String url) throws Exception {
        fixtures.approvedMerchant("user-1");

        putWebhook("user-1", url + "?token=tok_url_12345", true, List.of("*"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WEBHOOK_URL_REJECTED"))
                .andExpect(content().string(not(containsString("tok_url_12345"))));
        assertThat(repository.count()).isZero();
    }

    @Test
    void anUnsafeUrlCannotReplaceAnExistingOne() throws Exception {
        createWebhook("user-1", URL);

        putWebhook("user-1", "https://10.0.0.8/hook", true, List.of("*")).andExpect(status().isUnprocessableEntity());

        getWebhook("user-1").andExpect(jsonPath("$.url").value(URL));
    }

    @Test
    void usersOnlySeeAndChangeTheirOwnWebhook() throws Exception {
        String secretA = createWebhook("user-a", URL);
        fixtures.approvedMerchant("user-b");

        getWebhook("user-b").andExpect(content().json("{\"configured\":false}", true));
        rotate("user-b").andExpect(status().isConflict());
        deleteWebhook("user-b").andExpect(status().isNoContent());

        String secretB = objectMapper.readTree(putWebhook("user-b", "https://" + FakeDnsConfig.OTHER_PUBLIC_HOST + "/b",
                true, List.of("parcel.created")).andReturn().getResponse().getContentAsString()).get("secret").asText();

        assertThat(repository.count()).isEqualTo(2);
        getWebhook("user-a")
                .andExpect(jsonPath("$.url").value(URL))
                .andExpect(jsonPath("$.events[0]").value("*"))
                .andExpect(jsonPath("$.secretHint").value(secretA.substring(0, 10) + "..."));
        getWebhook("user-b")
                .andExpect(jsonPath("$.url").value("https://" + FakeDnsConfig.OTHER_PUBLIC_HOST + "/b"))
                .andExpect(jsonPath("$.secretHint").value(secretB.substring(0, 10) + "..."));
        rotate("user-b").andExpect(status().isOk());
        getWebhook("user-a").andExpect(jsonPath("$.secretHint").value(secretA.substring(0, 10) + "..."));
        deleteWebhook("user-b").andExpect(status().isNoContent());
        getWebhook("user-a").andExpect(jsonPath("$.configured").value(true));
    }

    @Test
    void onlyMerchantsMayUseTheWebhookEndpoints() throws Exception {
        createWebhook("user-1", URL);

        for (var user : List.of(TestAuth.admin("admin-1"), TestAuth.customer("customer-1"))) {
            mockMvc.perform(get(WEBHOOK_PATH).with(user)).andExpect(status().isForbidden());
            mockMvc.perform(put(WEBHOOK_PATH).with(user).contentType(MediaType.APPLICATION_JSON)
                    .content(body(URL, true, List.of("*")))).andExpect(status().isForbidden());
            mockMvc.perform(post(WEBHOOK_PATH + "/rotate-secret").with(user)).andExpect(status().isForbidden());
            mockMvc.perform(post(WEBHOOK_PATH + "/test").with(user)).andExpect(status().isForbidden());
            mockMvc.perform(delete(WEBHOOK_PATH).with(user)).andExpect(status().isForbidden());
            mockMvc.perform(get(WEBHOOK_PATH + "/deliveries").with(user)).andExpect(status().isForbidden());
        }
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void requestsWithoutATokenAreUnauthenticated() throws Exception {
        mockMvc.perform(get(WEBHOOK_PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(WEBHOOK_PATH).contentType(MediaType.APPLICATION_JSON)
                .content(body(URL, true, List.of("*")))).andExpect(status().isUnauthorized());
        mockMvc.perform(post(WEBHOOK_PATH + "/rotate-secret")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(WEBHOOK_PATH + "/test")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(WEBHOOK_PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(WEBHOOK_PATH + "/deliveries")).andExpect(status().isUnauthorized());
    }

    @Test
    void theDeliveryLogIsUnavailableWhenTheDeliveryServiceCannotBeReached() throws Exception {
        fixtures.approvedMerchant("user-1");

        mockMvc.perform(get(WEBHOOK_PATH + "/deliveries").param("page", "2").param("size", "10")
                        .with(TestAuth.merchant("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101"})
    void deliveryLogPaginationIsValidated(String page, String size) throws Exception {
        fixtures.approvedMerchant("user-1");

        mockMvc.perform(get(WEBHOOK_PATH + "/deliveries").param("page", page).param("size", size)
                        .with(TestAuth.merchant("user-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void theBiggestSubscriptionFitsTheDatabaseColumn() throws Exception {
        fixtures.approvedMerchant("user-1");
        List<String> all = List.of("parcel.created", "parcel.status.picked_up", "parcel.status.in_transit",
                "parcel.status.out_for_delivery", "parcel.status.delivered", "parcel.status.failed",
                "parcel.status.rescheduled", "parcel.status.returned", "parcel.cancelled");

        putWebhook("user-1", URL, true, all).andExpect(status().isOk());

        getWebhook("user-1").andExpect(jsonPath("$.events", contains(all.toArray())));
    }
}
