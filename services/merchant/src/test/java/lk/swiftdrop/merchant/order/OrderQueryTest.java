package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Listing and reading orders: filters, search, paging and the JSON shape the portal relies on. */
class OrderQueryTest extends AbstractOrderTest {

    private MerchantOrder created;
    private MerchantOrder delivered;
    private MerchantOrder inTransit;

    @BeforeEach
    void seedOrders() {
        created = orderFixtures.order(merchant).trackingNo("SD100").externalRef("SHOP-1")
                .createdAt(Instant.parse("2026-02-20T08:00:00Z")).save();
        delivered = orderFixtures.order(merchant).trackingNo("SD200").externalRef("SHOP-2").recipientName("Kamal Silva")
                .status(OrderStatus.DELIVERED).createdAt(Instant.parse("2026-02-25T23:30:00Z")).save();
        inTransit = orderFixtures.order(merchant).trackingNo("SD300").externalRef("A%B").recipientName("Percent Person")
                .status(OrderStatus.IN_TRANSIT).createdAt(Instant.parse("2026-02-26T00:30:00Z")).save();
        orderFixtures.order(merchant).trackingNo("SD400").externalRef("A_B").recipientName("Under_Score")
                .status(OrderStatus.FAILED).createdAt(Instant.parse("2026-03-01T09:00:00Z")).save();
        orderFixtures.order(merchant).sandbox().trackingNo("SBX100000001").externalRef("SB-1")
                .createdAt(Instant.parse("2026-03-01T10:00:00Z")).save();
        orderFixtures.order(merchant).trackingNo("SD600").externalRef("S-6").recipientName("Back\\Slash")
                .createdAt(Instant.parse("2026-01-15T08:00:00Z")).save();
        orderFixtures.order(merchant).externalRef("PH-1").createdAt(Instant.parse("2026-03-01T09:30:00Z"))
                .savePlaceholder();
    }

