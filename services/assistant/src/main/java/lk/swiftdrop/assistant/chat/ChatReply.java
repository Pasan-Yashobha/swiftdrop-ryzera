package lk.swiftdrop.assistant.chat;

import java.util.List;
import java.util.Map;

/**
 * What the chat widget receives. {@code toolCalls} lists the MCP tools the assistant used (never the user's token),
 * so the page can show how an answer was produced.
 */
public record ChatReply(String conversationId, String reply, List<ToolTrace> toolCalls) {
    public record ToolTrace(String tool, Map<String, Object> arguments, boolean failed) { }
}
