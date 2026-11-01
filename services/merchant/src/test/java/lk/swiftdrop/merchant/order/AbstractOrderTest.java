package lk.swiftdrop.merchant.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.order.dto.Money;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import lk.swiftdrop.merchant.testsupport.AbstractIntegrationTest;
import lk.swiftdrop.merchant.testsupport.CredentialFixtures;
import lk.swiftdrop.merchant.testsupport.CredentialFixtures.IssuedKey;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Integration test of the order module: real H2 database and Flyway migration, real security chains, a mocked
 * parcel service. The mock answers every booking with a new parcel unless a test stubs it differently.
 */
abstract class AbstractOrderTest extends AbstractIntegrationTest {

    protected static final String API_ORDERS = "/api/v1/merchant-api/orders";
    protected static final String SANDBOX_ORDERS = "/api/v1/merchant-api/sandbox/orders";
    protected static final String PORTAL_ORDERS = "/api/v1/merchants/me/orders";
    protected static final String OWNER = "owner-1";

    @MockitoBean
    protected ParcelClient parcelClient;

    @Autowired
    protected CredentialFixtures credentials;

    @Autowired
    protected OrderFixtures orderFixtures;

    @Autowired
    protected MerchantOrderRepository orders;

    @Autowired
    protected OrderStatusHistoryRepository history;

    private final AtomicInteger bookings = new AtomicInteger(24_000_000);

    protected Merchant merchant;

    @BeforeEach
    void approvedMerchantAndWorkingParcelService() {
        merchant = fixtures.approvedMerchant(OWNER);
        bookParcelsSuccessfully();
    }

    /** Every booking gets its own parcel id and tracking number. */
    protected void bookParcelsSuccessfully() {
        doAnswer(invocation -> {
            int n = bookings.incrementAndGet();
            return new ParcelBooking("parcel-" + n, "SD" + n, new Money(new BigDecimal("450.00"), "LKR"));
        }).when(parcelClient).createParcel(any());
    }

    protected IssuedKey liveKey() {
        return credentials.apiKey(merchant, ApiEnvironment.LIVE);
    }

    protected IssuedKey testKey() {
        return credentials.apiKey(merchant, ApiEnvironment.TEST);
    }

    protected MockHttpServletRequestBuilder apiPost(String path, IssuedKey key, Object body) throws Exception {
        return post(path).header("X-API-Key", key.rawKey()).contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
    }

    protected ResultActions createViaApi(IssuedKey key, Map<String, Object> payload, String idempotencyKey)
            throws Exception {
        MockHttpServletRequestBuilder request = apiPost(API_ORDERS, key, payload);
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    protected ResultActions createViaPortal(String owner, Map<String, Object> payload, boolean sandbox,
                                            String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder request = post(PORTAL_ORDERS).with(TestAuth.merchant(owner))
                .param("sandbox", Boolean.toString(sandbox))
                .contentType(MediaType.APPLICATION_JSON).content(json(payload));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
