package lk.swiftdrop.merchant.credential;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.merchant.common.ConflictException;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.NotFoundException;
import lk.swiftdrop.merchant.event.EventTypes;
import lk.swiftdrop.merchant.event.OutboxService;
import lk.swiftdrop.merchant.merchant.CurrentMerchantProvider;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API keys of the signed-in merchant (portal) and their verification (merchant API). A key is addressed by
 * its id within the merchant's own keys; another merchant's id is "not found", never "forbidden".
 *
 * <p>The raw key leaves this class only inside the {@link IssuedApiKey} returned by {@link #create} and
 * {@link #rotate}; it is not stored, logged or kept.
 */
@Service
@Transactional
public class ApiKeyService {

    private static final int MAX_ACTIVE_KEYS = 10;

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    private final ApiKeyRepository repository;
    private final CurrentMerchantProvider currentMerchant;
    private final KeyGenerator keyGenerator;
    private final OutboxService outbox;
    private final Clock clock;

    public ApiKeyService(ApiKeyRepository repository, CurrentMerchantProvider currentMerchant,
                         KeyGenerator keyGenerator, OutboxService outbox, Clock clock) {
        this.repository = repository;
        this.currentMerchant = currentMerchant;
        this.keyGenerator = keyGenerator;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ApiKey> list() {
        return repository.findByMerchantIdOrderByCreatedAtDesc(currentMerchant.requireMerchant().getId());
    }

    public IssuedApiKey create(String label, ApiEnvironment environment) {
        Merchant merchant = currentMerchant.requireApprovedMerchant();
        Instant now = clock.instant();
        if (repository.countUsable(merchant.getId(), now) >= MAX_ACTIVE_KEYS) {
            throw new ConflictException(ErrorCode.INVALID_STATE,
                    "You can have at most " + MAX_ACTIVE_KEYS + " active API keys. Revoke one first");
        }
        IssuedApiKey issued = issue(merchant.getId(), label, environment, null, now);
        log.info("API key {} created for merchant {}", issued.key().getKeyPrefix(), merchant.getId());
        return issued;
    }

    /**
     * Replaces a key by a new one with the same label and environment. The old key keeps working for
     * {@code graceHours} (0 revokes it at once). Rotation does not count against the limit of active keys:
     * the new key takes the place of the old one.
     */
    public IssuedApiKey rotate(UUID id, int graceHours) {
        Merchant merchant = currentMerchant.requireApprovedMerchant();
        Instant now = clock.instant();
        ApiKey old = repository.findForUpdate(id, merchant.getId()).orElseThrow(ApiKeyService::notFound);
        if (!old.isRotatable()) {
            throw new ConflictException(ErrorCode.INVALID_STATE,
                    "Only active API keys that have not been rotated yet can be rotated");
        }
        IssuedApiKey replacement = issue(merchant.getId(), old.getLabel(), old.getEnvironment(), old.getId(), now);
        if (graceHours == 0) {
            old.revoke(now);
        } else {
            old.expireAt(now.plus(Duration.ofHours(graceHours)));
        }
        String newPrefix = replacement.key().getKeyPrefix();
        outbox.enqueue(EventTypes.MERCHANT_API_KEY_ROTATED, merchant.getId().toString(),
                Map.of("merchantId", merchant.getId().toString(), "keyPrefix", newPrefix));
        log.info("API key {} of merchant {} rotated to {} (grace {} h)", old.getKeyPrefix(), merchant.getId(),
                newPrefix, graceHours);
        return replacement;
    }

    /** Revoking an already revoked key succeeds without change. */
    public void revoke(UUID id) {
        Merchant merchant = currentMerchant.requireMerchant();
        ApiKey key = repository.findByIdAndMerchantId(id, merchant.getId()).orElseThrow(ApiKeyService::notFound);
        if (key.revoke(clock.instant())) {
            log.info("API key {} of merchant {} revoked", key.getKeyPrefix(), merchant.getId());
        }
    }

    /**
     * Finds the key a request presented. Empty when the key is malformed, unknown, revoked, expired or its
     * secret is wrong; callers must not tell these cases apart to the client.
     */
    public Optional<ApiKey> authenticate(String rawKey) {
        Optional<ApiKeyFormat.Parsed> parsed = ApiKeyFormat.parse(rawKey);
        if (parsed.isEmpty()) {
            log.debug("Rejected an API key with an invalid format");
            return Optional.empty();
        }
        Instant now = clock.instant();
        Optional<ApiKey> key = repository.findByKeyPrefix(parsed.get().prefix())
                .filter(candidate -> SecretHasher.matches(rawKey, candidate.getKeyHash()))
                .filter(candidate -> candidate.isUsableAt(now));
        if (key.isEmpty()) {
            log.debug("Rejected API key {}", parsed.get().prefix());
            return Optional.empty();
        }
        if (CredentialUsage.isDue(key.get().getLastUsedAt(), now)) {
            repository.touch(key.get().getId(), now, CredentialUsage.staleBefore(now));
        }
        return key;
    }

    private IssuedApiKey issue(UUID merchantId, String label, ApiEnvironment environment, UUID rotatedFromId,
                               Instant now) {
        String rawKey = keyGenerator.apiKey(environment);
        ApiKey key = repository.save(ApiKey.issue(UUID.randomUUID(), merchantId, label, environment,
                rawKey.substring(0, ApiKeyFormat.PREFIX_LENGTH), SecretHasher.hash(rawKey), rotatedFromId, now));
        return new IssuedApiKey(key, rawKey);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("API key not found");
    }
}
