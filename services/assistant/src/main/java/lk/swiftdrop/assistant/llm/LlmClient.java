package lk.swiftdrop.assistant.llm;

import java.util.List;

/** Talks to the language model. Implementations: Gemini, "not configured", and scripted fakes in tests. */
public interface LlmClient {
    LlmReply generate(String systemPrompt, List<LlmMessage> conversation, List<ToolDefinition> tools);
}
