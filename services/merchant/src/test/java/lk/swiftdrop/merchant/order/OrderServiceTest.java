package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.merchant.common.ConflictException;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.ForbiddenException;
import lk.swiftdrop.merchant.common.NotFoundException;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.merchant.MerchantService;
import lk.swiftdrop.merchant.merchant.MerchantStatus;
import lk.swiftdrop.merchant.order.dto.Money;
import lk.swiftdrop.merchant.testsupport.MerchantFixtures;
import lk.swiftdrop.merchant.testsupport.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** The branches of order creation that need a database misbehaving in a way a real one cannot be made to. */
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    private final MerchantService merchants = mock(MerchantService.class);
    private final MerchantOrderRepository orders = mock(MerchantOrderRepository.class);
    private final OrderWriter writer = mock(OrderWriter.class);
    private final ParcelClient parcelClient = mock(ParcelClient.class);
    private final SandboxSimulator simulator = mock(SandboxSimulator.class);
    private final OrderService service = new OrderService(merchants, orders, writer, parcelClient, simulator,
            new OrderMapper(TestProperties.defaults()), Clock.fixed(NOW, ZoneOffset.UTC));

    private UUID merchantId;

    @BeforeEach
    void approvedMerchant() {
        Merchant merchant = Merchant.register(UUID.randomUUID(), "owner", MerchantFixtures.profileFor("owner"), NOW);
        merchant.approve("admin", NOW);
        merchantId = merchant.getId();
        when(merchants.getById(merchantId)).thenReturn(merchant);
        when(writer.insertPlaceholder(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(writer.complete(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            MerchantOrder order = orders.findById(invocation.getArgument(0)).orElseThrow();
            order.complete(invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3), NOW);
            return order;
        });
    }

    private NewOrder command(boolean sandbox, String key) {
        return new NewOrder(merchantId, OrderFixtures.request("ORDER-1"), OrderSource.API, sandbox, key);
    }

    @Test
    void suspendedMerchantCannotCreateOrdersWhateverTheCallerClaims() {
        Merchant suspended = Merchant.register(UUID.randomUUID(), "o2", MerchantFixtures.profileFor("o2"), NOW);
        suspended.approve("admin", NOW);
        suspended.suspend("admin", "review", NOW);
        when(merchants.getById(suspended.getId())).thenReturn(suspended);

        assertThatThrownBy(() -> service.create(
                new NewOrder(suspended.getId(), OrderFixtures.request("ORDER-1"), OrderSource.API, false, "k")))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.MERCHANT_NOT_APPROVED));

        assertThat(suspended.getStatus()).isEqualTo(MerchantStatus.SUSPENDED);
        verifyNoInteractions(orders, writer, parcelClient);
    }

    @Test
    void unknownMerchantIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(merchants.getById(unknown)).thenThrow(new NotFoundException("Merchant not found"));

        assertThatThrownBy(() -> service.create(
                new NewOrder(unknown, OrderFixtures.request("ORDER-1"), OrderSource.API, false, null)))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(writer, parcelClient);
    }

    @Test
    void conflictingRowThatDisappearsIsRetried() {
        when(orders.findByMerchantIdAndIdempotencyKey(eq(merchantId), eq("k"))).thenReturn(Optional.empty());
        when(writer.insertPlaceholder(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orders.findById(any())).thenAnswer(invocation -> Optional.of(storedPlaceholder()));
        when(parcelClient.createParcel(any()))
                .thenReturn(new ParcelBooking("p-1", "SD1", new Money(new BigDecimal("450.00"), "LKR")));

        OrderCreation created = service.create(command(false, "k"));

        assertThat(created.replayed()).isFalse();
        verify(writer, times(2)).insertPlaceholder(any());
        verify(orders, times(2)).findByMerchantIdAndIdempotencyKey(merchantId, "k");
        verify(parcelClient, times(1)).createParcel(any());
    }

    @Test
    void givesUpWhenTheKeyKeepsBeingTakenAndFreedAgain() {
        when(orders.findByMerchantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(writer.insertPlaceholder(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.create(command(false, "k")))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_STATE));

        verify(writer, times(3)).insertPlaceholder(any());
        verifyNoInteractions(parcelClient);
    }

    @Test
    void sandboxTrackingNumberThatIsTakenIsGeneratedAgain() {
        when(simulator.newTrackingNo()).thenReturn("SBX000000001", "SBX000000002");
        when(simulator.feeFor(any())).thenReturn(new Money(new BigDecimal("600.00"), "LKR"));
        when(orders.existsByTrackingNo("SBX000000001")).thenReturn(true);
        when(orders.existsByTrackingNo("SBX000000002")).thenReturn(false);
        when(orders.findById(any())).thenAnswer(invocation -> Optional.of(storedPlaceholder()));

        OrderCreation created = service.create(command(true, null));

        assertThat(created.order().trackingNo()).isEqualTo("SBX000000002");
        verify(writer).complete(any(), eq(null), eq("SBX000000002"), any(), any());
        verify(parcelClient, never()).createParcel(any());
    }

    private MerchantOrder storedPlaceholder() {
        return MerchantOrder.placeholder(UUID.randomUUID(), merchantId, OrderFixtures.request("ORDER-1"),
                OrderSource.API, false, null, "hash", NOW);
    }
}
