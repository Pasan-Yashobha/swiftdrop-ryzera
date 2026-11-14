package lk.swiftdrop.assistant.chat;

import java.util.List;
import java.util.Map;
import lk.swiftdrop.assistant.llm.ToolDefinition;

/** One signed-in user's connection to the MCP tools for the length of one chat message. */
public interface ToolSession extends AutoCloseable {
    List<ToolDefinition> tools();

    /** Runs a tool and returns its JSON answer. Never throws: a failure comes back as {@code {"error": ...}}. */
    String call(String name, Map<String, Object> arguments);

    @Override
    void close();
}
