package lk.swiftdrop.assistant.chat;

/** Opens tool sessions. The real one speaks MCP over HTTP; tests use a fake. */
public interface ToolSessions {
    /** @param authorization the user's own {@code Authorization} header value, forwarded to every tool call */
    ToolSession open(String authorization);
}
