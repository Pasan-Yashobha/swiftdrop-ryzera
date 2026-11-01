package lk.swiftdrop.merchant.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import lk.swiftdrop.merchant.common.ConflictException;
import lk.swiftdrop.merchant.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MerchantTest {

    private static final Instant T0 = Instant.parse("2026-11-01T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-01T09:00:00Z");
    private static final MerchantProfile PROFILE = new MerchantProfile("Lanka Crafts", "PV1", "owner@crafts.lk",
            "+94771234567", "12 Galle Road", "Colombo", "https://crafts.lk");

    @Test
    void newMerchantIsPendingWithDefaultRateLimit() {
        Merchant merchant = Merchant.register(UUID.randomUUID(), "user-1", PROFILE, T0);

        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.PENDING);
        assertThat(merchant.getRateLimitPerMinute()).isEqualTo(60);
        assertThat(merchant.getOwnerUserId()).isEqualTo("user-1");
        assertThat(merchant.getBusinessName()).isEqualTo("Lanka Crafts");
        assertThat(merchant.getCreatedAt()).isEqualTo(T0);
        assertThat(merchant.getUpdatedAt()).isEqualTo(T0);
        assertThat(merchant.isApproved()).isFalse();
        assertThat(merchant.getDecidedBy()).isNull();
    }

    @Test
    void updateProfileReplacesDetailsButKeepsStatus() {
        Merchant merchant = merchantIn(MerchantStatus.APPROVED);

        merchant.updateProfile(new MerchantProfile("New Name", null, "new@crafts.lk", "+94770000000", "1 Lane",
                "Kandy", null), T1);

        assertThat(merchant.getBusinessName()).isEqualTo("New Name");
        assertThat(merchant.getBusinessRegNo()).isNull();
        assertThat(merchant.getCity()).isEqualTo("Kandy");
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(merchant.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void approveRecordsDecisionAndClearsEarlierReason() {
        Merchant merchant = merchantIn(MerchantStatus.REJECTED);

        merchant.approve("admin-1", T1);

        assertThat(merchant.isApproved()).isTrue();
        assertThat(merchant.getDecidedBy()).isEqualTo("admin-1");
        assertThat(merchant.getDecidedAt()).isEqualTo(T1);
        assertThat(merchant.getDecisionReason()).isNull();
    }

    @Test
    void rejectAndSuspendKeepTheReason() {
        Merchant rejected = merchantIn(MerchantStatus.PENDING);
        rejected.reject("admin-1", "Incomplete documents", T1);
        Merchant suspended = merchantIn(MerchantStatus.APPROVED);
        suspended.suspend("admin-2", "Chargebacks", T1);

        assertThat(rejected.getStatus()).isEqualTo(MerchantStatus.REJECTED);
        assertThat(rejected.getDecisionReason()).isEqualTo("Incomplete documents");
        assertThat(suspended.getStatus()).isEqualTo(MerchantStatus.SUSPENDED);
        assertThat(suspended.getDecisionReason()).isEqualTo("Chargebacks");
        assertThat(suspended.getDecidedBy()).isEqualTo("admin-2");
    }

    @Test
    void resubmitClearsTheDecision() {
        Merchant merchant = merchantIn(MerchantStatus.REJECTED);

        merchant.resubmit(T1);

        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.PENDING);
        assertThat(merchant.getDecidedBy()).isNull();
        assertThat(merchant.getDecidedAt()).isNull();
        assertThat(merchant.getDecisionReason()).isNull();
    }

    @Test
    void changeRateLimitUpdatesTheLimit() {
        Merchant merchant = merchantIn(MerchantStatus.APPROVED);

        merchant.changeRateLimit(500, T1);

        assertThat(merchant.getRateLimitPerMinute()).isEqualTo(500);
        assertThat(merchant.getUpdatedAt()).isEqualTo(T1);
    }

    @ParameterizedTest
    @EnumSource(MerchantStatus.class)
    void approveIsAllowedFromPendingRejectedAndSuspended(MerchantStatus from) {
        assertTransition(from, EnumSet.of(MerchantStatus.PENDING, MerchantStatus.REJECTED, MerchantStatus.SUSPENDED),
                merchant -> merchant.approve("admin", T1));
    }

    @ParameterizedTest
    @EnumSource(MerchantStatus.class)
    void rejectIsAllowedFromPendingOnly(MerchantStatus from) {
        assertTransition(from, EnumSet.of(MerchantStatus.PENDING), merchant -> merchant.reject("admin", "No", T1));
    }

    @ParameterizedTest
    @EnumSource(MerchantStatus.class)
    void suspendIsAllowedFromApprovedOnly(MerchantStatus from) {
        assertTransition(from, EnumSet.of(MerchantStatus.APPROVED), merchant -> merchant.suspend("admin", "No", T1));
    }

    @ParameterizedTest
    @EnumSource(MerchantStatus.class)
    void reinstateIsAllowedFromSuspendedOnly(MerchantStatus from) {
        assertTransition(from, EnumSet.of(MerchantStatus.SUSPENDED), merchant -> merchant.reinstate("admin", T1));
    }

    @ParameterizedTest
    @EnumSource(MerchantStatus.class)
    void resubmitIsAllowedFromRejectedOnly(MerchantStatus from) {
        assertTransition(from, EnumSet.of(MerchantStatus.REJECTED), merchant -> merchant.resubmit(T1));
    }

    private static void assertTransition(MerchantStatus from, Set<MerchantStatus> allowedFrom,
                                         Consumer<Merchant> transition) {
        Merchant merchant = merchantIn(from);
        if (allowedFrom.contains(from)) {
            transition.accept(merchant);
            assertThat(merchant.getStatus()).isNotEqualTo(from);
        } else {
            assertThatThrownBy(() -> transition.accept(merchant))
                    .isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_STATE))
                    .hasMessageContaining(from.name());
            assertThat(merchant.getStatus()).isEqualTo(from);
        }
    }

    private static Merchant merchantIn(MerchantStatus status) {
        Merchant merchant = Merchant.register(UUID.randomUUID(), "user-1", PROFILE, T0);
        switch (status) {
            case PENDING -> { }
            case APPROVED -> merchant.approve("admin", T0);
            case REJECTED -> merchant.reject("admin", "No", T0);
            case SUSPENDED -> {
                merchant.approve("admin", T0);
                merchant.suspend("admin", "No", T0);
            }
        }
        return merchant;
    }
}
