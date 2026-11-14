package lk.swiftdrop.assistant.llm;

/** What a tool answered, as JSON text. It is data for the model to read, never instructions for it to follow. */
public record ToolResult(String name, String content) { }
