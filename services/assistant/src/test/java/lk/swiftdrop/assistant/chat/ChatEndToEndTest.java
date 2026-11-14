package lk.swiftdrop.assistant.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lk.swiftdrop.assistant.llm.LlmMessage;
import lk.swiftdrop.assistant.llm.LlmReply;
import lk.swiftdrop.assistant.support.FakeDownstream;
import lk.swiftdrop.assistant.support.ScriptedLlm;
import lk.swiftdrop.assistant.support.TestTokens;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

/**
 * The whole service over real HTTP: a signed-in user chats, a scripted "Gemini" asks for a tool, the chat loop runs it
 * through the real MCP endpoint with the user's token, and a fake Pricing service answers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ChatEndToEndTest {
    static final FakeDownstream PRICING = new FakeDownstream();
    static final String QUOTE = """
            {"quoteId":"q1","originZone":"COLOMBO","destinationZone":"KANDY","strategyUsed":"EXPRESS","currency":"LKR",
             "amounts":{"baseAmount":300.00,"weightAmount":200.00,"sizeAmount":50.00,"expressAmount":137.50,
             "discountAmount":68.75,"totalAmount":618.75},"expiresAt":"2026-11-13T12:15:00Z","promoCode":"SAVE10"}""";
    static final Map<String, Object> FEE_ARGS = Map.of("originZone", "COLOMBO", "destinationZone", "KANDY",
            "weightKg", 2.0, "deliveryType", "EXPRESS", "promoCode", "SAVE10");

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        ScriptedLlm scriptedLlm() { return new ScriptedLlm(); }
    }

    @DynamicPropertySource
    static void downstream(DynamicPropertyRegistry registry) {
        registry.add("assistant.downstream.pricing-url", PRICING::url);
    }

    @AfterAll
    static void stop() { PRICING.close(); }

    @LocalServerPort int port;
    @Autowired ScriptedLlm llm;
    TestRestTemplate rest;

    @BeforeEach
    void reset() {
        llm.reset();
        PRICING.reset();
        // Apache HttpClient silently retries a 503, which would call the scripted model twice; a browser would not.
        var factory = new HttpComponentsClientHttpRequestFactory(HttpClients.custom().disableAutomaticRetries().build());
        rest = new TestRestTemplate(new RestTemplateBuilder().requestFactory(() -> factory)
                .rootUri("http://localhost:" + port));
    }

    private ResponseEntity<JsonNode> post(String bearer, Object body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) headers.set("Authorization", bearer);
        return rest.exchange("/api/v1/assistant/chat", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private static LlmReply answerFromLastToolResult(java.util.List<LlmMessage> conversation) {
        var response = (LlmMessage.ToolResponse) conversation.get(conversation.size() - 1);
        return LlmReply.text("Your fee is " + response.results().get(0).content());
    }

    @Test
    void aCustomerAsksForAFeeAndTheAnswerComesFromTheRealToolOverMcp() {
        PRICING.on("POST", "/api/v1/pricing/estimate", 200, QUOTE);
        llm.thenCall("estimate_fee", FEE_ARGS).then(ChatEndToEndTest::answerFromLastToolResult);
        String bearer = TestTokens.bearer("customer-1", "CUSTOMER");

        var response = post(bearer, Map.of("message", "How much to send 2 kg express to Kandy with SAVE10?"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("reply").asText()).contains("618.75", "EXPRESS");
        assertThat(body.get("conversationId").asText()).isNotBlank();
        assertThat(body.at("/toolCalls/0/tool").asText()).isEqualTo("estimate_fee");
        assertThat(body.at("/toolCalls/0/arguments/originZone").asText()).isEqualTo("COLOMBO");
        assertThat(body.at("/toolCalls/0/failed").asBoolean()).isFalse();
        // the user's token reached Pricing through the MCP tool, and is never echoed back to the page
        assertThat(PRICING.calls()).singleElement().satisfies(call -> assertThat(call.authorization()).isEqualTo(bearer));
        assertThat(response.getBody().toString()).doesNotContain(bearer.substring(7));
        // the model was told about all three tools, taken from the MCP server
        assertThat(llm.calls().get(0).tools()).extracting(tool -> tool.name())
                .containsExactlyInAnyOrder("estimate_fee", "track_parcel", "get_history");
    }

    @Test
    void aFollowUpInTheSameConversationSeesTheEarlierExchange() {
        llm.thenReply("Hello!").thenReply("Still here.");
        String bearer = TestTokens.bearer("customer-1", "CUSTOMER");
        String id = post(bearer, Map.of("message", "hi")).getBody().get("conversationId").asText();
        var second = post(bearer, Map.of("message", "again", "conversationId", id));
        assertThat(second.getBody().get("conversationId").asText()).isEqualTo(id);
        assertThat(llm.calls().get(1).conversation()).hasSize(3);
    }

    @Test
    void theStandInTrackingAndHistoryToolsRunThroughTheSamePath() {
        llm.thenCall("track_parcel", Map.of("trackingNumber", "SD-4821-0937"))
                .then(ChatEndToEndTest::answerFromLastToolResult);
        var response = post(TestTokens.bearer("customer-1", "CUSTOMER"), Map.of("message", "Where is SD-4821-0937?"));
        assertThat(response.getBody().get("reply").asText()).contains("\"demo\":true", "OUT_FOR_DELIVERY");
    }

    @Test
    void aToolThatFailsOrDoesNotExistIsReportedNotRaisedAsAnError() {
        PRICING.on("POST", "/api/v1/pricing/estimate", 500, "{}");
        llm.thenCall("estimate_fee", FEE_ARGS).thenReply("Pricing is down, sorry.");
        var down = post(TestTokens.bearer("customer-1", "CUSTOMER"), Map.of("message", "fee?"));
        assertThat(down.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(down.getBody().at("/toolCalls/0/failed").asBoolean()).isTrue();

        llm.thenCall("delete_everything", Map.of()).thenReply("I cannot do that.");
        var unknown = post(TestTokens.bearer("customer-1", "CUSTOMER"), Map.of("message", "bad"));
        assertThat(unknown.getBody().at("/toolCalls/0/failed").asBoolean()).isTrue();
        assertThat(unknown.getBody().get("reply").asText()).isEqualTo("I cannot do that.");
    }

    @Test
    void merchantsMayChatButOtherRolesAndAnonymousUsersMayNot() {
        llm.thenReply("Hello merchant");
        assertThat(post(TestTokens.bearer("merchant-1", "MERCHANT"), Map.of("message", "hi")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(post(null, Map.of("message", "hi")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("Bearer not-a-token", Map.of("message", "hi")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post(TestTokens.bearer("admin-1", "ADMIN"), Map.of("message", "hi")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(TestTokens.bearer("courier-1", "COURIER"), Map.of("message", "hi")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(llm.calls()).hasSize(1);
    }

    @Test
    void badMessagesGetAClearFourHundredWithTheReason() {
        String bearer = TestTokens.bearer("customer-1", "CUSTOMER");
        assertThat(post(bearer, Map.of("message", "   ")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(bearer, Map.of("other", "x")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(bearer, Map.of("message", "hi", "conversationId", "x".repeat(65))).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        var tooLong = post(bearer, Map.of("message", "x".repeat(1001)));
        assertThat(tooLong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tooLong.getBody().get("detail").asText()).contains("too long");
        assertThat(llm.calls()).isEmpty();
    }

    @Test
    void aModelProblemReachesTheUserAsASafeSentence() {
        llm.then(conversation -> { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "The AI model is busy right now (usage limit reached). Please try again in a minute"); });
        var response = post(TestTokens.bearer("customer-1", "CUSTOMER"), Map.of("message", "hi"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("detail").asText()).contains("busy right now");
        assertThat(response.getBody().toString()).doesNotContain("Exception", "at lk.swiftdrop");
    }

    @Test
    void newChatForgetsTheConversationAndNeverFailsForUnknownOnes() {
        llm.thenReply("a1").thenReply("a2");
        String bearer = TestTokens.bearer("customer-1", "CUSTOMER");
        String id = post(bearer, Map.of("message", "hi")).getBody().get("conversationId").asText();
        var headers = new HttpHeaders();
        headers.set("Authorization", bearer);
        HttpStatusCode first = rest.exchange("/api/v1/assistant/conversations/" + id, HttpMethod.DELETE,
                new HttpEntity<>(headers), Void.class).getStatusCode();
        HttpStatusCode again = rest.exchange("/api/v1/assistant/conversations/" + id, HttpMethod.DELETE,
                new HttpEntity<>(headers), Void.class).getStatusCode();
        assertThat(first).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(again).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post(bearer, Map.of("message", "hello", "conversationId", id)).getBody().get("conversationId").asText())
                .isNotEqualTo(id);
    }

    @Test
    void theHealthCheckIsOpenAndNothingElseIsExposed() {
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/actuator/env", String.class).getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(rest.getForEntity("/api/v1/other", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
