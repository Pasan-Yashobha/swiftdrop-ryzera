package lk.swiftdrop.merchant.merchant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lk.swiftdrop.merchant.credential.AccessTokenService;
import lk.swiftdrop.merchant.credential.ApiKeyRepository;
import lk.swiftdrop.merchant.credential.OAuthClientRepository;
import lk.swiftdrop.merchant.credential.Scopes;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import lk.swiftdrop.merchant.testsupport.AbstractIntegrationTest;
import lk.swiftdrop.merchant.testsupport.CredentialFixtures;
import lk.swiftdrop.merchant.testsupport.CredentialFixtures.IssuedClient;
import lk.swiftdrop.merchant.testsupport.CredentialFixtures.IssuedKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Required test: unapproved merchants blocked from creating orders.
 *
 * <p>Covers the work-split requirement: "Tests: unapproved merchants blocked from creating orders."
 *
 * <p>Verifies that only an {@code APPROVED} merchant can reach the Merchant API order endpoints.
 * Merchants in the {@code PENDING}, {@code REJECTED}, and {@code SUSPENDED} states are always
 * rejected with {@code 403 MERCHANT_NOT_APPROVED} regardless of whether they present a valid API
 * key or a valid bearer token.
 *
 * <p>Also verifies that an approved merchant can create a sandbox order successfully, and that
 * reinstatement restores access after a suspension.
 */
class UnapprovedMerchantOrderTest extends AbstractIntegrationTest {

    private static final String PING = "/api/v1/merchant-api/ping";
    private static final String ORDERS = "/api/v1/merchant-api/orders";
    private static final String API_KEY_HEADER = "X-API-Key";

    @Autowired
    private CredentialFixtures credentials;

    @Autowired
    private ApiKeyRepository apiKeys;

    @Autowired
    private OAuthClientRepository oauthClients;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private AccessTokenService accessTokens;

    // -------------------------------------------------------------------------
    // PENDING merchant is blocked
    // -------------------------------------------------------------------------

    @Test
    void pendingMerchantIsBlockedFromPingWithApiKey() throws Exception {
        Merchant merchant = fixtures.pendingMerchant("user-pending-1");
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.TEST);

