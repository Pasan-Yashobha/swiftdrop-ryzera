package lk.swiftdrop.merchant.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.merchant.MerchantRepository;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Runs every webhook flow, successful and failing, with all loggers at DEBUG and proves that neither secrets,
 * ciphertext nor webhook URLs (which often carry tokens) reach the log.
 */
class WebhookLoggingTest extends AbstractWebhookLiveTest {

    private static final String URL_TOKEN = "tok_url_secret_98765";

    /**
     * Not part of the service: Hibernate dumps whole entities at DEBUG, the JDK HTTP client logs request URLs at
     * DEBUG, and the local test server logs its own requests. They stay at INFO, as in any real deployment.
     */
    private static final List<String> FOREIGN_LOGGERS = List.of("org.hibernate", "jdk.internal.httpclient",
            "com.sun.net.httpserver");

    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Map<String, Level> originalLevels = new HashMap<>();

    @Autowired
    private MerchantRepository merchants;

    @BeforeEach
    void captureEverything() {
        originalLevels.put(Logger.ROOT_LOGGER_NAME, root.getLevel());
        root.setLevel(Level.DEBUG);
        for (String name : FOREIGN_LOGGERS) {
            Logger logger = (Logger) LoggerFactory.getLogger(name);
            originalLevels.put(name, logger.getLevel());
            logger.setLevel(Level.INFO);
        }
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void restoreLogging() {
        root.detachAppender(logs);
        originalLevels.forEach((name, level) -> ((Logger) LoggerFactory.getLogger(name)).setLevel(level));
    }

    @Test
    void secretsCiphertextAndUrlsNeverAppearInTheLogs() throws Exception {
        List<String> sensitive = new ArrayList<>();
        try (TestHttpServer healthy = TestHttpServer.responding(200, "");
             TestHttpServer failing = TestHttpServer.responding(500, "{\"error\":\"" + URL_TOKEN + "\"}")) {
            String healthyUrl = healthy.baseUrl() + "/hook-path-xyz?token=" + URL_TOKEN;
            String failingUrl = failing.baseUrl() + "/failing-path-xyz?token=" + URL_TOKEN;

            String firstSecret = createWebhook("user-1", healthyUrl);
            sensitive.add(firstSecret);
            sensitive.add(storedCiphertext());
            putWebhook("user-1", failingUrl, true, List.of("parcel.created")).andExpect(status().isOk());
            sendTest("user-1").andExpect(status().isOk());

            String rotatedSecret = objectMapper.readTree(rotate("user-1").andReturn().getResponse()
                    .getContentAsString()).get("secret").asText();
            sensitive.add(rotatedSecret);
            sensitive.add(storedCiphertext());
            putWebhook("user-1", healthyUrl, true, List.of("*")).andExpect(status().isOk());
            sendTest("user-1").andExpect(status().isOk());

            Merchant merchant = merchants.findByOwnerUserId("user-1").orElseThrow();
            mockMvc.perform(get("/internal/merchants/" + merchant.getId() + "/webhook").with(TestAuth.internal()))
                    .andExpect(status().isOk());
            getWebhook("user-1").andExpect(status().isOk());

            // Failures: unsafe URL, unapproved merchant, missing configuration, corrupted stored secret.
            putWebhook("user-1", "ftp://example.com/hook?token=" + URL_TOKEN, true, List.of("*"))
                    .andExpect(status().isUnprocessableEntity());
            fixtures.pendingMerchant("user-2");
            putWebhook("user-2", healthyUrl, true, List.of("*")).andExpect(status().isForbidden());
            rotate("user-2").andExpect(status().isForbidden());
            fixtures.approvedMerchant("user-3");
            rotate("user-3").andExpect(status().isConflict());
            sendTest("user-3").andExpect(status().isConflict());
            jdbc.update("update webhook_configs set secret_ciphertext = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'");
            sendTest("user-1").andExpect(status().isInternalServerError());
            mockMvc.perform(get("/internal/merchants/" + merchant.getId() + "/webhook").with(TestAuth.internal()))
                    .andExpect(status().isInternalServerError());
            deleteWebhook("user-1").andExpect(status().isNoContent());
        }

        String everything = renderedLogs();
        assertThat(logs.list).as("the capture works").isNotEmpty();
        assertThat(everything).contains("rotated", "saved", "deleted");
        assertThat(everything).doesNotContain(sensitive.toArray(String[]::new));
        assertThat(everything).doesNotContain(URL_TOKEN, "hook-path-xyz", "failing-path-xyz");
    }

    private String renderedLogs() {
        return logs.list.stream()
                .map(event -> event.getFormattedMessage()
                        + (event.getThrowableProxy() == null ? "" : "\n" + ThrowableProxyUtil.asString(event.getThrowableProxy())))
                .collect(Collectors.joining("\n"));
    }
}
