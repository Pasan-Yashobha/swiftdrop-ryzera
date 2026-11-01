package lk.swiftdrop.merchant.webhook;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.merchant.common.ConflictException;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.merchant.CurrentMerchantProvider;
import lk.swiftdrop.merchant.webhook.dto.WebhookDeliveryPage;
import lk.swiftdrop.merchant.webhook.dto.WebhookTestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A merchant's webhook configuration (CONTRACT sections 5.5 and 8). The portal methods act on "my merchant" and
 * require it to be APPROVED where they create credentials or make this service call out; the internal lookups
 * serve the Webhook Delivery Service.
 *
 * <p>Transactions are opened explicitly and only around the database work: URL checks involve DNS and the test
 * ping waits for a remote server, neither of which should hold a database connection. The secret is logged
 * nowhere; it leaves this class only in the creation and rotation results and through {@link #revealSecret}.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookConfigRepository repository;
    private final CurrentMerchantProvider currentMerchant;
    private final SecretCipher cipher;
    private final WebhookSecretGenerator secretGenerator;
    private final UrlSafetyValidator urlValidator;
    private final WebhookPinger pinger;
    private final DeliveryLogClient deliveryLog;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public WebhookService(WebhookConfigRepository repository, CurrentMerchantProvider currentMerchant,
                          SecretCipher cipher, WebhookSecretGenerator secretGenerator,
                          UrlSafetyValidator urlValidator, WebhookPinger pinger, DeliveryLogClient deliveryLog,
                          TransactionTemplate transaction, Clock clock) {
        this.repository = repository;
        this.currentMerchant = currentMerchant;
        this.cipher = cipher;
        this.secretGenerator = secretGenerator;
        this.urlValidator = urlValidator;
        this.pinger = pinger;
        this.deliveryLog = deliveryLog;
        this.transaction = transaction;
        this.clock = clock;
    }

    /**
     * Result of {@link #save}.
     *
     * @param config the stored configuration
     * @param secret the new signing secret when the configuration was just created, otherwise null
     */
    public record SaveResult(WebhookConfig config, String secret) {

        @Override
        public String toString() {
            return "SaveResult[merchantId=" + config.getMerchantId() + "]";
        }
    }

    public Optional<WebhookConfig> findMine() {
        return repository.findById(currentMerchant.requireMerchant().getId());
    }

    /**
     * Creates or replaces the configuration of the caller's merchant. A new configuration gets a fresh secret that
     * is returned here and never again; an existing one keeps its secret.
     */
    public SaveResult save(String url, boolean active, List<String> events) {
        UUID merchantId = currentMerchant.requireApprovedMerchant().getId();
        String storedEvents = WebhookEvents.join(WebhookEvents.normalise(events));
        urlValidator.validate(url);
        SaveResult result = transaction.execute(status -> upsert(merchantId, url, active, storedEvents));
        log.info("Webhook configuration of merchant {} saved (active={}, newSecret={})", merchantId, active,
                result.secret() != null);
        return result;
    }

    /** @return the new secret, which is the only time it is shown */
    public String rotateSecret() {
        UUID merchantId = currentMerchant.requireApprovedMerchant().getId();
        String secret = transaction.execute(status -> {
            WebhookConfig config = requireConfig(merchantId);
            String newSecret = secretGenerator.generate();
            config.replaceSecret(cipher.encrypt(newSecret), secretGenerator.hintOf(newSecret), clock.instant());
            return newSecret;
        });
        log.info("Webhook secret of merchant {} rotated", merchantId);
        return secret;
    }

    /** Sends a signed {@code webhook.test} event and remembers the outcome on the configuration. */
    public WebhookTestResult test() {
        UUID merchantId = currentMerchant.requireApprovedMerchant().getId();
        WebhookConfig config = requireConfig(merchantId);
        WebhookTestResult result = pinger.ping(merchantId, config.getUrl(), revealSecret(config));
        repository.recordTestResult(merchantId, clock.instant(), describe(result));
        return result;
    }

    /** Removing a configuration that does not exist is not an error. */
    public void delete() {
        UUID merchantId = currentMerchant.requireMerchant().getId();
        repository.deleteById(merchantId);
        log.info("Webhook configuration of merchant {} deleted", merchantId);
    }

    public WebhookDeliveryPage deliveries(Pageable pageable) {
        return deliveryLog.fetch(currentMerchant.requireMerchant().getId(), pageable);
    }

    public Optional<WebhookConfig> findByMerchantId(UUID merchantId) {
        return repository.findById(merchantId);
    }

    /** The decrypted signing secret; only for the test ping and the internal endpoint. */
    public String revealSecret(WebhookConfig config) {
        return cipher.decrypt(config.getSecretCiphertext());
    }

    private SaveResult upsert(UUID merchantId, String url, boolean active, String events) {
        Optional<WebhookConfig> existing = repository.findById(merchantId);
        if (existing.isPresent()) {
            WebhookConfig config = existing.get();
            config.update(url, active, events, clock.instant());
            return new SaveResult(config, null);
        }
        String secret = secretGenerator.generate();
        WebhookConfig created = WebhookConfig.create(merchantId, url, active, events, cipher.encrypt(secret),
                secretGenerator.hintOf(secret), clock.instant());
        return new SaveResult(repository.saveAndFlush(created), secret);
    }

    private WebhookConfig requireConfig(UUID merchantId) {
        return repository.findById(merchantId).orElseThrow(() -> new ConflictException(
                ErrorCode.WEBHOOK_NOT_CONFIGURED, "No webhook is configured for this merchant"));
    }

    /** Short status line for the settings page, e.g. {@code 200 OK in 123 ms} or {@code failed: timeout}. */
    private static String describe(WebhookTestResult result) {
        if (result.httpStatus() == null) {
            return "failed: " + result.error();
        }
        HttpStatus status = HttpStatus.resolve(result.httpStatus());
        String reason = status == null ? "" : " " + status.getReasonPhrase();
        return result.httpStatus() + reason + " in " + result.durationMs() + " ms";
    }
}
