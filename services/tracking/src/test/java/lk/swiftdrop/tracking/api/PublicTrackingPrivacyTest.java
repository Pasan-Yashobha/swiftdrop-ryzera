package lk.swiftdrop.tracking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lk.swiftdrop.tracking.event.TrackingEventConsumer;
import lk.swiftdrop.tracking.testsupport.Events;
import lk.swiftdrop.tracking.testsupport.InMemoryTrackingConfig;
import lk.swiftdrop.tracking.testsupport.InMemoryTrackingStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Work-split test "public endpoint leaks no personal data": producers send names, phone numbers, e-mail addresses,
 * street addresses and internal ids; none of them may appear in the public response, the live stream or even the
 * stored read model, and the response may only contain the fields of {@link PublicTrackingResponse}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryTrackingConfig.class)
class PublicTrackingPrivacyTest {

    private static final String NO = "SD-7000-0001";
    private static final String PARCEL_ID = "8d1f7c2e-5b8a-4c61-9f51-2a7d3e0b9c11";
    private static final String COURIER_ID = "c0ffee00-1111-4222-8333-444455556666";
    private static final Instant T0 = Instant.now().minusSeconds(3600);

    private static final List<String> PERSONAL_DATA = List.of(
            "Nimali", "Perera", "+94771234567", "0771234567", "nimali@example.com", "45 Temple Street",
            "Temple Street", "20000", "Kasun", "Silva", "+94719876543", "customer-7d1f", "merchant-55aa",
            PARCEL_ID, COURIER_ID);

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "trackingNo", "status", "statusLabel", "tone", "stop", "totalStops", "terminal", "origin", "destination",
            "city", "point", "lat", "lng", "parcel", "size", "weightKg", "serviceType", "stops", "label", "at",
            "state", "history", "location", "note", "eta", "from", "to", "basis", "deliveredAt", "failure", "code",
            "courier", "position", "trail", "distanceKm", "lastUpdatedAt", "version");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TrackingEventConsumer consumer;

    @Autowired
    private InMemoryTrackingStore store;

    @Test
    void thePublicEndpointLeaksNoPersonalData() throws Exception {
        store.clear();
        consumer.onMessage(Events.created(NO, PARCEL_ID, 1, T0)
                .data("customerId", "customer-7d1f")
                .data("merchantId", "merchant-55aa")
                .data("recipientName", "Nimali Perera")
                .data("recipientPhone", "0771234567")
                .data("recipient", Map.of("name", "Nimali Perera", "phone", "+94771234567",
                        "email", "nimali@example.com"))
                .data("dropoff", Map.of("addressLine", "45 Temple Street", "city", "Kandy", "postalCode", "20000"))
                .data("notes", "Call Nimali on +94771234567")
                .message());
        consumer.onMessage(Events.event("parcel.assigned").at(T0.plusSeconds(60))
                .data("parcelId", PARCEL_ID)
                .data("courierId", COURIER_ID)
                .data("courierName", "Kasun Silva")
                .data("courierPhone", "+94719876543")
                .message());
        consumer.onMessage(Events.status("out_for_delivery", NO, 3, T0.plusSeconds(120))
                .data("hub", "Kandy hub")
                .data("courierName", "Kasun Silva")
                .data("recipientPhone", "0771234567")
                .message());
        consumer.onMessage(Events.location(COURIER_ID, 7.2812345, 80.6123456, Instant.now()).message());

        MvcResult stream = mvc.perform(get("/api/v1/tracking/" + NO + "/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        String onTheWay = mvc.perform(get("/api/v1/tracking/" + NO))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        consumer.onMessage(Events.status("failed", NO, 4, T0.plusSeconds(180))
                .data("failureReason", "Nimali Perera not home, phone 0771234567 no answer")
                .message());
        String failed = mvc.perform(get("/api/v1/tracking/" + NO))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String streamed = stream.getResponse().getContentAsString(StandardCharsets.UTF_8);

        // The stored model keeps the two internal ids it needs for routing events; nothing else personal.
        String stored = store.rawJson(NO).replace(COURIER_ID, "").replace(PARCEL_ID, "");
        for (String body : List.of(onTheWay, failed, streamed, stored)) {
            for (String value : PERSONAL_DATA) {
                assertThat(body).as("must not contain %s", value).doesNotContain(value);
            }
        }
        assertThat(fieldNames(Events.MAPPER.readTree(onTheWay))).isSubsetOf(ALLOWED_FIELDS);
        assertThat(fieldNames(Events.MAPPER.readTree(failed))).isSubsetOf(ALLOWED_FIELDS);

        JsonNode response = Events.MAPPER.readTree(onTheWay);
        // The useful, non-personal parts are still there.
        assertThat(response.at("/destination/city").asText()).isEqualTo("Kandy");
        assertThat(response.at("/courier/position/lat").asDouble()).isEqualTo(7.281);
        assertThat(response.at("/history/0/location").asText()).isEqualTo("Kandy hub");
        assertThat(Events.MAPPER.readTree(failed).at("/failure/code").asText()).isEqualTo("OTHER");
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        collect(node, names);
        return names;
    }

    private static void collect(JsonNode node, Set<String> names) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                names.add(field.getKey());
                collect(field.getValue(), names);
            }
        } else if (node.isArray()) {
            node.forEach(child -> collect(child, names));
        }
    }
}
