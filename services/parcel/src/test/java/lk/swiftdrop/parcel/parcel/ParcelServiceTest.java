package lk.swiftdrop.parcel.parcel;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.parcel.common.ConflictException;
import lk.swiftdrop.parcel.common.ErrorCode;
import lk.swiftdrop.parcel.common.ForbiddenException;
import lk.swiftdrop.parcel.event.OutboxService;
import lk.swiftdrop.parcel.idempotency.IdempotencyService;
import lk.swiftdrop.parcel.parcel.dto.AddressDto;
import lk.swiftdrop.parcel.parcel.dto.CourierStatusUpdateRequest;
import lk.swiftdrop.parcel.parcel.dto.CreateCustomerParcelRequest;
import lk.swiftdrop.parcel.parcel.dto.CreateMerchantParcelRequest;
import lk.swiftdrop.parcel.parcel.dto.CustomerParcelDetailResponse;
import lk.swiftdrop.parcel.parcel.dto.InternalParcelBookingResponse;
import lk.swiftdrop.parcel.parcel.dto.InternalParcelDetailResponse;
import lk.swiftdrop.parcel.parcel.dto.MoneyDto;
import lk.swiftdrop.parcel.parcel.dto.RecipientDto;
import lk.swiftdrop.parcel.pricing.PricingClient;
import lk.swiftdrop.parcel.pricing.PricingSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParcelServiceTest {

    @Mock
    private ParcelRepository parcelRepository;

    @Mock
    private ParcelStatusHistoryRepository historyRepository;

    @Mock
    private TrackingNumberGenerator trackingNumberGenerator;

    @Mock
    private PricingClient pricingClient;

    @Mock
    private OutboxService outboxService;

    @Mock
    private IdempotencyService idempotencyService;

    private ParcelStateMachine stateMachine;
    private Clock clock;
    private ParcelService parcelService;

    private static final Instant NOW = Instant.parse("2026-11-15T07:20:00Z");

    @BeforeEach
    void setUp() {
        stateMachine = new ParcelStateMachine();
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        parcelService = new ParcelService(
                parcelRepository,
                historyRepository,
                stateMachine,
                trackingNumberGenerator,
                pricingClient,
                outboxService,
                idempotencyService,
                clock
        );
    }

    @Test
    @DisplayName("Customer parcel creation successfully accepts quote and creates parcel with outbox event")
    void testCreateCustomerParcelSuccess() {
        UUID quoteId = UUID.randomUUID();
        String customerId = "cust-user-1";
        String trackingNo = "SD260001XYZ";

        var recipient = new RecipientDto("Kasun Perera", "+94771234567", "kasun@example.com");
        var pickup = new AddressDto("10 Temple Rd", "Colombo", "00300");
        var dropoff = new AddressDto("25 Main St", "Kandy", "20000");
        var request = new CreateCustomerParcelRequest(
                recipient, pickup, dropoff, new BigDecimal("2.50"), ParcelSize.SMALL,
                "Books", DeliveryType.STANDARD, "Fragile", quoteId
        );

        PricingSnapshot pricing = PricingSnapshot.of(
                quoteId, new BigDecimal("300.00"), new BigDecimal("100.00"), new BigDecimal("50.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("450.00"), "LKR"
        );

        when(pricingClient.acceptQuote(quoteId)).thenReturn(pricing);
        when(trackingNumberGenerator.generate()).thenReturn(trackingNo);
        when(parcelRepository.existsByTrackingNumber(trackingNo)).thenReturn(false);

        CustomerParcelDetailResponse response = parcelService.createCustomerParcel(request, customerId);

        assertThat(response).isNotNull();
        assertThat(response.trackingNumber()).isEqualTo(trackingNo);
        assertThat(response.status()).isEqualTo(ParcelStatus.CREATED);
        assertThat(response.pricing().totalFee()).isEqualByComparingTo("450.00");

        verify(parcelRepository).save(any(Parcel.class));
        verify(historyRepository).save(any(ParcelStatusHistory.class));
        verify(outboxService).recordEvent(eq("parcel.created"), any(), any());
    }

    @Test
    @DisplayName("Merchant parcel creation replays cached response when idempotency key exists")
    void testCreateMerchantParcelIdempotentReplay() {
        String key = "merchant-order-uuid";
        UUID merchantId = UUID.randomUUID();

        var recipient = new RecipientDto("Amara Silva", "+94779876543", null);
        var pickup = new AddressDto("1 Galle Face", "Colombo", "00100");
        var dropoff = new AddressDto("40 High St", "Galle", "80000");
        var spec = new CreateMerchantParcelRequest.ParcelSpec(new BigDecimal("1.50"), ParcelSize.SMALL, "Shirt", null);
        var request = new CreateMerchantParcelRequest(merchantId, "EXT-99", recipient, pickup, dropoff, spec, null, null);

        var cachedResponse = new InternalParcelBookingResponse(
                UUID.randomUUID().toString(), "SD260002CACHED", "CREATED", new MoneyDto(new BigDecimal("350.00"), "LKR")
        );

        when(idempotencyService.startOrGetReplay(key, request, InternalParcelBookingResponse.class))
                .thenReturn(Optional.of(cachedResponse));

        InternalParcelBookingResponse result = parcelService.createMerchantParcel(request, key);

        assertThat(result).isEqualTo(cachedResponse);
        verify(parcelRepository, never()).save(any());
        verify(pricingClient, never()).acceptQuote(any());
    }

    @Test
    @DisplayName("Courier status update to DELIVERED records history and delivers complete pricing in outbox event")
    @SuppressWarnings("unchecked")
    void testCourierStatusUpdateToDelivered() {
        UUID parcelId = UUID.randomUUID();
        String courierId = "CR-101";

        PricingSnapshot pricing = PricingSnapshot.of(
                UUID.randomUUID(), new BigDecimal("250.00"), new BigDecimal("50.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("300.00"), "LKR"
        );

        Parcel parcel = Parcel.create(
                parcelId, "SD260003DELIV", "cust-1", null, "Recipient", "+94770000000", null,
                "Pickup", "Colombo", "00100", "Dropoff", "Colombo", "00200",
                new BigDecimal("1.00"), ParcelSize.SMALL, "Gift", DeliveryType.STANDARD, null, pricing, NOW
        );
        // Put in OUT_FOR_DELIVERY status with courier assigned
        parcel.updateStatus(ParcelStatus.OUT_FOR_DELIVERY, courierId, null, null, null, NOW);

        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
        when(historyRepository.findMaxSequenceNumber(parcelId)).thenReturn(Optional.of(2));

        var updateRequest = new CourierStatusUpdateRequest(
                courierId, ParcelStatus.DELIVERED, "Received by Customer",
                "proofs/photo123.jpg", "123456", null, "Handed over safely"
        );

        parcelService.updateCourierStatus(parcelId, updateRequest);

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(parcel.getDeliveredAt()).isEqualTo(NOW);
        assertThat(parcel.getProofRecipientName()).isEqualTo("Received by Customer");
        assertThat(parcel.getProofPhotoKey()).isEqualTo("proofs/photo123.jpg");

        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxService).recordEvent(eq("parcel.status.delivered"), eq(parcelId.toString()), dataCaptor.capture());

        Map<String, Object> eventData = dataCaptor.getValue();
        assertThat(eventData.get("toStatus")).isEqualTo("DELIVERED");
        assertThat(eventData.get("courierId")).isEqualTo(courierId);
        assertThat(eventData.get("customerId")).isEqualTo("cust-1");
        assertThat(eventData.get("pricing")).isNotNull();
        assertThat(eventData.get("currency")).isEqualTo("LKR");

        // Verify sensitive fields are NOT leaked in outbox event
        assertThat(eventData).doesNotContainKey("photoObjectKey");
        assertThat(eventData).doesNotContainKey("otp");
        assertThat(eventData).doesNotContainKey("recipientPhone");
    }

    @Test
    @DisplayName("Courier status update fails when courier does not match assigned courier")
    void testCourierMismatchThrowsConflict() {
        UUID parcelId = UUID.randomUUID();
        Parcel parcel = Parcel.create(
                parcelId, "SD260004MISMATCH", "cust-1", null, "Recipient", "+94770000000", null,
                "Pickup", "Colombo", "00100", "Dropoff", "Colombo", "00200",
                new BigDecimal("1.00"), ParcelSize.SMALL, "Gift", DeliveryType.STANDARD, null, null, NOW
        );
        parcel.updateStatus(ParcelStatus.ASSIGNED, "CR-ASSIGNED-1", null, null, null, NOW);

        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

        var updateRequest = new CourierStatusUpdateRequest(
                "CR-WRONG-COURIER", ParcelStatus.PICKED_UP, null, null, null, null, null
        );

        assertThatThrownBy(() -> parcelService.updateCourierStatus(parcelId, updateRequest))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.COURIER_MISMATCH);
    }

    @Test
    @DisplayName("Customer authorization check blocks non-admin from accessing other customer's parcel")
    void testCustomerAuthorizationCheck() {
        UUID parcelId = UUID.randomUUID();
        Parcel parcel = Parcel.create(
                parcelId, "SD260005AUTH", "owner-user", null, "Recipient", "+94770000000", null,
                "Pickup", "Colombo", "00100", "Dropoff", "Colombo", "00200",
                new BigDecimal("1.00"), ParcelSize.SMALL, "Gift", DeliveryType.STANDARD, null, null, NOW
        );

        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

        // Other customer accessing -> Forbidden
        assertThatThrownBy(() -> parcelService.getCustomerParcel(parcelId, "different-user", false))
                .isInstanceOf(ForbiddenException.class);

        // Owner customer accessing -> Success
        CustomerParcelDetailResponse ownerResult = parcelService.getCustomerParcel(parcelId, "owner-user", false);
        assertThat(ownerResult).isNotNull();

        // Admin accessing other's parcel -> Success
        CustomerParcelDetailResponse adminResult = parcelService.getCustomerParcel(parcelId, "admin-user", true);
        assertThat(adminResult).isNotNull();
    }

    @Test
    @DisplayName("Internal parcel details returns required format for Document Service fallback")
    void testGetInternalParcelDetails() {
        UUID parcelId = UUID.randomUUID();
        PricingSnapshot pricing = PricingSnapshot.of(
                UUID.randomUUID(), new BigDecimal("200.00"), new BigDecimal("50.00"), new BigDecimal("30.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("280.00"), "LKR"
        );

        Parcel parcel = Parcel.create(
                parcelId, "SD260006DOC", "cust-doc-user", null, "Recipient", "+94770000000", null,
                "Pickup", "Colombo", "00100", "Dropoff", "Colombo", "00200",
                new BigDecimal("1.00"), ParcelSize.SMALL, "Gift", DeliveryType.STANDARD, null, pricing, NOW
        );

        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

        InternalParcelDetailResponse response = parcelService.getInternalParcelDetails(parcelId);

        assertThat(response).isNotNull();
        assertThat(response.parcelId()).isEqualTo(parcelId);
        assertThat(response.trackingNo()).isEqualTo("SD260006DOC");
        assertThat(response.customerId()).isEqualTo("cust-doc-user");
        assertThat(response.currency()).isEqualTo("LKR");
        assertThat(response.pricing().baseAmount()).isEqualByComparingTo("200.00");
        assertThat(response.pricing().totalAmount()).isEqualByComparingTo("280.00");
    }
}
