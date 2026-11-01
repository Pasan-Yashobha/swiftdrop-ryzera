package lk.swiftdrop.merchant.merchant;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.merchant.common.ConflictException;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.NotFoundException;
import lk.swiftdrop.merchant.common.PiiMasker;
import lk.swiftdrop.merchant.event.EventTypes;
import lk.swiftdrop.merchant.event.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merchant registration and the admin approval workflow. Every state change that other services care about
 * writes its outbox event in the same transaction.
 */
@Service
@Transactional
public class MerchantService {

    private static final Logger log = LoggerFactory.getLogger(MerchantService.class);

    private final MerchantRepository repository;
    private final OutboxService outbox;
    private final Clock clock;

    public MerchantService(MerchantRepository repository, OutboxService outbox, Clock clock) {
        this.repository = repository;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Registers the business of {@code ownerUserId}; every user can own one merchant. */
    public Merchant register(String ownerUserId, MerchantProfile profile) {
        if (repository.existsByOwnerUserId(ownerUserId)) {
            throw alreadyRegistered();
        }
        Merchant merchant = Merchant.register(UUID.randomUUID(), ownerUserId, profile, clock.instant());
        try {
            // Flush now: a concurrent registration of the same owner violates the unique constraint here.
            repository.saveAndFlush(merchant);
        } catch (DataIntegrityViolationException race) {
            throw alreadyRegistered();
        }
        enqueueRegistered(merchant);
        log.info("Merchant {} registered (contact {})", merchant.getId(), PiiMasker.maskEmail(profile.contactEmail()));
        return merchant;
    }

    /** Edits the details of the caller's own merchant; status and rate limit are not editable here. */
    public Merchant update(String ownerUserId, MerchantProfile profile) {
        Merchant merchant = getByOwner(ownerUserId);
        merchant.updateProfile(profile, clock.instant());
        return merchant;
    }

    /** A rejected merchant asks for a new review. */
    public Merchant resubmit(String ownerUserId) {
        Merchant merchant = getByOwner(ownerUserId);
        merchant.resubmit(clock.instant());
        enqueueRegistered(merchant);
        log.info("Merchant {} resubmitted for review", merchant.getId());
        return merchant;
    }

    @Transactional(readOnly = true)
    public Optional<Merchant> findByOwner(String ownerUserId) {
        return repository.findByOwnerUserId(ownerUserId);
    }

    @Transactional(readOnly = true)
    public Merchant getByOwner(String ownerUserId) {
        return findByOwner(ownerUserId).orElseThrow(() ->
                new NotFoundException(ErrorCode.MERCHANT_NOT_REGISTERED, "You have not registered a merchant account yet"));
    }

    /** Lookup for credential checks; the caller decides what a missing merchant means. */
    @Transactional(readOnly = true)
    public Optional<Merchant> findById(UUID id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Merchant getById(UUID id) {
        return findById(id).orElseThrow(() -> new NotFoundException("Merchant not found"));
    }

    @Transactional(readOnly = true)
    public Page<Merchant> search(MerchantStatus status, String search, Pageable pageable) {
        return repository.findAll(MerchantSpecifications.filter(status, search), pageable);
    }

    public Merchant approve(UUID id, String adminId) {
        Merchant merchant = getById(id);
        merchant.approve(adminId, clock.instant());
        outbox.enqueue(EventTypes.MERCHANT_APPROVED, merchant.getId().toString(),
                Map.of("merchantId", merchant.getId().toString(), "decidedBy", adminId));
        log.info("Merchant {} approved by {}", id, adminId);
        return merchant;
    }

    public Merchant reject(UUID id, String adminId, String reason) {
        Merchant merchant = getById(id);
        merchant.reject(adminId, reason, clock.instant());
        outbox.enqueue(EventTypes.MERCHANT_REJECTED, merchant.getId().toString(),
                Map.of("merchantId", merchant.getId().toString(), "decidedBy", adminId, "reason", reason));
        log.info("Merchant {} rejected by {}", id, adminId);
        return merchant;
    }

    /** Takes effect immediately for the merchant API, which checks the status on every request. */
    public Merchant suspend(UUID id, String adminId, String reason) {
        Merchant merchant = getById(id);
        merchant.suspend(adminId, reason, clock.instant());
        log.info("Merchant {} suspended by {}", id, adminId);
        return merchant;
    }

    public Merchant reinstate(UUID id, String adminId) {
        Merchant merchant = getById(id);
        merchant.reinstate(adminId, clock.instant());
        log.info("Merchant {} reinstated by {}", id, adminId);
        return merchant;
    }

    public Merchant setRateLimit(UUID id, int requestsPerMinute) {
        Merchant merchant = getById(id);
        merchant.changeRateLimit(requestsPerMinute, clock.instant());
        log.info("Rate limit of merchant {} set to {} per minute", id, requestsPerMinute);
        return merchant;
    }

    private void enqueueRegistered(Merchant merchant) {
        outbox.enqueue(EventTypes.MERCHANT_REGISTERED, merchant.getId().toString(),
                Map.of("merchantId", merchant.getId().toString(), "businessName", merchant.getBusinessName()));
    }

    private static ConflictException alreadyRegistered() {
        return new ConflictException(ErrorCode.MERCHANT_ALREADY_REGISTERED,
                "You have already registered a merchant account");
    }
}
