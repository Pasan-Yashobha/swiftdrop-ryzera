package lk.swiftdrop.assistant.llm;

import java.util.Map;

/** A tool the model may call, described the same way for every model; {@code inputSchema} is JSON Schema. */
public record ToolDefinition(String name, String description, Map<String, Object> inputSchema) { }