        mockMvc.perform(get(PING).header(API_KEY_HEADER, key.rawKey()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void pendingMerchantIsBlockedFromCreatingOrderWithApiKey() throws Exception {
        Merchant merchant = fixtures.pendingMerchant("user-pending-2");
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.TEST);

        mockMvc.perform(post(ORDERS)
                        .header(API_KEY_HEADER, key.rawKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validOrderRequest("PEND-001"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
    }

    @Test
    void pendingMerchantIsBlockedFromCreatingOrderWithBearerToken() throws Exception {
        Merchant merchant = fixtures.pendingMerchant("user-pending-3");
        IssuedClient client = credentials.oauthClient(merchant, ApiEnvironment.TEST, Scopes.ORDERS_WRITE);
        String token = accessTokens.issue(oauthClients.findById(client.id()).orElseThrow(), List.of(Scopes.ORDERS_WRITE)).token();

        mockMvc.perform(post(ORDERS)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validOrderRequest("PEND-TOKEN-001"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
    }

    // -------------------------------------------------------------------------
    // REJECTED merchant is blocked
    // -------------------------------------------------------------------------

    @Test
    void rejectedMerchantIsBlockedFromCreatingOrderWithApiKey() throws Exception {
        Merchant merchant = fixtures.merchant("user-rejected-1", MerchantStatus.REJECTED);
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.TEST);

        mockMvc.perform(post(ORDERS)
                        .header(API_KEY_HEADER, key.rawKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validOrderRequest("REJ-001"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
    }

    @Test
    void rejectedMerchantIsBlockedFromPing() throws Exception {
        Merchant merchant = fixtures.merchant("user-rejected-2", MerchantStatus.REJECTED);
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.LIVE);

        mockMvc.perform(get(PING).header(API_KEY_HEADER, key.rawKey()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
    }

    // -------------------------------------------------------------------------
    // SUSPENDED merchant is blocked
    // -------------------------------------------------------------------------

    @Test
    void suspendedMerchantIsBlockedFromCreatingOrderWithApiKey() throws Exception {
        Merchant merchant = fixtures.merchant("user-suspended-1", MerchantStatus.SUSPENDED);
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.LIVE);

        mockMvc.perform(post(ORDERS)
                        .header(API_KEY_HEADER, key.rawKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validOrderRequest("SUSP-001"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
    }

    @Test
    void suspendedMerchantIsBlockedFromCreatingOrderWithBearerToken() throws Exception {
        Merchant merchant = fixtures.merchant("user-suspended-2", MerchantStatus.SUSPENDED);
        IssuedClient client = credentials.oauthClient(merchant, ApiEnvironment.TEST, Scopes.ORDERS_WRITE);
        String token = accessTokens.issue(oauthClients.findById(client.id()).orElseThrow(), List.of(Scopes.ORDERS_WRITE)).token();

        mockMvc.perform(post(ORDERS)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validOrderRequest("SUSP-TOKEN-001"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
    }

    // -------------------------------------------------------------------------
    // APPROVED merchant can create orders
    // -------------------------------------------------------------------------

    @Test
    void approvedMerchantCanPingWithTestApiKey() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-approved-1");
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.TEST);

        mockMvc.perform(get(PING).header(API_KEY_HEADER, key.rawKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchant.getId().toString()))
                .andExpect(jsonPath("$.environment").value("TEST"));
    }

    @Test
    void approvedMerchantCanCreateSandboxOrderWithTestApiKey() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-approved-2");
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.TEST);

        mockMvc.perform(post(ORDERS)
                        .header(API_KEY_HEADER, key.rawKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validOrderRequest("APPROVED-001"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNo").value(org.hamcrest.Matchers.startsWith("SBX")))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.sandbox").value(true));
    }

    @Test
    void approvedMerchantCanCreateOrderWithBearerToken() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-approved-3");
        String token = credentials.bearerToken(merchant, ApiEnvironment.TEST, Scopes.ORDERS_WRITE);

        mockMvc.perform(post(ORDERS)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "token-order-approved-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validOrderRequest("APPR-TOKEN-003"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNo").value(org.hamcrest.Matchers.startsWith("SBX")));
    }

    // -------------------------------------------------------------------------
    // Suspension blocks, reinstatement restores
    // -------------------------------------------------------------------------

    @Test
    void suspendingApprovedMerchantBlocksAccessAndReinstatingRestoresIt() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-reinstate-1");
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.TEST);

        // Approved: access is granted
        mockMvc.perform(get(PING).header(API_KEY_HEADER, key.rawKey()))
                .andExpect(status().isOk());

        // Suspend
        merchantService.suspend(merchant.getId(), "fixture-admin", "Test suspension");

        // Suspended: access is blocked immediately
        mockMvc.perform(get(PING).header(API_KEY_HEADER, key.rawKey()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));

        // Reinstate
        merchantService.reinstate(merchant.getId(), "fixture-admin");

        // Reinstated: access is restored
        mockMvc.perform(get(PING).header(API_KEY_HEADER, key.rawKey()))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // All unapproved statuses are blocked (table-driven)
    // -------------------------------------------------------------------------

    @Test
    void allNonApprovedStatusesAreBlockedFromOrderCreation() throws Exception {
        for (MerchantStatus blocked : java.util.List.of(
                MerchantStatus.PENDING, MerchantStatus.REJECTED, MerchantStatus.SUSPENDED)) {

            Merchant merchant = fixtures.merchant("user-blocked-" + blocked, blocked);
            IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.TEST);

            mockMvc.perform(post(ORDERS)
                            .header(API_KEY_HEADER, key.rawKey())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validOrderRequest("BLK-" + blocked))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static Map<String, Object> validOrderRequest(String externalRef) {
        Map<String, Object> recipient = new LinkedHashMap<>();
        recipient.put("name", "Test Recipient");
        recipient.put("phone", "+94771234567");

        Map<String, Object> pickup = new LinkedHashMap<>();
        pickup.put("addressLine", "12 Galle Road");
        pickup.put("city", "Colombo");
        pickup.put("postalCode", "00300");

        Map<String, Object> dropoff = new LinkedHashMap<>();
        dropoff.put("addressLine", "45 Temple Street");
        dropoff.put("city", "Kandy");
        dropoff.put("postalCode", "20000");

        Map<String, Object> parcel = new LinkedHashMap<>();
        parcel.put("weightKg", 1.5);
        parcel.put("size", "SMALL");
        parcel.put("description", "Test parcel");

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("externalRef", externalRef);
        request.put("recipient", recipient);
        request.put("pickup", pickup);
        request.put("dropoff", dropoff);
        request.put("parcel", parcel);
        return request;
    }
}
