package lk.swiftdrop.dispatch.service;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SseEmitterServiceTest {

    private final SseEmitterService sseEmitterService = new SseEmitterService();

    @Test
    @DisplayName("Subscribe returns valid non-null SseEmitter and broadcast delivers without error")
    void subscribeAndBroadcast() {
        SseEmitter emitter = sseEmitterService.subscribe();
        assertThat(emitter).isNotNull();

        assertThatCode(() -> sseEmitterService.broadcast("parcel.assigned", Map.of("key", "val")))
                .doesNotThrowAnyException();
    }
}
