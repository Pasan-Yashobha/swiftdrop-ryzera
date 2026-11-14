package lk.swiftdrop.webhook.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.webhook.security.InternalTokenFilter;
import lk.swiftdrop.webhook.testsupport.AbstractIntegrationTest;
import lk.swiftdrop.webhook.testsupport.Envelopes;
import lk.swiftdrop.webhook.testsupport.TestAuth;
import lk.swiftdrop.webhook.testsupport.TestHttpServer;
import org.junit.jupiter.api.Test;

class InternalDeliveryApiTest extends AbstractIntegrationTest {

    private static String log(UUID merchantId) {
        return "/internal/merchants/" + merchantId + "/webhook-deliveries";
    }

    @Test
    void requiresTheInternalToken() throws Exception {
        UUID merchantId = newMerchant();
        mockMvc.perform(get(log(merchantId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get(log(merchantId)).header(InternalTokenFilter.HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(log(merchantId)).header("Authorization", "Bearer some.identity.jwt"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(log(merchantId)).with(TestAuth.admin("admin-1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalTokenDoesNotOpenTheAdminApi() throws Exception {
        mockMvc.perform(get("/api/v1/admin/webhooks/deliveries").with(TestAuth.internal()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheShapeTheMerchantServiceReads() throws Exception {
        UUID merchantId = newMerchant();
        UUID otherMerchant = newMerchant();
        try (TestHttpServer receiver = TestHttpServer.scripted(503, 200)) {
            MERCHANT_SERVICE.webhook(merchantId, receiver.url(), SECRET);
            MERCHANT_SERVICE.webhook(otherMerchant, receiver.url(), SECRET);
            consume(Envelopes.parcelDelivered("e-mine", merchantId));
            consume(Envelopes.parcelDelivered("e-other", otherMerchant));
        }

        String body = mockMvc.perform(get(log(merchantId)).param("page", "0").param("size", "10")
                        .with(TestAuth.internal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].eventId").value("e-mine"))
                .andExpect(jsonPath("$.content[0].eventType").value("parcel.status.delivered"))
                .andExpect(jsonPath("$.content[0].status").value("RETRYING"))
                .andExpect(jsonPath("$.content[0].attempts").value(1))
                .andExpect(jsonPath("$.content[0].lastHttpStatus").value(503))
                .andExpect(jsonPath("$.content[0].error").value("HTTP 503"))
                .andReturn().getResponse().getContentAsString();

        JsonNode item = json(body).path("content").get(0);
        List<String> fields = new ArrayList<>();
        item.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("id", "eventId", "eventType", "status", "attempts", "lastHttpStatus",
                "durationMs", "createdAt", "lastAttemptAt", "error");
        assertThat(item.path("id").asText()).isEqualTo(onlyDeliveryOf(merchantId));
        assertThat(item.path("durationMs").isIntegralNumber()).isTrue();
        assertThat(item.path("createdAt").asText()).endsWith("Z");
    }

    @Test
    void merchantWithoutDeliveriesGetsAnEmptyPage() throws Exception {
        mockMvc.perform(get(log(newMerchant())).with(TestAuth.internal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void invalidMerchantIdAndPagingAreRejected() throws Exception {
        mockMvc.perform(get("/internal/merchants/M-1/webhook-deliveries").with(TestAuth.internal()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(log(newMerchant())).param("page", "-1").with(TestAuth.internal()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String onlyDeliveryOf(UUID merchantId) {
        return deliveries.findAll().stream().filter(d -> d.getMerchantId().equals(merchantId)).findFirst()
                .orElseThrow().getId().toString();
    }
}
