package lk.swiftdrop.assistant.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.Map;

/** A real MCP client for tests: it talks to the running service over HTTP, like Claude Desktop or the chat loop would. */
public final class McpTestClient implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final McpSyncClient client;

    public McpTestClient(int port, String authorization) {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port).endpoint("/mcp")
                .customizeRequest(request -> {
                    if (authorization != null) request.header("Authorization", authorization);
                }).build();
        this.client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15))
                .initializationTimeout(Duration.ofSeconds(15)).build();
        client.initialize();
    }

    public McpSchema.ListToolsResult tools() { return client.listTools(); }

    /** Calls a tool and returns its JSON answer. */
    public JsonNode call(String tool, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(tool, arguments));
        var first = result.content().get(0);
        try {
            return JSON.readTree(((McpSchema.TextContent) first).text());
        } catch (Exception exception) {
            throw new IllegalStateException("Tool answer was not JSON: " + first, exception);
        }
    }

    @Override
    public void close() { client.closeGracefully(); }
}
