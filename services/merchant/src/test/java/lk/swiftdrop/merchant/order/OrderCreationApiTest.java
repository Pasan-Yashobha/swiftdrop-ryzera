package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.ServiceUnavailableException;
import lk.swiftdrop.merchant.common.UnprocessableException;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.order.dto.Money;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import lk.swiftdrop.merchant.testsupport.CredentialFixtures.IssuedKey;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Order creation through the merchant API with live and sandbox credentials. */
class OrderCreationApiTest extends AbstractOrderTest {

    @Test
    void liveKeyBooksAParcelAndStoresTheOrder() throws Exception {
        IssuedKey key = liveKey();

        createViaApi(key, OrderFixtures.payload("ORDER-1001"), "key-1")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/merchant-api/orders/SD24000001"))
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.trackingNo").value("SD24000001"))
                .andExpect(jsonPath("$.trackingUrl").value("http://localhost:4200/track/SD24000001"))
                .andExpect(jsonPath("$.externalRef").value("ORDER-1001"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.sandbox").value(false))
                .andExpect(jsonPath("$.source").value("API"))
                .andExpect(jsonPath("$.recipient.name").value("Nimali Perera"))
                .andExpect(jsonPath("$.recipient.phone").value("+94771234567"))
                .andExpect(jsonPath("$.pickup.addressLine").value("12 Galle Rd"))
                .andExpect(jsonPath("$.dropoff.city").value("Kandy"))
                .andExpect(jsonPath("$.dropoff.postalCode").value("20000"))
                .andExpect(jsonPath("$.parcel.weightKg").value(2.5))
                .andExpect(jsonPath("$.parcel.size").value("SMALL"))
                .andExpect(jsonPath("$.notes").value("Call on arrival"))
                .andExpect(jsonPath("$.fee.amount").value(450.00))
                .andExpect(jsonPath("$.fee.currency").value("LKR"))
                .andExpect(jsonPath("$.failureReason").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString());

        MerchantOrder stored = orders.findAll().get(0);
        assertThat(stored.getMerchantId()).isEqualTo(merchant.getId());
        assertThat(stored.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(stored.getParcelId()).isEqualTo("parcel-24000001");
        assertThat(stored.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(stored.getRequestHash()).hasSize(64);
        assertThat(stored.isSandbox()).isFalse();
        assertThat(stored.getSource()).isEqualTo(OrderSource.API);
    }

    @Test
    void parcelServiceReceivesTheMerchantTheOrderAndTheOrderIdAsIdempotencyKey() throws Exception {
        createViaApi(liveKey(), OrderFixtures.payload("ORDER-1001"), "key-1").andExpect(status().isCreated());

        ArgumentCaptor<ParcelBookingRequest> captor = ArgumentCaptor.forClass(ParcelBookingRequest.class);
        verify(parcelClient).createParcel(captor.capture());
        ParcelBookingRequest booking = captor.getValue();
        MerchantOrder stored = orders.findAll().get(0);
        assertThat(booking.orderId()).isEqualTo(stored.getId());
        assertThat(booking.merchantId()).isEqualTo(merchant.getId());
        assertThat(booking.order()).isEqualTo(OrderFixtures.request("ORDER-1001"));
    }

    @Test
    void orderWithoutIdempotencyKeyOrExternalRefIsAccepted() throws Exception {
        createViaApi(liveKey(), OrderFixtures.payload(null), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalRef").doesNotExist());

        assertThat(orders.findAll().get(0).getIdempotencyKey()).isNull();
    }

    @Test
    void testKeyCreatesASandboxOrderWithoutCallingTheParcelService() throws Exception {
        createViaApi(testKey(), OrderFixtures.payload("ORDER-1001"), "key-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNo").value(matchesPattern("SBX\\d{9}")))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.sandbox").value(true))
                .andExpect(jsonPath("$.fee.amount").value(600.00))
                .andExpect(jsonPath("$.fee.currency").value("LKR"));

        verifyNoInteractions(parcelClient);
        MerchantOrder stored = orders.findAll().get(0);
        assertThat(stored.isSandbox()).isTrue();
        assertThat(stored.getParcelId()).isNull();
    }

    @Test
    void replayWithTheSameKeyAndPayloadReturnsTheStoredOrder() throws Exception {
        IssuedKey key = liveKey();
        String first = createViaApi(key, OrderFixtures.payload("ORDER-1001"), "key-1")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        createViaApi(key, OrderFixtures.payload("ORDER-1001"), "key-1")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().json(first));

        verify(parcelClient, times(1)).createParcel(any());
        assertThat(orders.count()).isEqualTo(1);
    }

    @Test
    void replayIgnoresSurroundingBlanksAndWeightNotation() throws Exception {
        IssuedKey key = liveKey();
        createViaApi(key, OrderFixtures.payload("ORDER-1001"), "key-1").andExpect(status().isCreated());

        Map<String, Object> sameOrder = OrderFixtures.payload("  ORDER-1001 ");
        OrderFixtures.section(sameOrder, "recipient").put("name", "  Nimali Perera  ");
        OrderFixtures.section(sameOrder, "parcel").put("weightKg", new BigDecimal("2.500"));
        createViaApi(key, sameOrder, "key-1").andExpect(status().isOk());

        verify(parcelClient, times(1)).createParcel(any());
    }

    @Test
    void reusingTheKeyForADifferentPayloadIsUnprocessable() throws Exception {
        IssuedKey key = liveKey();
        createViaApi(key, OrderFixtures.payload("ORDER-1001"), "key-1").andExpect(status().isCreated());

        Map<String, Object> other = OrderFixtures.payload("ORDER-1001");
        OrderFixtures.section(other, "dropoff").put("city", "Galle");
        createViaApi(key, other, "key-1")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(orders.count()).isEqualTo(1);
    }

    @Test
    void theSamePayloadAsSandboxAndLiveIsADifferentRequest() throws Exception {
        createViaApi(liveKey(), OrderFixtures.payload("ORDER-1001"), "key-1").andExpect(status().isCreated());

        createViaApi(testKey(), OrderFixtures.payload("ORDER-1001"), "key-1")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void keyOfAnOrderThatIsStillBeingCreatedIsInUse() throws Exception {
        IssuedKey key = liveKey();
        String hash = RequestFingerprint.of(OrderFixtures.request("ORDER-1001"), false);
        orderFixtures.order(merchant).idempotencyKey("key-1", hash).externalRef("ORDER-1001")
                .createdAt(Instant.now()).savePlaceholder();

        createViaApi(key, OrderFixtures.payload("ORDER-1001"), "key-1")
                .andExpect(status().isConflict())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_IN_USE"))
                .andExpect(jsonPath("$.path").value(API_ORDERS))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        verifyNoInteractions(parcelClient);
    }

    @Test
    void sameKeyWithDifferentPayloadWhileInFlightIsReusedNotInUse() throws Exception {
        String hash = RequestFingerprint.of(OrderFixtures.request("ORDER-1001"), false);
        orderFixtures.order(merchant).idempotencyKey("key-1", hash).externalRef("ORDER-1001").savePlaceholder();

        Map<String, Object> other = OrderFixtures.payload("ORDER-1001");
        OrderFixtures.section(other, "parcel").put("size", "LARGE");
        createViaApi(liveKey(), other, "key-1")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void externalRefOfAnotherOrderIsADuplicate() throws Exception {
        IssuedKey key = liveKey();
        createViaApi(key, OrderFixtures.payload("ORDER-1001"), "key-1").andExpect(status().isCreated());

        createViaApi(key, OrderFixtures.payload("ORDER-1001"), "key-2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EXTERNAL_REF"));
        createViaApi(key, OrderFixtures.payload("ORDER-1001"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EXTERNAL_REF"));

        verify(parcelClient, times(1)).createParcel(any());
        assertThat(orders.count()).isEqualTo(1);
    }

    @Test
    void sandboxTestsDoNotUseUpTheExternalRefOfTheLiveOrder() throws Exception {
        createViaApi(testKey(), OrderFixtures.payload("ORDER-1001"), null).andExpect(status().isCreated());

        createViaApi(liveKey(), OrderFixtures.payload("ORDER-1001"), null).andExpect(status().isCreated());

        assertThat(orders.count()).isEqualTo(2);
        createViaApi(liveKey(), OrderFixtures.payload("ORDER-1001"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EXTERNAL_REF"));
    }

    @Test
    void retryWithoutAKeyCompletesThePlaceholderOfTheSameRequest() throws Exception {
        String hash = RequestFingerprint.of(OrderFixtures.request("ORDER-1001"), false);
        MerchantOrder placeholder = orderFixtures.order(merchant).idempotencyKey(null, hash)
                .externalRef("ORDER-1001").savePlaceholder();

        createViaApi(liveKey(), OrderFixtures.payload("ORDER-1001"), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(placeholder.getId().toString()));

        assertThat(orders.count()).isEqualTo(1);
    }

    @Test
    void sameExternalRefOfAnotherMerchantIsFine() throws Exception {
        createViaApi(liveKey(), OrderFixtures.payload("ORDER-1001"), null).andExpect(status().isCreated());
        Merchant other = fixtures.approvedMerchant("owner-2");
        IssuedKey otherKey = credentials.apiKey(other, ApiEnvironment.LIVE);

        createViaApi(otherKey, OrderFixtures.payload("ORDER-1001"), null).andExpect(status().isCreated());

        assertThat(orders.count()).isEqualTo(2);
    }

    @Test
    void rejectedParcelRemovesThePlaceholderSoTheSameKeyCanBeRetried() throws Exception {
        IssuedKey key = liveKey();
        when(parcelClient.createParcel(any()))
                .thenThrow(new UnprocessableException(ErrorCode.PARCEL_REJECTED, "Pickup address is outside our area"));

        createViaApi(key, OrderFixtures.payload("ORDER-1001"), "key-1")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PARCEL_REJECTED"))
                .andExpect(jsonPath("$.message").value("Pickup address is outside our area"));
        assertThat(orders.count()).isZero();

        bookParcelsSuccessfully();
        createViaApi(key, OrderFixtures.payload("ORDER-1001"), "key-1").andExpect(status().isCreated());
        assertThat(orders.count()).isEqualTo(1);
    }

    @Test
    void placeholderOfAnEarlierRequestIsCompletedByTheRetryWithTheSameOrderId() throws Exception {
        IssuedKey key = liveKey();
        String hash = RequestFingerprint.of(OrderFixtures.request("ORDER-1001"), false);
        MerchantOrder placeholder = orderFixtures.order(merchant).idempotencyKey("key-1", hash)
                .externalRef("ORDER-1001").savePlaceholder();

        createViaApi(key, OrderFixtures.payload("ORDER-1001"), "key-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(placeholder.getId().toString()));

        ArgumentCaptor<ParcelBookingRequest> captor = ArgumentCaptor.forClass(ParcelBookingRequest.class);
        verify(parcelClient).createParcel(captor.capture());
        // The parcel service de-duplicates on this id, so a booking made by the lost first attempt is not repeated.
        assertThat(captor.getValue().orderId()).isEqualTo(placeholder.getId());
        assertThat(orders.count()).isEqualTo(1);
        assertThat(orders.findAll().get(0).getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void unavailableParcelServiceKeepsThePlaceholderBecauseTheParcelMayBeBooked() throws Exception {
        IssuedKey key = liveKey();
        when(parcelClient.createParcel(any())).thenThrow(
                new ServiceUnavailableException(ErrorCode.PARCEL_SERVICE_UNAVAILABLE, "The parcel service is unavailable"));

        createViaApi(key, OrderFixtures.payload("ORDER-1001"), "key-1")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PARCEL_SERVICE_UNAVAILABLE"));
        assertThat(orders.findAll()).extracting(MerchantOrder::getStatus).containsExactly(OrderStatus.PENDING);

        // Straight away the first request is still taken to be running; the retry completes it once it is old enough.
        bookParcelsSuccessfully();
        createViaApi(key, OrderFixtures.payload("ORDER-1001"), "key-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_IN_USE"));
    }

    @Test
    void unexpectedParcelClientFailureAlsoKeepsThePlaceholderAndIsReportedAsInternalError() throws Exception {
        when(parcelClient.createParcel(any())).thenThrow(new IllegalStateException("boom"));

        createViaApi(liveKey(), OrderFixtures.payload("ORDER-1001"), "key-1")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(not(containsString("boom"))));
        assertThat(orders.count()).isEqualTo(1);
    }

    @Test
    void invalidPayloadCreatesNothing() throws Exception {
        Map<String, Object> invalid = OrderFixtures.payload("ORDER-1001");
        OrderFixtures.section(invalid, "dropoff").put("city", " ");

        createViaApi(liveKey(), invalid, "key-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("dropoff.city"));

        verify(parcelClient, never()).createParcel(any());
        assertThat(orders.count()).isZero();
    }

    @Test
    void tooLongIdempotencyKeyIsRejected() throws Exception {
        createViaApi(liveKey(), OrderFixtures.payload("ORDER-1001"), "k".repeat(101))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("Idempotency-Key"));
    }

    @Test
    void blankIdempotencyKeyCountsAsNoKey() throws Exception {
        createViaApi(liveKey(), OrderFixtures.payload("ORDER-1001"), "   ").andExpect(status().isCreated());

        assertThat(orders.findAll().get(0).getIdempotencyKey()).isNull();
    }

    @Test
    void returnedFeeComesFromTheParcelService() throws Exception {
        when(parcelClient.createParcel(any())).thenReturn(
                new ParcelBooking("p-9", "SD99", new Money(new BigDecimal("1234.50"), "LKR")));

        createViaApi(liveKey(), OrderFixtures.payload("ORDER-1001"), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fee.amount").value(1234.50));

        assertThat(List.of(orders.findAll().get(0).getFeeAmount())).containsExactly(new BigDecimal("1234.50"));
    }
}
