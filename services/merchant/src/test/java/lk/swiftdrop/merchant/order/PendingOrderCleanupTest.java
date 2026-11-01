package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import lk.swiftdrop.merchant.credential.ControllableClock;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.order.dto.Money;
import lk.swiftdrop.merchant.testsupport.CredentialFixtures.IssuedKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** The clean-up of placeholders that nobody completed, and the short transactions behind order creation. */
@Import(ControllableClock.Config.class)
class PendingOrderCleanupTest extends AbstractOrderTest {

    @Autowired
    private ControllableClock clock;

    @Autowired
    private PendingOrderCleanup cleanup;

    @Autowired
    private OrderWriter writer;

    @BeforeEach
    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void removesPlaceholdersOlderThanFiveMinutesAndNothingElse() {
        MerchantOrder stuck = orderFixtures.order(merchant).externalRef("STUCK").createdAt(clock.instant().minusSeconds(301))
                .savePlaceholder();
        MerchantOrder stuckSandbox = orderFixtures.order(merchant).sandbox().externalRef("STUCK-SBX")
                .createdAt(clock.instant().minus(Duration.ofHours(2))).savePlaceholder();
        MerchantOrder busy = orderFixtures.order(merchant).externalRef("BUSY").createdAt(clock.instant().minusSeconds(299))
                .savePlaceholder();
        MerchantOrder fresh = orderFixtures.order(merchant).externalRef("FRESH").createdAt(clock.instant())
                .savePlaceholder();
        MerchantOrder oldButBooked = orderFixtures.order(merchant).externalRef("BOOKED")
                .createdAt(clock.instant().minus(Duration.ofDays(30))).save();
        Merchant other = fixtures.approvedMerchant("owner-2");
        MerchantOrder otherStuck = orderFixtures.order(other).externalRef("STUCK").createdAt(clock.instant().minusSeconds(900))
                .savePlaceholder();

        cleanup.removeStalePlaceholders();

        assertThat(orders.findAll()).extracting(MerchantOrder::getId)
                .containsExactlyInAnyOrder(busy.getId(), fresh.getId(), oldButBooked.getId());
        assertThat(orders.existsById(stuck.getId())).isFalse();
        assertThat(orders.existsById(stuckSandbox.getId())).isFalse();
        assertThat(orders.existsById(otherStuck.getId())).isFalse();
    }

    @Test
    void placeholderBecomesStaleAsTimePasses() {
        MerchantOrder placeholder = orderFixtures.order(merchant).createdAt(clock.instant()).savePlaceholder();

        clock.advance(Duration.ofSeconds(300));
        cleanup.removeStalePlaceholders();
        assertThat(orders.existsById(placeholder.getId())).isTrue();

        clock.advance(Duration.ofSeconds(1));
        cleanup.removeStalePlaceholders();
        assertThat(orders.existsById(placeholder.getId())).isFalse();
    }

    @Test
    void doingNothingWhenThereIsNothingToRemove() {
        assertThatCode(cleanup::removeStalePlaceholders).doesNotThrowAnyException();
    }

    @Test
    void keyAndExternalRefOfARemovedPlaceholderCanBeUsedAgain() throws Exception {
        String hash = RequestFingerprint.of(OrderFixtures.request("ORDER-1"), false);
        orderFixtures.order(merchant).idempotencyKey("key-1", hash).externalRef("ORDER-1")
                .createdAt(clock.instant().minusSeconds(600)).savePlaceholder();
        IssuedKey key = liveKey();
        createViaApi(key, OrderFixtures.payload("ORDER-1"), "key-1").andExpect(status().isConflict());

        cleanup.removeStalePlaceholders();

        createViaApi(key, OrderFixtures.payload("ORDER-1"), "key-1").andExpect(status().isCreated());
    }

    @Test
    void completingAPlaceholderThatWasRemovedFailsLoudly() {
        UUID gone = UUID.randomUUID();

        assertThatThrownBy(() -> writer.complete(gone, "p", "SD1", new Money(new BigDecimal("1.00"), "LKR"),
                clock.instant()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(gone.toString());
    }

    @Test
    void discardingAMissingPlaceholderIsHarmless() {
        assertThatCode(() -> writer.discard(UUID.randomUUID())).doesNotThrowAnyException();
    }
}
