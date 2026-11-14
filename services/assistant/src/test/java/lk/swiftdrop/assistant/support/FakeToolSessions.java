package lk.swiftdrop.assistant.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lk.swiftdrop.assistant.chat.ToolSession;
import lk.swiftdrop.assistant.chat.ToolSessions;
import lk.swiftdrop.assistant.llm.ToolDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Tool sessions without MCP: answers come from a map, and every call and open/close is recorded. */
public final class FakeToolSessions implements ToolSessions {
    public record Call(String tool, Map<String, Object> arguments) { }

    public final List<String> authorizations = new ArrayList<>();
    public final List<Call> calls = new ArrayList<>();
    public final Map<String, String> answers = new HashMap<>();
    public int opened;
    public int closed;
    public boolean unavailable;

    public final List<ToolDefinition> definitions = List.of(
            new ToolDefinition("estimate_fee", "Estimate a fee", Map.of("type", "object")),
            new ToolDefinition("track_parcel", "Track a parcel", Map.of("type", "object")),
            new ToolDefinition("get_history", "Recent parcels", Map.of("type", "object")));

    @Override
    public ToolSession open(String authorization) {
        if (unavailable) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The assistant tools are not available right now");
        opened++;
        authorizations.add(authorization);
        return new ToolSession() {
            @Override
            public List<ToolDefinition> tools() { return definitions; }

            @Override
            public String call(String name, Map<String, Object> arguments) {
                calls.add(new Call(name, arguments));
                return answers.getOrDefault(name, "{\"error\":\"That tool does not exist.\"}");
            }

            @Override
            public void close() { closed++; }
        };
    }
}
