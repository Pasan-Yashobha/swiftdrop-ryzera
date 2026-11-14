package lk.swiftdrop.assistant.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lk.swiftdrop.assistant.support.FakeDownstream;
import lk.swiftdrop.assistant.support.McpTestClient;
import lk.swiftdrop.assistant.support.TestTokens;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The three MCP tools, called by a real MCP client over HTTP, with all downstream services faked. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class McpToolsIntegrationTest {
    static final FakeDownstream DOWNSTREAM = new FakeDownstream();

    @DynamicPropertySource
    static void downstream(DynamicPropertyRegistry registry) {
        registry.add("assistant.downstream.pricing-url", DOWNSTREAM::url);
        registry.add("assistant.downstream.tracking-url", DOWNSTREAM::url);
        registry.add("assistant.downstream.parcel-url", DOWNSTREAM::url);
    }

    @AfterAll
    static void stop() { DOWNSTREAM.close(); }

    @LocalServerPort int port;

    static final String QUOTE = """
            {"quoteId":"q1","rateCardId":"r1","originZone":"COLOMBO","destinationZone":"KANDY","strategyUsed":"EXPRESS",
             "currency":"LKR","amounts":{"baseAmount":300.00,"weightAmount":200.00,"sizeAmount":50.00,
             "expressAmount":137.50,"discountAmount":68.75,"totalAmount":618.75},
             "createdAt":"2026-11-13T12:00:00Z","expiresAt":"2026-11-13T12:15:00Z","promoCode":"SAVE10"}""";
    static final String ZONES = """
            {"items":[{"id":"1","code":"COLOMBO","name":"Colombo"},{"id":"2","code":"KANDY","name":"Kandy"}],
             "page":0,"size":100,"totalElements":2}""";

    @BeforeEach
    void reset() { DOWNSTREAM.reset(); }

    private static Map<String, Object> args(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }

    @Test
    void theServerAdvertisesTheThreeToolsWithTheirRequiredInputs() {
        try (var client = new McpTestClient(port, TestTokens.bearer("customer-1", "CUSTOMER"))) {
            var tools = client.tools().tools();
            assertThat(tools.stream().map(McpSchema.Tool::name).collect(Collectors.toSet()))
                    .isEqualTo(Set.of("estimate_fee", "track_parcel", "get_history"));
            var fee = tools.stream().filter(tool -> tool.name().equals("estimate_fee")).findFirst().orElseThrow();
            assertThat(fee.inputSchema().required()).containsExactlyInAnyOrder("originZone", "destinationZone", "weightKg");
            assertThat(fee.description()).contains("LKR");
        }
    }

    @Test
    void estimateFeeAsksPricingWithTheUsersOwnTokenAndReturnsAReadableAnswer() {
        DOWNSTREAM.on("POST", "/api/v1/pricing/estimate", 200, QUOTE);
        String bearer = TestTokens.bearer("customer-1", "CUSTOMER");
        try (var client = new McpTestClient(port, bearer)) {
            JsonNode answer = client.call("estimate_fee", args("originZone", "colombo", "destinationZone", " Kandy ",
                    "weightKg", 2.0, "deliveryType", "express", "promoCode", "SAVE10"));
            assertThat(answer.get("total").decimalValue()).isEqualByComparingTo("618.75");
            assertThat(answer.get("currency").asText()).isEqualTo("LKR");
            assertThat(answer.get("deliveryType").asText()).isEqualTo("EXPRESS");
            assertThat(answer.get("breakdown").get("expressSurcharge").decimalValue()).isEqualByComparingTo("137.50");
            assertThat(answer.get("promoCode").asText()).isEqualTo("SAVE10");
            assertThat(answer.has("quoteId")).isFalse();
        }
        var call = DOWNSTREAM.calls().get(0);
        assertThat(call.authorization()).isEqualTo(bearer);
        assertThat(call.body()).contains("\"originZone\":\"COLOMBO\"", "\"destinationZone\":\"KANDY\"",
                "\"deliveryType\":\"EXPRESS\"", "\"sizeClass\":\"MEDIUM\"", "\"promoCode\":\"SAVE10\"");
    }

    @Test
    void estimateFeeWithAnUnknownZoneListsTheRealZonesSoTheModelCanCorrectItself() {
        DOWNSTREAM.on("POST", "/api/v1/pricing/estimate", 422, "{}");
        DOWNSTREAM.on("GET", "/api/v1/pricing/zones", 200, ZONES);
        try (var client = new McpTestClient(port, TestTokens.bearer("customer-1", "CUSTOMER"))) {
            JsonNode answer = client.call("estimate_fee", args("originZone", "COLOMBO", "destinationZone", "ATLANTIS",
                    "weightKg", 1.0));
            assertThat(answer.get("error").asText()).contains("The delivery zones are: COLOMBO, KANDY");
        }
    }

    @Test
    void estimateFeeRejectsBadInputWithoutCallingPricing() {
        try (var client = new McpTestClient(port, TestTokens.bearer("customer-1", "CUSTOMER"))) {
            assertThat(client.call("estimate_fee", args("originZone", "COLOMBO", "destinationZone", "KANDY",
                    "weightKg", 25.0)).get("error").asText()).contains("at most 20 kg");
            assertThat(client.call("estimate_fee", args("originZone", "COLOMBO", "destinationZone", "KANDY",
                    "weightKg", 0.0)).get("error").asText()).contains("more than 0 kg");
            assertThat(client.call("estimate_fee", args("originZone", "COLOMBO", "destinationZone", "KANDY",
                    "weightKg", 2.0, "sizeClass", "HUGE")).get("error").asText()).contains("SMALL, MEDIUM or LARGE");
            assertThat(client.call("estimate_fee", args("originZone", "COLOMBO", "destinationZone", "KANDY",
                    "weightKg", 2.0, "deliveryType", "TELEPORT")).get("error").asText()).contains("STANDARD or EXPRESS");
            assertThat(client.call("estimate_fee", args("originZone", " ", "destinationZone", "KANDY",
                    "weightKg", 2.0)).get("error").asText()).contains("pickup zone and the delivery zone");
        }
        assertThat(DOWNSTREAM.calls()).isEmpty();
    }

    @Test
    void estimateFeeExplainsWhenThePricingServiceRefusesOrIsDown() {
        try (var client = new McpTestClient(port, TestTokens.bearer("customer-1", "CUSTOMER"))) {
            var input = args("originZone", "COLOMBO", "destinationZone", "KANDY", "weightKg", 2.0);
            DOWNSTREAM.on("POST", "/api/v1/pricing/estimate", 403, "{}");
            assertThat(client.call("estimate_fee", input).get("error").asText()).contains("not allowed");
            DOWNSTREAM.on("POST", "/api/v1/pricing/estimate", 500, "{}");
            assertThat(client.call("estimate_fee", input).get("error").asText()).contains("not available right now");
        }
    }

    @Test
    void trackParcelCallsTrackingWithoutSendingAnyToken() {
        DOWNSTREAM.on("GET", "/api/v1/tracking/SD-1234-5678", 200, "{\"trackingNumber\":\"SD-1234-5678\",\"status\":\"IN_TRANSIT\"}");
        try (var client = new McpTestClient(port, TestTokens.bearer("customer-1", "CUSTOMER"))) {
            JsonNode answer = client.call("track_parcel", args("trackingNumber", "SD-1234-5678"));
            assertThat(answer.get("data").get("status").asText()).isEqualTo("IN_TRANSIT");
        }
        assertThat(DOWNSTREAM.calls()).hasSize(1);
        assertThat(DOWNSTREAM.calls().get(0).authorization()).isNull();
    }

    @Test
    void trackParcelHandlesMalformedNumbersUnknownParcelsAndOutages() {
        try (var client = new McpTestClient(port, TestTokens.bearer("customer-1", "CUSTOMER"))) {
            assertThat(client.call("track_parcel", args("trackingNumber", "../etc/passwd")).get("error").asText())
                    .contains("does not look like a tracking number");
            assertThat(DOWNSTREAM.calls()).isEmpty();
            assertThat(client.call("track_parcel", args("trackingNumber", "SD-0000-0000")).get("error").asText())
                    .contains("No parcel was found");
            DOWNSTREAM.on("GET", "/api/v1/tracking/SD-1111-1111", 500, "{}");
            assertThat(client.call("track_parcel", args("trackingNumber", "SD-1111-1111")).get("error").asText())
                    .contains("not available right now");
        }
    }

    @Test
    void trackParcelCutsAVeryLargeAnswerDownToASafeSize() {
        DOWNSTREAM.on("GET", "/api/v1/tracking/SD-2222-2222", 200, "{\"events\":\"" + "x".repeat(9000) + "\"}");
        try (var client = new McpTestClient(port, TestTokens.bearer("customer-1", "CUSTOMER"))) {
            JsonNode answer = client.call("track_parcel", args("trackingNumber", "SD-2222-2222"));
            assertThat(answer.get("truncated").asBoolean()).isTrue();
            assertThat(answer.get("data").asText().length()).isLessThanOrEqualTo(4000);
        }
    }

    @Test
    void getHistoryForwardsTheUsersTokenAndClampsTheLimit() {
        DOWNSTREAM.on("GET", "/api/v1/parcels/history", 200, "{\"parcels\":[{\"trackingNumber\":\"SD-9999-0001\"}]}");
        String bearer = TestTokens.bearer("customer-7", "CUSTOMER");
        try (var client = new McpTestClient(port, bearer)) {
            assertThat(client.call("get_history", args()).get("data").get("parcels").get(0).get("trackingNumber").asText())
                    .isEqualTo("SD-9999-0001");
            client.call("get_history", args("limit", 99));
            client.call("get_history", args("limit", 0));
        }
        var calls = DOWNSTREAM.calls();
        assertThat(calls).extracting(FakeDownstream.Call::query).containsExactly("limit=5", "limit=20", "limit=1");
        assertThat(calls).extracting(FakeDownstream.Call::authorization).containsOnly(bearer);
    }

    @Test
    void getHistoryExplainsRefusalsAndOutages() {
        try (var client = new McpTestClient(port, TestTokens.bearer("customer-7", "CUSTOMER"))) {
            DOWNSTREAM.on("GET", "/api/v1/parcels/history", 403, "{}");
            assertThat(client.call("get_history", args()).get("error").asText()).contains("not allowed");
            DOWNSTREAM.on("GET", "/api/v1/parcels/history", 503, "{}");
            assertThat(client.call("get_history", args()).get("error").asText()).contains("not available right now");
        }
    }

    @Test
    void theMcpEndpointRequiresAValidTokenFromACustomerOrMerchant() {
        assertThatThrownBy(() -> new McpTestClient(port, null).close()).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new McpTestClient(port, "Bearer not-a-real-token").close()).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new McpTestClient(port, TestTokens.bearer("admin-1", "ADMIN")).close())
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new McpTestClient(port, TestTokens.bearer("courier-1", "COURIER")).close())
                .isInstanceOf(RuntimeException.class);
        try (var merchant = new McpTestClient(port, TestTokens.bearer("merchant-1", "MERCHANT"))) {
            assertThat(merchant.tools().tools()).hasSize(3);
        }
    }
}
