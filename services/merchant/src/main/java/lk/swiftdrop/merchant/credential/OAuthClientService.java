package lk.swiftdrop.merchant.credential;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.merchant.common.ConflictException;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.NotFoundException;
import lk.swiftdrop.merchant.merchant.CurrentMerchantProvider;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OAuth clients of the signed-in merchant (portal) and verification of their credentials (token endpoint and
 * merchant API). Another merchant's client id is "not found", never "forbidden".
 *
 * <p>The raw secret leaves this class only inside the {@link IssuedOAuthClient} returned by {@link #create}
 * and {@link #rotateSecret}; it is not stored, logged or kept.
 */
@Service
@Transactional
public class OAuthClientService {

    private static final int MAX_ACTIVE_CLIENTS = 10;

    private static final Logger log = LoggerFactory.getLogger(OAuthClientService.class);

    private final OAuthClientRepository repository;
    private final CurrentMerchantProvider currentMerchant;
    private final KeyGenerator keyGenerator;
    private final Clock clock;

    public OAuthClientService(OAuthClientRepository repository, CurrentMerchantProvider currentMerchant,
                              KeyGenerator keyGenerator, Clock clock) {
        this.repository = repository;
        this.currentMerchant = currentMerchant;
        this.keyGenerator = keyGenerator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<OAuthClient> list() {
        return repository.findByMerchantIdOrderByCreatedAtDesc(currentMerchant.requireMerchant().getId());
    }

    public IssuedOAuthClient create(String label, ApiEnvironment environment, List<String> scopes) {
        Merchant merchant = currentMerchant.requireApprovedMerchant();
        if (repository.countByMerchantIdAndStatus(merchant.getId(), CredentialStatus.ACTIVE) >= MAX_ACTIVE_CLIENTS) {
            throw new ConflictException(ErrorCode.INVALID_STATE,
                    "You can have at most " + MAX_ACTIVE_CLIENTS + " active OAuth clients. Revoke one first");
        }
        String rawSecret = keyGenerator.secret();
        OAuthClient client = repository.save(OAuthClient.issue(UUID.randomUUID(), merchant.getId(),
                keyGenerator.clientId(), SecretHasher.hash(rawSecret), label, environment,
                Scopes.canonical(scopes), clock.instant()));
        log.info("OAuth client {} created for merchant {}", client.getClientId(), merchant.getId());
        return new IssuedOAuthClient(client, rawSecret);
    }

    /** The previous secret stops working immediately. */
    public IssuedOAuthClient rotateSecret(UUID id) {
        Merchant merchant = currentMerchant.requireApprovedMerchant();
        OAuthClient client = repository.findForUpdate(id, merchant.getId()).orElseThrow(OAuthClientService::notFound);
        if (!client.isActive()) {
            throw new ConflictException(ErrorCode.INVALID_STATE, "A revoked OAuth client cannot get a new secret");
        }
        String rawSecret = keyGenerator.secret();
        client.replaceSecretHash(SecretHasher.hash(rawSecret));
        log.info("Secret of OAuth client {} of merchant {} rotated", client.getClientId(), merchant.getId());
        return new IssuedOAuthClient(client, rawSecret);
    }

    /** Revoking an already revoked client succeeds without change. */
    public void revoke(UUID id) {
        Merchant merchant = currentMerchant.requireMerchant();
        OAuthClient client = repository.findByIdAndMerchantId(id, merchant.getId())
                .orElseThrow(OAuthClientService::notFound);
        if (client.revoke(clock.instant())) {
            log.info("OAuth client {} of merchant {} revoked", client.getClientId(), merchant.getId());
        }
    }

    /**
     * Verifies a client id and secret. Empty when the client is unknown or revoked or the secret is wrong;
     * callers must not tell these cases apart to the client.
     */
    public Optional<OAuthClient> authenticate(String clientId, String secret) {
        if (!KeyGenerator.isClientId(clientId)) {
            log.debug("Rejected an OAuth client id with an invalid format");
            return Optional.empty();
        }
        Optional<OAuthClient> client = repository.findByClientId(clientId)
                .filter(OAuthClient::isActive)
                .filter(candidate -> SecretHasher.matches(secret, candidate.getSecretHash()));
        if (client.isEmpty()) {
            log.debug("Rejected credentials of OAuth client {}", clientId);
            return Optional.empty();
        }
        touch(client.get());
        return client;
    }

    /** The client behind an access token, if it still exists and is not revoked. */
    public Optional<OAuthClient> findActive(UUID id) {
        Optional<OAuthClient> client = repository.findById(id).filter(OAuthClient::isActive);
        client.ifPresent(this::touch);
        return client;
    }

    private void touch(OAuthClient client) {
        Instant now = clock.instant();
        if (CredentialUsage.isDue(client.getLastUsedAt(), now)) {
            repository.touch(client.getId(), now, CredentialUsage.staleBefore(now));
        }
    }

    private static NotFoundException notFound() {
        return new NotFoundException("OAuth client not found");
    }
}