    private ResultActions portal(String path, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(PORTAL_ORDERS + path).with(TestAuth.merchant(OWNER));
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    // ---- portal list ----

    @Test
    void listsLiveOrdersNewestFirstAndHidesPlaceholders() throws Exception {
        portal("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD400", "SD300", "SD200", "SD100", "SD600")));
    }

    @Test
    void sandboxParameterSelectsTheEnvironment() throws Exception {
        portal("", "sandbox", "true").andExpect(jsonPath("$.content[*].trackingNo").value(contains("SBX100000001")));
        portal("", "sandbox", "sandbox").andExpect(jsonPath("$.totalElements").value(1));
        portal("", "sandbox", "false").andExpect(jsonPath("$.totalElements").value(5));
        portal("", "sandbox", "live").andExpect(jsonPath("$.totalElements").value(5));
        portal("", "sandbox", "all").andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.content[0].trackingNo").value("SBX100000001"));
    }

    @Test
    void unknownSandboxValueIsRejected() throws Exception {
        portal("", "sandbox", "maybe").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("sandbox"));
    }

    @Test
    void filtersByStatus() throws Exception {
        portal("", "status", "DELIVERED").andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD200")));
        portal("", "status", "CREATED").andExpect(jsonPath("$.totalElements").value(2));
        portal("", "status", "CANCELLED").andExpect(jsonPath("$.content").value(empty()));
    }

    @Test
    void placeholdersAreOnlyShownWhenAskedForExplicitly() throws Exception {
        portal("", "status", "PENDING")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].externalRef").value("PH-1"))
                .andExpect(jsonPath("$.content[0].trackingNo").doesNotExist())
                .andExpect(jsonPath("$.content[0].fee").doesNotExist());
    }

    @Test
    void unknownStatusIsRejected() throws Exception {
        portal("", "status", "BROKEN").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @ParameterizedTest
    @CsvSource({"sd2,SD200", "SD300,SD300", "shop-1,SD100", "SHOP,SD200|SD100", "kamal,SD200", "SILVA,SD200",
            "Perera,SD100", "'  shop-2  ',SD200"})
    void searchesTrackingNumberExternalReferenceAndRecipientNameIgnoringCase(String search, String expected)
            throws Exception {
        portal("", "search", search).andExpect(jsonPath("$.content[*].trackingNo")
                .value(contains(expected.split("\\|"))));
    }

    @Test
    void percentSignInTheSearchIsLiteral() throws Exception {
        portal("", "search", "%").andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD300")));
        portal("", "search", "a%b").andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD300")));
    }

    @Test
    void underscoreInTheSearchIsLiteral() throws Exception {
        portal("", "search", "_").andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD400")));
        portal("", "search", "a_b").andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD400")));
    }

    @Test
    void backslashInTheSearchIsLiteral() throws Exception {
        portal("", "search", "\\").andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD600")));
        portal("", "search", "back\\slash").andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD600")));
    }

    @Test
    void blankSearchIsIgnored() throws Exception {
        portal("", "search", "   ").andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void searchFindsNothingForUnknownText() throws Exception {
        portal("", "search", "zzz").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void dateRangeIsInclusiveAndInUtc() throws Exception {
        portal("", "from", "2026-02-26").andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD400", "SD300")));
        portal("", "to", "2026-02-25").andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD200", "SD100", "SD600")));
        portal("", "from", "2026-02-26", "to", "2026-02-26")
                .andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD300")));
        portal("", "from", "2026-02-25", "to", "2026-02-25")
                .andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD200")));
        portal("", "from", "2026-03-02").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersCombine() throws Exception {
        portal("", "search", "shop", "status", "DELIVERED", "from", "2026-02-25", "to", "2026-02-25")
                .andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD200")));
        portal("", "search", "shop", "status", "FAILED").andExpect(jsonPath("$.totalElements").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"26-02-2026", "2026-13-01", "yesterday"})
    void invalidDatesAreRejected(String date) throws Exception {
        portal("", "from", date).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        portal("", "to", date).andExpect(status().isBadRequest());
    }

    @Test
    void pagesThroughTheOrders() throws Exception {
        portal("", "size", "2", "page", "1")
                .andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD200", "SD100")))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
        portal("", "size", "2", "page", "2").andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD600")));
        portal("", "size", "2", "page", "9").andExpect(jsonPath("$.content").value(empty()));
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "size,0", "size,101", "size,-5"})
    void invalidPagingIsRejected(String name, String value) throws Exception {
        portal("", name, value).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void listItemsHaveExactlyTheFieldsOfTheFrontendModel() throws Exception {
        JsonNode item = body(portal("", "status", "DELIVERED")).get("content").get(0);

        assertThat(fieldNames(item)).containsExactlyInAnyOrder("id", "trackingNo", "externalRef", "status", "sandbox",
                "source", "trackingUrl", "recipient", "pickup", "dropoff", "parcel", "notes", "fee", "failureReason",
                "createdAt", "updatedAt");
        assertThat(fieldNames(item.get("recipient"))).containsExactlyInAnyOrder("name", "phone");
        assertThat(fieldNames(item.get("pickup"))).containsExactlyInAnyOrder("addressLine", "city", "postalCode");
        assertThat(fieldNames(item.get("dropoff"))).containsExactlyInAnyOrder("addressLine", "city", "postalCode");
        assertThat(fieldNames(item.get("parcel"))).containsExactlyInAnyOrder("weightKg", "size", "description");
        assertThat(fieldNames(item.get("fee"))).containsExactlyInAnyOrder("amount", "currency");
        assertThat(item.get("trackingUrl").asText()).isEqualTo("http://localhost:4200/track/SD200");
        assertThat(item.get("source").asText()).isEqualTo("API");
        assertThat(item.get("createdAt").asText()).isEqualTo("2026-02-25T23:30:00Z");
    }

    // ---- portal detail ----

    @Test
    void detailContainsTheTimelineOldestFirst() throws Exception {
        UUID id = delivered.getId();
        history.save(OrderStatusHistory.record(id, "e3", OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED,
                Instant.parse("2026-02-26T09:00:00Z"), null));
        history.save(OrderStatusHistory.record(id, "e1", null, OrderStatus.CREATED,
                Instant.parse("2026-02-25T23:30:00Z"), null));
        history.save(OrderStatusHistory.record(id, "e2", OrderStatus.CREATED, OrderStatus.IN_TRANSIT,
                Instant.parse("2026-02-26T08:00:00Z"), "Left the depot"));
        history.save(OrderStatusHistory.record(inTransit.getId(), "other", null, OrderStatus.CREATED,
                Instant.parse("2026-02-26T00:30:00Z"), null));

        JsonNode detail = body(mockMvc.perform(get(PORTAL_ORDERS + "/" + id).with(TestAuth.merchant(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.trackingNo").value("SD200"))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.history.length()").value(3))
                .andExpect(jsonPath("$.history[*].toStatus").value(contains("CREATED", "IN_TRANSIT", "DELIVERED")))
                .andExpect(jsonPath("$.history[0].fromStatus").value(nullValue()))
                .andExpect(jsonPath("$.history[1].note").value("Left the depot")));

        assertThat(fieldNames(detail.get("history").get(0)))
                .containsExactlyInAnyOrder("fromStatus", "toStatus", "occurredAt", "note");
        assertThat(fieldNames(detail)).contains("history").hasSize(17);
    }

    @Test
    void detailOfAnOrderWithoutHistoryHasAnEmptyTimeline() throws Exception {
        mockMvc.perform(get(PORTAL_ORDERS + "/" + created.getId()).with(TestAuth.merchant(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history").value(empty()));
    }

    @Test
    void unknownOrderIsNotFound() throws Exception {
        mockMvc.perform(get(PORTAL_ORDERS + "/" + UUID.randomUUID()).with(TestAuth.merchant(OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void orderIdThatIsNotAUuidIsAValidationError() throws Exception {
        mockMvc.perform(get(PORTAL_ORDERS + "/not-a-uuid").with(TestAuth.merchant(OWNER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ---- merchant API ----

    @Test
    void apiListsOnlyOrdersOfTheCredentialsEnvironment() throws Exception {
        mockMvc.perform(get(API_ORDERS).header("X-API-Key", liveKey().rawKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD400", "SD300", "SD200", "SD100", "SD600")));
        mockMvc.perform(get(API_ORDERS).header("X-API-Key", testKey().rawKey()))
                .andExpect(jsonPath("$.content[*].trackingNo").value(contains("SBX100000001")));
    }

    @Test
    void apiFiltersByExactExternalRefAndStatus() throws Exception {
        String key = liveKey().rawKey();

        mockMvc.perform(get(API_ORDERS).param("externalRef", "SHOP-1").header("X-API-Key", key))
                .andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD100")));
        mockMvc.perform(get(API_ORDERS).param("externalRef", "SHOP").header("X-API-Key", key))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(API_ORDERS).param("externalRef", "A%B").header("X-API-Key", key))
                .andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD300")));
        mockMvc.perform(get(API_ORDERS).param("status", "FAILED").header("X-API-Key", key))
                .andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD400")));
        mockMvc.perform(get(API_ORDERS).param("status", "FAILED").param("externalRef", "SHOP-1").header("X-API-Key", key))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void apiPagesAndRejectsBadPaging() throws Exception {
        String key = liveKey().rawKey();

        mockMvc.perform(get(API_ORDERS).param("size", "2").param("page", "2").header("X-API-Key", key))
                .andExpect(jsonPath("$.content[*].trackingNo").value(contains("SD600")))
                .andExpect(jsonPath("$.totalPages").value(3));
        mockMvc.perform(get(API_ORDERS).param("size", "101").header("X-API-Key", key))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(API_ORDERS).param("status", "NOPE").header("X-API-Key", key))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apiReturnsOneOrderByTrackingNumber() throws Exception {
        mockMvc.perform(get(API_ORDERS + "/SD200").header("X-API-Key", liveKey().rawKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(delivered.getId().toString()))
                .andExpect(jsonPath("$.externalRef").value("SHOP-2"))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.history").doesNotExist());
        mockMvc.perform(get(API_ORDERS + "/SD999").header("X-API-Key", liveKey().rawKey()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
