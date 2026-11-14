package lk.swiftdrop.assistant.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lk.swiftdrop.assistant.support.McpTestClient;
import lk.swiftdrop.assistant.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

/** With no Gemini key or model set, chat says so plainly while the MCP tools keep working for any other MCP client. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class UnconfiguredModelTest {
    @Autowired TestRestTemplate rest;
    @LocalServerPort int port;

    @Test
    void chatAnswersServiceUnavailableWithAnExplanation() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", TestTokens.bearer("customer-1", "CUSTOMER"));
        var response = rest.exchange("/api/v1/assistant/chat", HttpMethod.POST,
                new HttpEntity<>(Map.of("message", "hi"), headers), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("detail").asText()).contains("not configured");
    }

    @Test
    void theToolsStillWorkWithoutAModel() {
        try (var client = new McpTestClient(port, TestTokens.bearer("customer-1", "CUSTOMER"))) {
            assertThat(client.tools().tools()).hasSize(3);
        }
    }
}
