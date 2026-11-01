package lk.swiftdrop.merchant.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Runs every credential flow with verbose logging and checks that no secret reaches a log line: not the API
 * keys, client secrets and access tokens that were issued, and not the wrong secrets that were presented.
 */
class SecretLogLeakTest extends AbstractCredentialTest {

    private static final List<String> VERBOSE_LOGGERS = List.of(
            "lk.swiftdrop", "org.springframework.web", "org.springframework.security");

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Map<String, Level> originalLevels = new HashMap<>();

    @BeforeEach
    void captureLogs() {
        appender.start();
        rootLogger().addAppender(appender);
        for (String name : VERBOSE_LOGGERS) {
            Logger logger = (Logger) LoggerFactory.getLogger(name);
            originalLevels.put(name, logger.getLevel());
            logger.setLevel(Level.DEBUG);
        }
    }

    @AfterEach
    void restoreLogging() {
        rootLogger().detachAppender(appender);
        appender.stop();
        originalLevels.forEach((name, level) -> ((Logger) LoggerFactory.getLogger(name)).setLevel(level));
    }

    @Test
    void noSecretIsLoggedWhileCredentialsAreCreatedRotatedUsedAndRejected() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        List<String> secrets = new ArrayList<>();

        JsonNode createdKey = postAsMerchant("/api/v1/merchants/me/api-keys", "{\"label\":\"Shop\",\"environment\":\"LIVE\"}");
        String rawKey = createdKey.get("apiKey").asText();
        secrets.add(rawKey);
        secrets.add(rawKey.substring(17));
        JsonNode rotatedKey = postAsMerchant("/api/v1/merchants/me/api-keys/" + createdKey.get("id").asText() + "/rotate",
                "{\"graceHours\":1}");
        secrets.add(rotatedKey.get("apiKey").asText());
        secrets.add(rotatedKey.get("apiKey").asText().substring(17));

        JsonNode client = postAsMerchant("/api/v1/merchants/me/oauth-clients",
                "{\"label\":\"Checkout\",\"environment\":\"LIVE\",\"scopes\":[\"orders:write\"]}");
        String clientSecret = client.get("clientSecret").asText();
        String clientId = client.get("clientId").asText();
        secrets.add(clientSecret);
        String newSecret = postAsMerchant("/api/v1/merchants/me/oauth-clients/" + client.get("id").asText() + "/rotate-secret", null)
                .get("clientSecret").asText();
        secrets.add(newSecret);

        String wrongKey = "sd_live_zzzzzzzz_" + "Q".repeat(43);
        String wrongKeyOfRealPrefix = rawKey.substring(0, 17) + "Q".repeat(43);
        secrets.add(wrongKey.substring(17));
        secrets.add(wrongKeyOfRealPrefix.substring(17));

        mockMvc.perform(get("/api/v1/merchant-api/ping").header("X-API-Key", rotatedKey.get("apiKey").asText()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/merchant-api/ping").header("X-API-Key", wrongKey)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/merchant-api/ping").header("X-API-Key", wrongKeyOfRealPrefix))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/merchant-api/ping").header("X-API-Key", "not a key at all"))
                .andExpect(status().isUnauthorized());

        MvcResult tokenResult = mockMvc.perform(post("/api/v1/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "client_credentials")
                        .header(HttpHeaders.AUTHORIZATION, basic(clientId, newSecret)))
                .andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(tokenResult.getResponse().getContentAsString()).get("access_token").asText();
        secrets.add(token);
        secrets.add(token.substring(token.lastIndexOf('.') + 1));
        mockMvc.perform(post("/api/v1/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials").param("client_id", clientId)
                        .param("client_secret", clientSecret))
                .andExpect(status().isUnauthorized());
        secrets.add(token + "x");
        mockMvc.perform(get("/api/v1/merchant-api/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/merchant-api/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());

        merchantService.setRateLimit(merchant.getId(), 1);
        mockMvc.perform(get("/api/v1/merchant-api/ping").header("X-API-Key", rotatedKey.get("apiKey").asText()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/merchant-api/ping").header("X-API-Key", rotatedKey.get("apiKey").asText()))
                .andExpect(status().isTooManyRequests());
        merchantService.suspend(merchant.getId(), "admin", "Review");
        clock.advance(Duration.ofMinutes(5));
        mockMvc.perform(get("/api/v1/merchant-api/ping").header("X-API-Key", rotatedKey.get("apiKey").asText()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/merchants/me/api-keys/" + rotatedKey.get("id").asText())
                        .with(TestAuth.merchant("user-1")))
                .andExpect(status().isNoContent());

        List<String> lines = logLines();
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("created for merchant"));
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("Rejected API key sd_live_zzzzzzzz"));
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("Rejected credentials of OAuth client " + clientId));
        for (String secret : secrets) {
            assertThat(lines).as("log lines containing a secret").noneSatisfy(line -> assertThat(line).contains(secret));
        }
    }

    private JsonNode postAsMerchant(String path, String body) throws Exception {
        MockHttpServletRequestBuilder request = post(path).with(TestAuth.merchant("user-1"));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).isBetween(200, 201);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private List<String> logLines() {
        List<String> lines = new ArrayList<>();
        for (ILoggingEvent event : new ArrayList<>(appender.list)) {
            StringBuilder line = new StringBuilder(event.getFormattedMessage());
            if (event.getThrowableProxy() != null) {
                line.append(' ').append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private static Logger rootLogger() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }
}
