package lk.swiftdrop.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/** Everything configurable about the assistant; see application.yml for the environment variables. */
@ConfigurationProperties("assistant")
public record AssistantProperties(@DefaultValue Gemini gemini, @DefaultValue Downstream downstream,
                                  @DefaultValue Chat chat) {

    public record Gemini(String apiKey, String model,
                         @DefaultValue("https://generativelanguage.googleapis.com") String baseUrl,
                         @DefaultValue("30") int timeoutSeconds) {
        /** The chat endpoint answers 503 until both a key and a model name are set. */
        public boolean configured() {
            return StringUtils.hasText(apiKey) && StringUtils.hasText(model);
        }
    }

    /**
     * Downstream services. A blank tracking or parcel URL means the real service is not connected yet,
     * and the tool returns clearly labelled sample data instead.
     */
    public record Downstream(@DefaultValue("http://localhost:8086") String pricingUrl, String trackingUrl,
                             @DefaultValue("/api/v1/tracking/{trackingNumber}") String trackingPath,
                             String parcelUrl,
                             @DefaultValue("/api/v1/parcels/history?limit={limit}") String historyPath) { }

    /** {@code mcpUrl} is optional: blank means "this service, on the port it is really listening on". */
    public record Chat(String mcpUrl,
                       @DefaultValue("1000") int maxMessageChars,
                       @DefaultValue("4") int maxToolRounds,
                       @DefaultValue("10") int maxHistoryTurns,
                       @DefaultValue("10") int messagesPerMinute,
                       @DefaultValue("30") int conversationTtlMinutes) { }
}
