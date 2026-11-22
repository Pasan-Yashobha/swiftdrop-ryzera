package lk.swiftdrop.assistant.llm;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Used when no API key or model name is set: the chat endpoint says so plainly instead of failing oddly. */
public class UnconfiguredLlmClient implements LlmClient {
    @Override
    public LlmReply generate(String systemPrompt, List<LlmMessage> conversation, List<ToolDefinition> tools) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "The AI model is not configured on this server yet");
    }
}
