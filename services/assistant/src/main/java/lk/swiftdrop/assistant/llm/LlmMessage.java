package lk.swiftdrop.assistant.llm;

import java.util.List;

/**
 * One entry of a conversation, in a form that does not depend on the model. {@code providerState} on a tool request
 * is the model's own raw answer, kept so models that need it replayed unchanged (Gemini "thought signatures") get it.
 */
public sealed interface LlmMessage {
    record User(String text) implements LlmMessage { }

    record Model(String text) implements LlmMessage { }

    record ToolRequest(List<ToolCall> calls, Object providerState) implements LlmMessage { }

    record ToolResponse(List<ToolResult> results) implements LlmMessage { }
}
