package lk.swiftdrop.assistant.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.swiftdrop.assistant.config.AssistantProperties;
import lk.swiftdrop.assistant.support.FakeDownstream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

class McpToolSessionsTest {
    @Test
    void aToolServerThatCannotBeReachedGivesAClear503() {
        String dead;
        try (var server = new FakeDownstream()) {
            dead = server.url();
        }
        var properties = new AssistantProperties(new AssistantProperties.Gemini(null, null, "u", 5),
                new AssistantProperties.Downstream("http://p", null, "/t", null, "/h"),
                new AssistantProperties.Chat(dead, 100, 4, 10, 10, 30));
        assertThatThrownBy(() -> new McpToolSessions(properties, new ObjectMapper(), new MockEnvironment()).open("Bearer x"))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode().value()).isEqualTo(503);
                    assertThat(error.getReason()).contains("tools are not available");
                });
    }
}
