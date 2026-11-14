package lk.swiftdrop.assistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.swiftdrop.assistant.llm.GeminiClient;
import lk.swiftdrop.assistant.llm.LlmClient;
import lk.swiftdrop.assistant.llm.UnconfiguredLlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Picks the language model. To use another one, add an {@link LlmClient} implementation here; nothing else changes. */
@Configuration
public class LlmConfig {
    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    LlmClient llmClient(AssistantProperties properties, ObjectMapper mapper) {
        if (properties.gemini().configured()) {
            log.info("Using Gemini model {}", properties.gemini().model());
            return new GeminiClient(properties.gemini(), mapper);
        }
        log.warn("No Gemini API key or model set (GEMINI_API_KEY, GEMINI_MODEL): chat answers 503, the MCP tools still work");
        return new UnconfiguredLlmClient();
    }
}
