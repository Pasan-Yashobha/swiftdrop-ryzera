package lk.swiftdrop.notification.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.notification.event.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Handles one message from {@code notification.q}: validate the envelope, record it once (idempotent), then make the
 * first attempt for each channel. Channel failures are handled by the dispatcher and never propagate, so only
 * infrastructure failures (database down) reach the listener's retry policy.
 *
 * @see lk.swiftdrop.notification.event.InvalidEventException thrown for unusable messages (dead-lettered)
 */
@Component
public class NotificationEventHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventHandler.class);
    private static final String MDC_KEY = "correlationId";

    private final NotificationIntake intake;
    private final NotificationDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    public NotificationEventHandler(NotificationIntake intake, NotificationDispatcher dispatcher,
                                    ObjectMapper objectMapper) {
        this.intake = intake;
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
    }

    public void handle(byte[] body) {
        EventEnvelope event = EventEnvelope.parse(body, objectMapper);
        MDC.put(MDC_KEY, event.correlationId());
        try {
            List<UUID> due = intake.accept(event, new String(body, StandardCharsets.UTF_8));
            if (due.isEmpty()) {
                log.debug("Event {} ({}) needs no notification or was already handled", event.eventId(),
                        event.eventType());
            }
            due.forEach(dispatcher::attempt);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
