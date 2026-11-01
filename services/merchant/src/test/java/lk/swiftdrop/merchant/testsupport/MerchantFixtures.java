package lk.swiftdrop.merchant.testsupport;

import java.time.Clock;
import java.util.UUID;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.merchant.MerchantProfile;
import lk.swiftdrop.merchant.merchant.MerchantRepository;
import lk.swiftdrop.merchant.merchant.MerchantStatus;
import org.springframework.stereotype.Component;

/** Persists merchants in a given state for tests of the credential, order and webhook modules. */
@Component
public class MerchantFixtures {

    private static final String FIXTURE_ADMIN = "fixture-admin";

    private final MerchantRepository repository;
    private final Clock clock;

    public MerchantFixtures(MerchantRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Merchant approvedMerchant(String ownerUserId) {
        return merchant(ownerUserId, MerchantStatus.APPROVED);
    }

    public Merchant pendingMerchant(String ownerUserId) {
        return merchant(ownerUserId, MerchantStatus.PENDING);
    }

    /** Creates and stores a merchant owned by {@code ownerUserId} that has reached {@code status}. */
    public Merchant merchant(String ownerUserId, MerchantStatus status) {
        Merchant merchant = Merchant.register(UUID.randomUUID(), ownerUserId, profileFor(ownerUserId), clock.instant());
        switch (status) {
            case PENDING -> { }
            case APPROVED -> merchant.approve(FIXTURE_ADMIN, clock.instant());
            case REJECTED -> merchant.reject(FIXTURE_ADMIN, "Fixture rejection", clock.instant());
            case SUSPENDED -> {
                merchant.approve(FIXTURE_ADMIN, clock.instant());
                merchant.suspend(FIXTURE_ADMIN, "Fixture suspension", clock.instant());
            }
        }
        return repository.save(merchant);
    }

    /** Valid business details that are unique per owner. */
    public static MerchantProfile profileFor(String ownerUserId) {
        return new MerchantProfile("Shop of " + ownerUserId, null, ownerUserId + "@shop.example",
                "+94771234567", "12 Galle Road", "Colombo", null);
    }
}
