package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import lk.swiftdrop.merchant.credential.Scopes;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.merchant.MerchantService;
import lk.swiftdrop.merchant.merchant.MerchantStatus;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import lk.swiftdrop.merchant.testsupport.CredentialFixtures.IssuedKey;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/** Who may create and read which orders: approval, ownership, environment, scopes and roles. */
class OrderAuthorizationTest extends AbstractOrderTest {

    private static final String OTHER_OWNER = "owner-2";

    @Autowired
    private MerchantService merchantService;

    private void assertNothingWasBooked() {
        assertThat(orders.count()).isZero();
        verifyNoInteractions(parcelClient);
    }

    // ---- merchants that are not approved ----

    @ParameterizedTest
    @EnumSource(value = MerchantStatus.class, names = {"PENDING", "REJECTED", "SUSPENDED"})
    void merchantThatIsNotApprovedCannotCreateOrdersWithAnApiKey(MerchantStatus status) throws Exception {
        Merchant unapproved = fixtures.merchant("unapproved", status);
        IssuedKey key = credentials.apiKey(unapproved, ApiEnvironment.LIVE);

        createViaApi(key, OrderFixtures.payload("ORDER-1"), "key-1")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));

        assertNothingWasBooked();
    }

    @ParameterizedTest
    @EnumSource(value = MerchantStatus.class, names = {"PENDING", "REJECTED", "SUSPENDED"})
    void merchantThatIsNotApprovedCannotCreateSandboxOrdersEither(MerchantStatus status) throws Exception {
        Merchant unapproved = fixtures.merchant("unapproved", status);
        IssuedKey key = credentials.apiKey(unapproved, ApiEnvironment.TEST);

        createViaApi(key, OrderFixtures.payload("ORDER-1"), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));

        assertNothingWasBooked();
    }

    @ParameterizedTest
    @EnumSource(value = MerchantStatus.class, names = {"PENDING", "REJECTED", "SUSPENDED"})
    void merchantThatIsNotApprovedCannotCreateOrdersInThePortal(MerchantStatus status) throws Exception {
        fixtures.merchant("unapproved", status);

        createViaPortal("unapproved", OrderFixtures.payload("ORDER-1"), false, "key-1")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
        createViaPortal("unapproved", OrderFixtures.payload("ORDER-2"), true, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));

        assertNothingWasBooked();
    }

    @ParameterizedTest
    @EnumSource(value = MerchantStatus.class, names = {"PENDING", "REJECTED", "SUSPENDED"})
    void merchantThatIsNotApprovedCannotUploadBulkOrders(MerchantStatus status) throws Exception {
        fixtures.merchant("unapproved", status);
        byte[] csv = (String.join(",", BulkCsvImporter.HEADER) + "\n").getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart(PORTAL_ORDERS + "/bulk").file(new MockMultipartFile("file", "o.csv", "text/csv", csv))
                        .with(TestAuth.merchant("unapproved")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));

        assertNothingWasBooked();
    }

    @Test
    void merchantThatIsNotApprovedCannotCreateOrdersWithABearerToken() throws Exception {
        String token = credentials.bearerToken(merchant, ApiEnvironment.LIVE);
        merchantService.suspend(merchant.getId(), "admin", "Under review");

        mockMvc.perform(post(API_ORDERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(OrderFixtures.payload("ORDER-1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));

        assertNothingWasBooked();
    }

    @Test
    void suspensionAfterTheKeyWasCreatedBlocksTheKeyImmediately() throws Exception {
        IssuedKey key = liveKey();
        createViaApi(key, OrderFixtures.payload("ORDER-1"), "key-1").andExpect(status().isCreated());

        merchantService.suspend(merchant.getId(), "admin", "Fraud suspected");
        createViaApi(key, OrderFixtures.payload("ORDER-2"), "key-2")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));

        assertThat(orders.count()).isEqualTo(1);

        merchantService.reinstate(merchant.getId(), "admin");
        createViaApi(key, OrderFixtures.payload("ORDER-2"), "key-2").andExpect(status().isCreated());
    }

    @Test
    void suspendedMerchantCanStillSeeItsHistoryInThePortal() throws Exception {
        MerchantOrder order = orderFixtures.order(merchant).externalRef("ORDER-1").save();
        merchantService.suspend(merchant.getId(), "admin", "Review");

        mockMvc.perform(get(PORTAL_ORDERS).with(TestAuth.merchant(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(PORTAL_ORDERS + "/" + order.getId()).with(TestAuth.merchant(OWNER)))
                .andExpect(status().isOk());
        mockMvc.perform(get(PORTAL_ORDERS + "/stats").with(TestAuth.merchant(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    // ---- other merchants' orders ----

    @Test
    void merchantCannotReadOrListOrAdvanceAnotherMerchantsOrders() throws Exception {
        Merchant other = fixtures.approvedMerchant(OTHER_OWNER);
        MerchantOrder liveOrder = orderFixtures.order(other).trackingNo("SD-OTHER").externalRef("THEIRS").save();
        MerchantOrder sandboxOrder = orderFixtures.order(other).sandbox().trackingNo("SBX999999999").save();
        orderFixtures.order(merchant).trackingNo("SD-MINE").save();

        // portal
        mockMvc.perform(get(PORTAL_ORDERS + "/" + liveOrder.getId()).with(TestAuth.merchant(OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get(PORTAL_ORDERS).param("sandbox", "all").with(TestAuth.merchant(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].trackingNo").value("SD-MINE"));
        mockMvc.perform(get(PORTAL_ORDERS).param("search", "THEIRS").with(TestAuth.merchant(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // merchant API
        IssuedKey live = liveKey();
        IssuedKey test = testKey();
        mockMvc.perform(get(API_ORDERS + "/SD-OTHER").header("X-API-Key", live.rawKey()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get(API_ORDERS).param("externalRef", "THEIRS").header("X-API-Key", live.rawKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(API_ORDERS + "/SBX999999999").header("X-API-Key", test.rawKey()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(SANDBOX_ORDERS + "/SBX999999999/advance")
                        .header("X-API-Key", test.rawKey()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(orders.findById(sandboxOrder.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(history.count()).isZero();
    }

    @Test
    void testCredentialsOnlySeeSandboxOrdersAndLiveCredentialsOnlyLiveOnes() throws Exception {
        orderFixtures.order(merchant).trackingNo("SD-LIVE").save();
        orderFixtures.order(merchant).sandbox().trackingNo("SBX111111111").save();
        IssuedKey live = liveKey();
        IssuedKey test = testKey();

        mockMvc.perform(get(API_ORDERS + "/SD-LIVE").header("X-API-Key", live.rawKey())).andExpect(status().isOk());
        mockMvc.perform(get(API_ORDERS + "/SD-LIVE").header("X-API-Key", test.rawKey()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(API_ORDERS + "/SBX111111111").header("X-API-Key", test.rawKey()))
                .andExpect(status().isOk());
        mockMvc.perform(get(API_ORDERS + "/SBX111111111").header("X-API-Key", live.rawKey()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(API_ORDERS).header("X-API-Key", live.rawKey()))
                .andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD-LIVE")));
        mockMvc.perform(get(API_ORDERS).header("X-API-Key", test.rawKey()))
                .andExpect(jsonPath("$.content[*].trackingNo").value(contains("SBX111111111")));
    }

    // ---- scopes ----

    @Test
    void readOnlyTokenCannotCreateOrders() throws Exception {
        String readOnly = credentials.bearerToken(merchant, ApiEnvironment.LIVE, Scopes.ORDERS_READ);

        mockMvc.perform(post(API_ORDERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + readOnly)
                        .contentType(MediaType.APPLICATION_JSON).content(json(OrderFixtures.payload("ORDER-1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post(SANDBOX_ORDERS + "/SBX1/advance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readOnly))
                .andExpect(status().isForbidden());

        assertNothingWasBooked();
    }

    @Test
    void writeOnlyTokenCannotReadOrders() throws Exception {
        orderFixtures.order(merchant).trackingNo("SD-MINE").save();
        String writeOnly = credentials.bearerToken(merchant, ApiEnvironment.LIVE, Scopes.ORDERS_WRITE);

        mockMvc.perform(get(API_ORDERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + writeOnly))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get(API_ORDERS + "/SD-MINE").header(HttpHeaders.AUTHORIZATION, "Bearer " + writeOnly))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(API_ORDERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + writeOnly)
                        .contentType(MediaType.APPLICATION_JSON).content(json(OrderFixtures.payload("ORDER-1"))))
                .andExpect(status().isCreated());
    }

    @Test
    void fullScopeTokenCanCreateAndRead() throws Exception {
        String token = credentials.bearerToken(merchant, ApiEnvironment.LIVE);

        mockMvc.perform(post(API_ORDERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(OrderFixtures.payload("ORDER-1"))))
                .andExpect(status().isCreated());
        mockMvc.perform(get(API_ORDERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void liveCredentialCannotAdvanceOrders() throws Exception {
        String trackingNo = body(createViaApi(testKey(), OrderFixtures.payload("ORDER-1"), null)
                .andExpect(status().isCreated())).get("trackingNo").asText();

        mockMvc.perform(post(SANDBOX_ORDERS + "/" + trackingNo + "/advance")
                        .header("X-API-Key", liveKey().rawKey()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SANDBOX_ONLY"));

        assertThat(orders.findAll().get(0).getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(history.count()).isZero();
    }

    // ---- who is calling ----

    @Test
    void portalUserWithoutAMerchantIsToldToRegister() throws Exception {
        String someId = "7d1f5b0e-3b1c-4f43-9b6e-0f0c3c1a9a11";
        byte[] csv = "x".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(get(PORTAL_ORDERS).with(TestAuth.merchant("nobody")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MERCHANT_NOT_REGISTERED"));
        mockMvc.perform(get(PORTAL_ORDERS + "/stats").with(TestAuth.merchant("nobody")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MERCHANT_NOT_REGISTERED"));
        mockMvc.perform(get(PORTAL_ORDERS + "/" + someId).with(TestAuth.merchant("nobody")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MERCHANT_NOT_REGISTERED"));
        createViaPortal("nobody", OrderFixtures.payload("ORDER-1"), false, null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MERCHANT_NOT_REGISTERED"));
        mockMvc.perform(multipart(PORTAL_ORDERS + "/bulk").file(new MockMultipartFile("file", "o.csv", "text/csv", csv))
                        .with(TestAuth.merchant("nobody")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MERCHANT_NOT_REGISTERED"));

        assertNothingWasBooked();
    }

    @Test
    void onlyMerchantUsersMayUseThePortalOrderEndpoints() throws Exception {
        Map<String, Object> payload = OrderFixtures.payload("ORDER-1");

        mockMvc.perform(get(PORTAL_ORDERS).with(TestAuth.admin("admin-1"))).andExpect(status().isForbidden());
        mockMvc.perform(get(PORTAL_ORDERS).with(TestAuth.customer("customer-1"))).andExpect(status().isForbidden());
        mockMvc.perform(post(PORTAL_ORDERS).with(TestAuth.customer("customer-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PORTAL_ORDERS)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(PORTAL_ORDERS).contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isUnauthorized());

        assertNothingWasBooked();
    }

    @Test
    void merchantApiNeedsACredentialAndPortalLoginsAreNotOne() throws Exception {
        Map<String, Object> payload = OrderFixtures.payload("ORDER-1");

        mockMvc.perform(post(API_ORDERS).contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post(API_ORDERS).header("X-API-Key", "sd_live_zzzzzzzz_" + "Q".repeat(43))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_API_KEY"));
        mockMvc.perform(post(API_ORDERS).header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJIUzI1NiJ9.e30.c2ln")
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mockMvc.perform(post(PORTAL_ORDERS).header("X-API-Key", liveKey().rawKey())
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isUnauthorized());

        assertNothingWasBooked();
    }
}
