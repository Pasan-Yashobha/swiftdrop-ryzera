package lk.swiftdrop.assistant.llm;

import java.util.List;

/** The model's answer: final text, or tool calls it wants run first (then text is usually empty). */
public record LlmReply(String text, List<ToolCall> toolCalls, Object providerState) {
    public static LlmReply text(String text) {
        return new LlmReply(text, List.of(), null);
    }
}
