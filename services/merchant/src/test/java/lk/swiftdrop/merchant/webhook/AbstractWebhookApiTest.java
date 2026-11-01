package lk.swiftdrop.merchant.webhook;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lk.swiftdrop.merchant.testsupport.AbstractIntegrationTest;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Webhook endpoints with strict URL rules (https only, no private addresses) and DNS replaced by a fixed table.
 * The delivery log service is a closed port, so its calls fail immediately.
 */
@Import(FakeDnsConfig.class)
@TestPropertySource(properties = {
        "swiftdrop.webhook.allow-insecure-urls=false",
        "swiftdrop.webhook.allow-private-hosts=false",
        "swiftdrop.webhook.delivery-log-url=http://127.0.0.1:1"})
abstract class AbstractWebhookApiTest extends AbstractIntegrationTest {

    protected static final String URL = "https://" + FakeDnsConfig.PUBLIC_HOST + "/hooks/swiftdrop";
    protected static final String WEBHOOK_PATH = "/api/v1/merchants/me/webhook";

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected SecretCipher cipher;

    protected ResultActions getWebhook(String owner) throws Exception {
        return mockMvc.perform(get(WEBHOOK_PATH).with(TestAuth.merchant(owner)));
    }

    protected ResultActions putWebhook(String owner, String url, boolean active, List<String> events) throws Exception {
        return putRaw(owner, body(url, active, events));
    }

    protected ResultActions putRaw(String owner, String jsonBody) throws Exception {
        return mockMvc.perform(put(WEBHOOK_PATH).with(TestAuth.merchant(owner))
                .contentType(MediaType.APPLICATION_JSON).content(jsonBody));
    }

    protected ResultActions rotate(String owner) throws Exception {
        return mockMvc.perform(post(WEBHOOK_PATH + "/rotate-secret").with(TestAuth.merchant(owner)));
    }

    protected ResultActions sendTest(String owner) throws Exception {
        return mockMvc.perform(post(WEBHOOK_PATH + "/test").with(TestAuth.merchant(owner)));
    }

    protected ResultActions deleteWebhook(String owner) throws Exception {
        return mockMvc.perform(delete(WEBHOOK_PATH).with(TestAuth.merchant(owner)));
    }

    protected String body(String url, boolean active, List<String> events) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("url", url);
        request.put("active", active);
        request.put("events", events);
        return json(request);
    }

    /** Registers an approved merchant for {@code owner} and creates its webhook; returns the secret shown once. */
    protected String createWebhook(String owner, String url) throws Exception {
        fixtures.approvedMerchant(owner);
        String response = putWebhook(owner, url, true, List.of("*"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode secret = objectMapper.readTree(response).get("secret");
        return secret.asText();
    }

    protected String storedCiphertext() {
        return jdbc.queryForObject("select secret_ciphertext from webhook_configs", String.class);
    }
}
