package lk.swiftdrop.assistant.tools;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * Spring AI builds the MCP web transport without passing HTTP headers to the tools. This replaces that one bean so
 * the caller's {@code Authorization} header reaches each tool, which then forwards it to the real services.
 * Spring Security has already verified the token before the request gets here.
 */
@Configuration
public class McpAuth {
    /** Key of the bearer token in the MCP transport context. */
    public static final String KEY = "authorization";

    static final McpTransportContextExtractor<ServerRequest> EXTRACTOR = request -> {
        String header = request.headers().firstHeader("Authorization");
        return header == null ? McpTransportContext.EMPTY : McpTransportContext.create(Map.of(KEY, header));
    };

    @Bean
    WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
            ObjectMapper objectMapper, McpServerStreamableHttpProperties properties) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .mcpEndpoint(properties.getMcpEndpoint())
                .disallowDelete(properties.isDisallowDelete())
                .keepAliveInterval(properties.getKeepAliveInterval())
                .contextExtractor(EXTRACTOR)
                .build();
    }
}
