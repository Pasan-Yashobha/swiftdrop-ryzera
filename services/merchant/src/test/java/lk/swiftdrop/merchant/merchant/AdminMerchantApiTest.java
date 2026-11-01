package lk.swiftdrop.merchant.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.swiftdrop.merchant.event.OutboxEvent;
import lk.swiftdrop.merchant.event.OutboxRepository;
import lk.swiftdrop.merchant.testsupport.AbstractIntegrationTest;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class AdminMerchantApiTest extends AbstractIntegrationTest {

    private static final String ADMIN = "admin-1";

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private MerchantRepository merchants;

    // ---- access control ----

    @Test
    void adminEndpointsRequireAnAdminToken() throws Exception {
        Merchant merchant = fixtures.pendingMerchant("user-1");
        String approve = "/api/v1/admin/merchants/" + merchant.getId() + "/approve";

        mockMvc.perform(get("/api/v1/admin/merchants")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(approve)).andExpect(status().isUnauthorized());
        for (RequestPostProcessor auth : List.of(TestAuth.merchant("user-1"), TestAuth.customer("user-2"))) {
            mockMvc.perform(get("/api/v1/admin/merchants").with(auth))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mockMvc.perform(post(approve).with(auth)).andExpect(status().isForbidden());
        }

        assertThat(merchants.findById(merchant.getId()).orElseThrow().getStatus()).isEqualTo(MerchantStatus.PENDING);
        assertThat(outbox.count()).isZero();
    }

    // ---- listing ----

    @Test
    void listIsSortedByCreationDateDescendingAndPaged() throws Exception {
        Merchant first = fixtures.pendingMerchant("user-1");
        Thread.sleep(5);
        Merchant second = fixtures.approvedMerchant("user-2");
        Thread.sleep(5);
        Merchant third = fixtures.pendingMerchant("user-3");

        mockMvc.perform(get("/api/v1/admin/merchants").with(TestAuth.admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[*].id").value(contains(
                        third.getId().toString(), second.getId().toString(), first.getId().toString())))
                .andExpect(jsonPath("$.content[0].ownerUserId").value("user-3"));

        mockMvc.perform(get("/api/v1/admin/merchants?page=1&size=2").with(TestAuth.admin(ADMIN)))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(first.getId().toString()));
    }

    @Test
    void listFiltersByStatus() throws Exception {
        fixtures.pendingMerchant("user-1");
        Merchant approved = fixtures.approvedMerchant("user-2");
        fixtures.merchant("user-3", MerchantStatus.SUSPENDED);

        mockMvc.perform(get("/api/v1/admin/merchants?status=APPROVED").with(TestAuth.admin(ADMIN)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(approved.getId().toString()));
        mockMvc.perform(get("/api/v1/admin/merchants?status=SUSPENDED").with(TestAuth.admin(ADMIN)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listSearchMatchesNameOrEmailIgnoringCase() throws Exception {
        merchants.save(Merchant.register(UUID.randomUUID(), "user-1", new MerchantProfile("Lanka Crafts", null,
                "sales@crafts.lk", "+94771234567", "1 Road", "Colombo", null), Instant.now()));
        merchants.save(Merchant.register(UUID.randomUUID(), "user-2", new MerchantProfile("Tea Traders", null,
                "info@LANKA-tea.lk", "+94771234567", "2 Road", "Kandy", null), Instant.now()));
        merchants.save(Merchant.register(UUID.randomUUID(), "user-3", new MerchantProfile("Gem House", null,
                "gems@gem.lk", "+94771234567", "3 Road", "Galle", null), Instant.now()));

        mockMvc.perform(get("/api/v1/admin/merchants?search=lanka").with(TestAuth.admin(ADMIN)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/admin/merchants?search=GEM HOUSE").with(TestAuth.admin(ADMIN)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].businessName").value("Gem House"));
        mockMvc.perform(get("/api/v1/admin/merchants?search=lanka&status=PENDING&size=1").with(TestAuth.admin(ADMIN)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void listSearchTreatsWildcardsAsPlainText() throws Exception {
        fixtures.pendingMerchant("user-1");

        mockMvc.perform(get("/api/v1/admin/merchants?search=%25").with(TestAuth.admin(ADMIN)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/admin/merchants?search=_").with(TestAuth.admin(ADMIN)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listRejectsInvalidParameters() throws Exception {
        mockMvc.perform(get("/api/v1/admin/merchants?size=0").with(TestAuth.admin(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("size"));
        mockMvc.perform(get("/api/v1/admin/merchants?size=101").with(TestAuth.admin(ADMIN)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/merchants?page=-1").with(TestAuth.admin(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("page"));
        mockMvc.perform(get("/api/v1/admin/merchants?status=BOGUS").with(TestAuth.admin(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("status"));
    }

    @Test
    void getReturnsAdminViewAndNotFoundForUnknownId() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");

        mockMvc.perform(get("/api/v1/admin/merchants/" + merchant.getId()).with(TestAuth.admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(merchant.getId().toString()))
                .andExpect(jsonPath("$.ownerUserId").value("user-1"))
                .andExpect(jsonPath("$.decidedBy").value("fixture-admin"))
                .andExpect(jsonPath("$.contactEmail").value("user-1@shop.example"));
        mockMvc.perform(get("/api/v1/admin/merchants/" + UUID.randomUUID()).with(TestAuth.admin(ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/admin/merchants/not-a-uuid").with(TestAuth.admin(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ---- approve / reject ----

    @Test
    void approveActivatesPendingMerchantAndPublishesEvent() throws Exception {
        Merchant merchant = fixtures.pendingMerchant("user-1");

        postDecision(merchant, "approve", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value(ADMIN))
                .andExpect(jsonPath("$.decidedAt").isString())
                .andExpect(jsonPath("$.decisionReason").value(nullValue()));

        OutboxEvent event = onlyEvent();
        assertThat(event.getEventType()).isEqualTo("merchant.approved");
        assertThat(event.getAggregateId()).isEqualTo(merchant.getId().toString());
        JsonNode data = objectMapper.readTree(event.getPayload()).get("data");
        assertThat(data.get("merchantId").asText()).isEqualTo(merchant.getId().toString());
        assertThat(data.get("decidedBy").asText()).isEqualTo(ADMIN);
    }

    @Test
    void approveAgainIsAConflict() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");

        postDecision(merchant, "approve", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        assertThat(outbox.count()).isZero();
    }

    @Test
    void rejectedAndSuspendedMerchantsCanBeApproved() throws Exception {
        Merchant rejected = fixtures.merchant("user-1", MerchantStatus.REJECTED);
        Merchant suspended = fixtures.merchant("user-2", MerchantStatus.SUSPENDED);

        postDecision(rejected, "approve", null).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decisionReason").value(nullValue()));
        postDecision(suspended, "approve", null).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void rejectStoresReasonAndPublishesEvent() throws Exception {
        Merchant merchant = fixtures.pendingMerchant("user-1");

        postDecision(merchant, "reject", Map.of("reason", "  Registration number could not be verified "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionReason").value("Registration number could not be verified"))
                .andExpect(jsonPath("$.decidedBy").value(ADMIN));

        OutboxEvent event = onlyEvent();
        assertThat(event.getEventType()).isEqualTo("merchant.rejected");
        JsonNode data = objectMapper.readTree(event.getPayload()).get("data");
        assertThat(data.get("reason").asText()).isEqualTo("Registration number could not be verified");
        assertThat(data.get("decidedBy").asText()).isEqualTo(ADMIN);

        mockMvc.perform(get("/api/v1/merchants/me").with(TestAuth.merchant("user-1")))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionReason").value("Registration number could not be verified"))
                .andExpect(jsonPath("$.decidedBy").doesNotExist());
    }

    @Test
    void rejectNeedsAReason() throws Exception {
        Merchant merchant = fixtures.pendingMerchant("user-1");

        postDecision(merchant, "reject", Map.of("reason", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("reason"));
        postDecision(merchant, "reject", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        postDecision(merchant, "reject", Map.of("reason", "x".repeat(501)))
                .andExpect(status().isBadRequest());
        assertThat(outbox.count()).isZero();
    }

    @Test
    void onlyPendingMerchantsCanBeRejected() throws Exception {
        for (MerchantStatus status : new MerchantStatus[] {
                MerchantStatus.APPROVED, MerchantStatus.REJECTED, MerchantStatus.SUSPENDED}) {
            Merchant merchant = fixtures.merchant("user-" + status, status);
            postDecision(merchant, "reject", Map.of("reason", "No"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        }
    }

    // ---- suspend / reinstate ----

    @Test
    void suspendAndReinstateRoundTrip() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");

        postDecision(merchant, "suspend", Map.of("reason", "Chargebacks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.decisionReason").value("Chargebacks"));
        postDecision(merchant, "reinstate", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decisionReason").value(nullValue()));

        assertThat(outbox.count()).isZero();
    }

    @Test
    void invalidSuspendAndReinstateTransitionsAreConflicts() throws Exception {
        Merchant pending = fixtures.pendingMerchant("user-1");
        Merchant approved = fixtures.approvedMerchant("user-2");

        postDecision(pending, "suspend", Map.of("reason", "x")).andExpect(status().isConflict());
        postDecision(approved, "reinstate", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        postDecision(pending, "reinstate", null).andExpect(status().isConflict());
    }

    @Test
    void decisionsOnUnknownMerchantAreNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/admin/merchants/" + unknown + "/approve").with(TestAuth.admin(ADMIN)))
                .andExpect(status().isNotFound());
    }

    // ---- rate limit ----

    @Test
    void rateLimitCanBeChanged() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");

        mockMvc.perform(put("/api/v1/admin/merchants/" + merchant.getId() + "/rate-limit").with(TestAuth.admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"requestsPerMinute\": 250}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateLimitPerMinute").value(250));
        mockMvc.perform(get("/api/v1/merchants/me").with(TestAuth.merchant("user-1")))
                .andExpect(jsonPath("$.rateLimitPerMinute").value(250));
    }

    @Test
    void rateLimitMustBeBetweenOneAndOneHundredThousand() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        String url = "/api/v1/admin/merchants/" + merchant.getId() + "/rate-limit";

        for (String body : new String[] {"{\"requestsPerMinute\": 0}", "{\"requestsPerMinute\": 100001}", "{}"}) {
            mockMvc.perform(put(url).with(TestAuth.admin(ADMIN)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        assertThat(merchants.findById(merchant.getId()).orElseThrow().getRateLimitPerMinute()).isEqualTo(60);
    }

    // ---- helpers ----

    private ResultActions postDecision(Merchant merchant, String action, Map<String, Object> body) throws Exception {
        var request = post("/api/v1/admin/merchants/" + merchant.getId() + "/" + action)
                .with(TestAuth.admin(ADMIN));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        } else if (action.equals("reject") || action.equals("suspend")) {
            request.contentType(MediaType.APPLICATION_JSON);
        }
        return mockMvc.perform(request);
    }

    private OutboxEvent onlyEvent() {
        assertThat(outbox.findAll()).hasSize(1);
        return outbox.findAll().get(0);
    }
}
