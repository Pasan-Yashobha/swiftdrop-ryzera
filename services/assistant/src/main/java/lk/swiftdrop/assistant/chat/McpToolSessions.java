package lk.swiftdrop.assistant.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lk.swiftdrop.assistant.config.AssistantProperties;
import lk.swiftdrop.assistant.llm.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Connects to this service's own MCP endpoint over HTTP, as any MCP client would, sending the user's token so that
 * every tool runs with that user's rights.
 */
@Component
public class McpToolSessions implements ToolSessions {
    private static final Logger log = LoggerFactory.getLogger(McpToolSessions.class);
    static final String TOOL_FAILED = "{\"error\":\"The tool could not be run right now.\"}";

    private final AssistantProperties.Chat chat;
    private final ObjectMapper mapper;
    private final Environment environment;

    public McpToolSessions(AssistantProperties properties, ObjectMapper mapper, Environment environment) {
        this.chat = properties.chat();
        this.mapper = mapper;
        this.environment = environment;
    }

    /**
     * The MCP endpoint is this same service. Unless a URL is configured, use the port the web server really bound
     * to, which stays right when the port is chosen at start-up.
     */
    private String baseUrl() {
        if (chat.mcpUrl() != null && !chat.mcpUrl().isBlank()) {
            return chat.mcpUrl();
        }
        return "http://localhost:" + environment.getProperty("local.server.port",
                environment.getProperty("server.port", "8080"));
    }

    @Override
    public ToolSession open(String authorization) {
        var transport = HttpClientStreamableHttpTransport.builder(baseUrl()).endpoint("/mcp")
                .customizeRequest(request -> {
                    if (authorization != null) request.header("Authorization", authorization);
                }).build();
        McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15))
                .initializationTimeout(Duration.ofSeconds(10)).build();
        try {
            client.initialize();
            List<ToolDefinition> tools = client.listTools().tools().stream().map(this::definition).toList();
            return new Session(client, tools);
        } catch (RuntimeException exception) {
            client.close();
            log.warn("Could not open the MCP tool session");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The assistant tools are not available right now");
        }
    }

    @SuppressWarnings("unchecked")
    private ToolDefinition definition(McpSchema.Tool tool) {
        Map<String, Object> schema = tool.inputSchema() == null ? Map.of() : mapper.convertValue(tool.inputSchema(), Map.class);
        return new ToolDefinition(tool.name(), tool.description(), new LinkedHashMap<>(schema));
    }

    private final class Session implements ToolSession {
        private final McpSyncClient client;
        private final List<ToolDefinition> tools;

        Session(McpSyncClient client, List<ToolDefinition> tools) {
            this.client = client;
            this.tools = tools;
        }

        @Override
        public List<ToolDefinition> tools() { return tools; }

        @Override
        public String call(String name, Map<String, Object> arguments) {
            if (tools.stream().noneMatch(tool -> tool.name().equals(name))) {
                return "{\"error\":\"That tool does not exist.\"}";
            }
            try {
                McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(name, arguments));
                if (result.content() == null || result.content().isEmpty()
                        || !(result.content().get(0) instanceof McpSchema.TextContent text)) {
                    return TOOL_FAILED;
                }
                return Boolean.TRUE.equals(result.isError())
                        ? mapper.createObjectNode().put("error", text.text()).toString() : text.text();
            } catch (RuntimeException exception) {
                log.warn("MCP tool call failed");
                return TOOL_FAILED;
            }
        }

        @Override
        public void close() { client.closeGracefully(); }
    }
}
