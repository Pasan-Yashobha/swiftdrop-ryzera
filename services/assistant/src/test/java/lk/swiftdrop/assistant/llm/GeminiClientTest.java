package lk.swiftdrop.assistant.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lk.swiftdrop.assistant.config.AssistantProperties;
import lk.swiftdrop.assistant.support.FakeDownstream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** The Gemini client against a fake Gemini server: the request it builds, the answers it understands, the errors it maps. */
class GeminiClientTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String PATH = "/v1beta/models/test-model:generateContent";

    FakeDownstream gemini;
    GeminiClient client;

    @BeforeEach
    void start() {
        gemini = new FakeDownstream();
        client = clientFor(gemini.url(), "test-model");
    }

    @AfterEach
    void stop() { gemini.close(); }

    static GeminiClient clientFor(String url, String model) {
        return new GeminiClient(new AssistantProperties.Gemini("secret-key-123", model, url, 5), JSON);
    }

    static final List<ToolDefinition> TOOLS = List.of(new ToolDefinition("estimate_fee", "Estimate a fee", Map.of(
            "type", "object", "additionalProperties", false, "$schema", "x",
            "properties", Map.of("originZone", Map.of("type", "string", "description", "Pickup zone"),
                    "weightKg", Map.of("type", "number")),
            "required", List.of("originZone", "weightKg"))));

    JsonNode sentBody() throws Exception {
        return JSON.readTree(gemini.calls().get(0).body());
    }

    @Test
    void aTextAnswerIsReturned() {
        gemini.on("POST", PATH, 200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello \"},{\"text\":\"there\"}]}}]}");
        var reply = client.generate("be brief", List.of(new LlmMessage.User("hi")), TOOLS);
        assertThat(reply.text()).isEqualTo("Hello there");
        assertThat(reply.toolCalls()).isEmpty();
    }

    @Test
    void theRequestCarriesTheKeyInAHeaderNotTheUrlAndDescribesTheConversationAndTools() throws Exception {
        gemini.on("POST", PATH, 200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}");
        client.generate("be brief", List.of(new LlmMessage.User("hi"), new LlmMessage.Model("hello"),
                new LlmMessage.User("how much?")), TOOLS);
        var call = gemini.calls().get(0);
        assertThat(call.headers()).containsEntry("x-goog-api-key", "secret-key-123");
        assertThat(call.query()).isNull();
        assertThat(call.path()).doesNotContain("secret-key-123");

        JsonNode body = sentBody();
        assertThat(body.at("/systemInstruction/parts/0/text").asText()).isEqualTo("be brief");
        assertThat(body.at("/contents/0/role").asText()).isEqualTo("user");
        assertThat(body.at("/contents/1/role").asText()).isEqualTo("model");
        assertThat(body.at("/contents/2/parts/0/text").asText()).isEqualTo("how much?");
        JsonNode declaration = body.at("/tools/0/functionDeclarations/0");
        assertThat(declaration.get("name").asText()).isEqualTo("estimate_fee");
        // only the schema keywords Gemini accepts survive
        JsonNode parameters = declaration.get("parameters");
        assertThat(parameters.get("type").asText()).isEqualTo("object");
        assertThat(parameters.has("additionalProperties")).isFalse();
        assertThat(parameters.has("$schema")).isFalse();
        assertThat(parameters.at("/properties/weightKg/type").asText()).isEqualTo("number");
        assertThat(parameters.at("/properties/originZone/description").asText()).isEqualTo("Pickup zone");
        assertThat(parameters.get("required")).hasSize(2);
    }

    @Test
    void withoutToolsNoToolsAreSentAndAModelPrefixInTheNameIsAccepted() throws Exception {
        gemini.on("POST", PATH, 200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}");
        clientFor(gemini.url() + "/", "models/test-model").generate("s", List.of(new LlmMessage.User("hi")), List.of());
        assertThat(sentBody().has("tools")).isFalse();
    }

    @Test
    void aFunctionCallIsUnderstoodAndTheModelsOwnPartsAreKeptForReplay() {
        String parts = "[{\"functionCall\":{\"name\":\"estimate_fee\",\"args\":{\"originZone\":\"COLOMBO\",\"weightKg\":2}},"
                + "\"thoughtSignature\":\"sig-abc\"}]";
        gemini.on("POST", PATH, 200, "{\"candidates\":[{\"content\":{\"parts\":" + parts + "}}]}");
        var reply = client.generate("s", List.of(new LlmMessage.User("fee?")), TOOLS);
        assertThat(reply.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.name()).isEqualTo("estimate_fee");
            assertThat(call.arguments()).containsEntry("originZone", "COLOMBO").containsEntry("weightKg", 2);
        });
        assertThat(reply.providerState()).isInstanceOf(JsonNode.class);
        assertThat(reply.providerState().toString()).contains("sig-abc");
    }

    @Test
    void aToolTurnIsSentBackWithTheModelsOwnPartsAndTheToolResultsAsFunctionResponses() throws Exception {
        gemini.on("POST", PATH, 200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"It costs 550\"}]}}]}");
        JsonNode raw = JSON.readTree("[{\"functionCall\":{\"name\":\"estimate_fee\",\"args\":{}},\"thoughtSignature\":\"sig-abc\"}]");
        client.generate("s", List.of(new LlmMessage.User("fee?"),
                new LlmMessage.ToolRequest(List.of(new ToolCall("estimate_fee", Map.of())), raw),
                new LlmMessage.ToolResponse(List.of(new ToolResult("estimate_fee", "{\"total\":550.00}"),
                        new ToolResult("track_parcel", "not json at all")))), TOOLS);
        JsonNode contents = sentBody().get("contents");
        assertThat(contents.get(1).get("role").asText()).isEqualTo("model");
        assertThat(contents.at("/1/parts/0/thoughtSignature").asText()).isEqualTo("sig-abc");
        assertThat(contents.get(2).get("role").asText()).isEqualTo("user");
        assertThat(contents.at("/2/parts/0/functionResponse/name").asText()).isEqualTo("estimate_fee");
        assertThat(contents.at("/2/parts/0/functionResponse/response/result/total").decimalValue()).isEqualByComparingTo("550.00");
        assertThat(contents.at("/2/parts/1/functionResponse/response/result").asText()).isEqualTo("not json at all");
    }

    @Test
    void aToolRequestWithoutRawPartsIsRebuiltFromTheCalls() throws Exception {
        gemini.on("POST", PATH, 200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}");
        client.generate("s", List.of(new LlmMessage.User("fee?"),
                new LlmMessage.ToolRequest(List.of(new ToolCall("estimate_fee", Map.of("weightKg", 2))), null),
                new LlmMessage.ToolResponse(List.of(new ToolResult("estimate_fee", "{}")))), TOOLS);
        JsonNode part = sentBody().at("/contents/1/parts/0/functionCall");
        assertThat(part.get("name").asText()).isEqualTo("estimate_fee");
        assertThat(part.at("/args/weightKg").asInt()).isEqualTo(2);
    }

    @Test
    void theModelsInnerThoughtsAreNeverShownToTheUser() {
        gemini.on("POST", PATH, 200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"private reasoning\",\"thought\":true},"
                + "{\"text\":\"The answer\"}]}}]}");
        assertThat(client.generate("s", List.of(new LlmMessage.User("q")), TOOLS).text()).isEqualTo("The answer");
    }

    @Test
    void ABlockedPromptGetsAPoliteRefusalAndAnEmptyAnswerStaysEmpty() {
        gemini.on("POST", PATH, 200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");
        assertThat(client.generate("s", List.of(new LlmMessage.User("q")), TOOLS).text()).isEqualTo(GeminiClient.BLOCKED);
        gemini.on("POST", PATH, 200, "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}");
        assertThat(client.generate("s", List.of(new LlmMessage.User("q")), TOOLS).text()).isEqualTo(GeminiClient.BLOCKED);
        gemini.on("POST", PATH, 200, "{\"candidates\":[{\"content\":{\"parts\":[]},\"finishReason\":\"STOP\"}]}");
        assertThat(client.generate("s", List.of(new LlmMessage.User("q")), TOOLS).text()).isEmpty();
        gemini.on("POST", PATH, 200, "{}");
        assertThat(client.generate("s", List.of(new LlmMessage.User("q")), TOOLS).text()).isEmpty();
    }

    @Test
    void providerErrorsBecomeSafeMessagesWithoutLeakingTheirDetails() {
        expect(429, 503, "busy");
        expect(400, 502, "could not process");
        expect(401, 502, "could not process");
        expect(403, 502, "could not process");
        expect(404, 502, "could not process");
        expect(500, 502, "having problems");
        expect(503, 502, "having problems");
    }

    private void expect(int providerStatus, int ourStatus, String messagePart) {
        gemini.on("POST", PATH, providerStatus, "{\"error\":{\"message\":\"secret-key-123 is invalid\"}}");
        assertThatThrownBy(() -> client.generate("s", List.of(new LlmMessage.User("q")), TOOLS))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode().value()).isEqualTo(ourStatus);
                    assertThat(error.getReason()).contains(messagePart).doesNotContain("secret-key-123");
                });
    }

    @Test
    void aGeminiThatCannotBeReachedIsReportedAsATimeout() {
        String deadUrl = gemini.url();
        gemini.close();
        assertThatThrownBy(() -> clientFor(deadUrl, "test-model").generate("s", List.of(new LlmMessage.User("q")), TOOLS))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(504));
        gemini = new FakeDownstream();
    }

    @Test
    void theConfigurationIsOnlyCompleteWithBothAKeyAndAModel() {
        assertThat(new AssistantProperties.Gemini("k", "m", "u", 5).configured()).isTrue();
        assertThat(new AssistantProperties.Gemini("", "m", "u", 5).configured()).isFalse();
        assertThat(new AssistantProperties.Gemini("k", " ", "u", 5).configured()).isFalse();
        assertThat(new AssistantProperties.Gemini(null, null, "u", 5).configured()).isFalse();
    }
}
