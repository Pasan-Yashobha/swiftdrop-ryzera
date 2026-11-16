package lk.swiftdrop.assistant.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import lk.swiftdrop.assistant.support.McpTestClient;
import lk.swiftdrop.assistant.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/** Tracking and parcel history while those services are not connected: clearly labelled sample data. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class McpStandInToolsTest {
    @LocalServerPort int port;

    private static Map<String, Object> args(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }

    @Test
    void trackingReturnsSampleDataThatSaysItIsSampleData() {
        try (var client = new McpTestClient(port, TestTokens.bearer("customer-1", "CUSTOMER"))) {
            JsonNode answer = client.call("track_parcel", args("trackingNumber", "SD-4821-0937"));
            assertThat(answer.get("demo").asBoolean()).isTrue();
            assertThat(answer.get("note").asText()).contains("not a real parcel");
            assertThat(answer.get("trackingNumber").asText()).isEqualTo("SD-4821-0937");
            assertThat(answer.get("status").asText()).isEqualTo("OUT_FOR_DELIVERY");
            assertThat(answer.get("events")).hasSize(4);
        }
    }

    @Test
    void historyReturnsSampleParcelsLimitedToWhatWasAsked() {
        try (var client = new McpTestClient(port, TestTokens.bearer("customer-1", "CUSTOMER"))) {
            JsonNode all = client.call("get_history", args());
            assertThat(all.get("demo").asBoolean()).isTrue();
            assertThat(all.get("parcels")).hasSize(3);
            assertThat(client.call("get_history", args("limit", 1)).get("parcels")).hasSize(1);
            assertThat(client.call("get_history", args("limit", 99)).get("parcels")).hasSize(3);
        }
    }
}
