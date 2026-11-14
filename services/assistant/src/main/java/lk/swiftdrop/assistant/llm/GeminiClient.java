package lk.swiftdrop.assistant.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lk.swiftdrop.assistant.config.AssistantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Google Gemini through its REST API ({@code generateContent}) with function calling. The API key travels in the
 * {@code x-goog-api-key} header, never in the URL. Message text and the key are never logged, and provider error
 * bodies are never passed on to the user.
 */
public class GeminiClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    /** The parts of JSON Schema that Gemini function declarations accept. */
    private static final Set<String> SCHEMA_KEYS = Set.of("type", "description", "properties", "required", "enum", "items", "format");
    static final String BLOCKED = "I'm sorry, I can't help with that request.";

    private final AssistantProperties.Gemini config;
    private final ObjectMapper mapper;
    private final RestClient rest;

    public GeminiClient(AssistantProperties.Gemini config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(Math.max(5, config.timeoutSeconds())).toMillis());
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public LlmReply generate(String systemPrompt, List<LlmMessage> conversation, List<ToolDefinition> tools) {
        ObjectNode request = request(systemPrompt, conversation, tools);
        JsonNode response;
        try {
            response = rest.post().uri(endpoint()).header("x-goog-api-key", config.apiKey())
                    .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw failure(exception.getStatusCode().value());
        } catch (ResourceAccessException exception) {
            log.warn("Gemini could not be reached or took too long");
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "The AI service took too long to answer");
        } catch (RestClientException exception) {
            log.warn("Gemini call failed");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI service is having problems. Please try again shortly");
        }
        return parse(response);
    }

    private String endpoint() {
        String base = config.baseUrl().endsWith("/") ? config.baseUrl().substring(0, config.baseUrl().length() - 1) : config.baseUrl();
        String model = config.model().trim();
        if (model.startsWith("models/")) model = model.substring("models/".length());
        return base + "/v1beta/models/" + model + ":generateContent";
    }

    private ObjectNode request(String systemPrompt, List<LlmMessage> conversation, List<ToolDefinition> tools) {
        ObjectNode root = mapper.createObjectNode();
        root.putObject("systemInstruction").putArray("parts").addObject().put("text", systemPrompt);
        ArrayNode contents = root.putArray("contents");
        for (LlmMessage message : conversation) {
            switch (message) {
                case LlmMessage.User user -> turn(contents, "user").addObject().put("text", user.text());
                case LlmMessage.Model model -> turn(contents, "model").addObject().put("text", model.text());
                case LlmMessage.ToolRequest request -> toolRequest(turn(contents, "model"), request);
                case LlmMessage.ToolResponse response -> toolResponse(turn(contents, "user"), response);
            }
        }
        if (!tools.isEmpty()) {
            ArrayNode declarations = root.putArray("tools").addObject().putArray("functionDeclarations");
            for (ToolDefinition tool : tools) {
                ObjectNode declaration = declarations.addObject();
                declaration.put("name", tool.name());
                declaration.put("description", tool.description());
                if (tool.inputSchema() != null && !tool.inputSchema().isEmpty()) {
                    declaration.set("parameters", mapper.valueToTree(cleanSchema(tool.inputSchema())));
                }
            }
        }
        ObjectNode generation = root.putObject("generationConfig");
        generation.put("temperature", 0.2);
        generation.put("maxOutputTokens", 2048);
        return root;
    }

    private static ArrayNode turn(ArrayNode contents, String role) {
        ObjectNode turn = contents.addObject();
        turn.put("role", role);
        return turn.putArray("parts");
    }

    private void toolRequest(ArrayNode parts, LlmMessage.ToolRequest request) {
        if (request.providerState() instanceof JsonNode raw && raw.isArray()) {
            parts.addAll((ArrayNode) raw);
            return;
        }
        for (ToolCall call : request.calls()) {
            ObjectNode functionCall = parts.addObject().putObject("functionCall");
            functionCall.put("name", call.name());
            functionCall.set("args", mapper.valueToTree(call.arguments()));
        }
    }

    private void toolResponse(ArrayNode parts, LlmMessage.ToolResponse response) {
        for (ToolResult result : response.results()) {
            ObjectNode functionResponse = parts.addObject().putObject("functionResponse");
            functionResponse.put("name", result.name());
            ObjectNode body = functionResponse.putObject("response");
            try {
                body.set("result", mapper.readTree(result.content()));
            } catch (JsonProcessingException exception) {
                body.put("result", result.content());
            }
        }
    }

    private LlmReply parse(JsonNode response) {
        JsonNode candidate = response == null ? null : response.path("candidates").path(0);
        JsonNode parts = candidate == null ? null : candidate.path("content").path("parts");
        List<ToolCall> calls = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        if (parts != null && parts.isArray()) {
            for (JsonNode part : parts) {
                if (part.has("functionCall")) {
                    JsonNode call = part.get("functionCall");
                    Map<String, Object> arguments = call.has("args")
                            ? mapper.convertValue(call.get("args"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { })
                            : Map.of();
                    calls.add(new ToolCall(call.path("name").asText(), arguments));
                } else if (part.has("text") && !part.path("thought").asBoolean(false)) {
                    text.append(part.get("text").asText());
                }
            }
        }
        if (!calls.isEmpty()) {
            return new LlmReply(text.toString().trim(), calls, parts);
        }
        String answer = text.toString().trim();
        if (answer.isEmpty() && blocked(response, candidate)) {
            return LlmReply.text(BLOCKED);
        }
        return LlmReply.text(answer);
    }

    private static boolean blocked(JsonNode response, JsonNode candidate) {
        boolean promptBlocked = response != null && response.path("promptFeedback").has("blockReason");
        String finish = candidate == null ? "" : candidate.path("finishReason").asText("");
        return promptBlocked || Set.of("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION").contains(finish);
    }

    private ResponseStatusException failure(int status) {
        log.warn("Gemini answered {}", status);
        return switch (status) {
            case 429 -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI model is busy right now (usage limit reached). Please try again in a minute");
            case 400, 401, 403, 404 -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The AI service could not process the request");
            default -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The AI service is having problems. Please try again shortly");
        };
    }

    /** Keeps only the schema keywords Gemini accepts and writes types in the lower case it expects. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> cleanSchema(Map<String, Object> schema) {
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            if (!SCHEMA_KEYS.contains(entry.getKey()) || entry.getValue() == null) continue;
            switch (entry.getKey()) {
                case "properties" -> {
                    Map<String, Object> properties = new LinkedHashMap<>();
                    ((Map<String, Object>) entry.getValue()).forEach((name, child) ->
                            properties.put(name, cleanSchema((Map<String, Object>) child)));
                    clean.put("properties", properties);
                }
                case "items" -> clean.put("items", cleanSchema((Map<String, Object>) entry.getValue()));
                case "type" -> clean.put("type", entry.getValue().toString().toLowerCase(Locale.ROOT));
                default -> clean.put(entry.getKey(), entry.getValue());
            }
        }
        return clean;
    }
}
