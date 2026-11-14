package lk.swiftdrop.assistant.llm;

import java.util.Map;

/** The model asked for this tool to be run with these arguments. */
public record ToolCall(String name, Map<String, Object> arguments) { }
