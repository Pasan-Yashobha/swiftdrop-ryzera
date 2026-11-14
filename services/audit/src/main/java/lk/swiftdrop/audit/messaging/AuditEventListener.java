package lk.swiftdrop.audit.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.swiftdrop.audit.config.RabbitConfig;
import lk.swiftdrop.audit.service.AuditRecorder;
import lk.swiftdrop.audit.service.EventMapper;
import lk.swiftdrop.audit.service.InvalidEventException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Reads audited events from audit.q. A message that is not a usable event goes straight to the dead-letter
 * queue; a temporary failure (for example the database is down) is retried, then dead-lettered.
 */
@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final ObjectMapper mapper;
    private final EventMapper eventMapper;
    private final AuditRecorder recorder;

    public AuditEventListener(ObjectMapper mapper, EventMapper eventMapper, AuditRecorder recorder) {
        this.mapper = mapper;
        this.eventMapper = eventMapper;
        this.recorder = recorder;
    }

    // The message is read as plain JSON on purpose: publishers add their own Java class name header,
    // which this service does not have.
    @RabbitListener(queues = "${swiftdrop.audit.events.queue:audit.q}", containerFactory = RabbitConfig.LISTENER_FACTORY)
    public void onMessage(Message message) {
        EventEnvelope envelope;
        try {
            envelope = mapper.readValue(message.getBody(), EventEnvelope.class);
        } catch (IOException e) {
            log.warn("Rejected a message that is not valid JSON");
            throw new AmqpRejectAndDontRequeueException("Malformed event", e);
        }
        try {
            recorder.record(eventMapper.map(envelope));
        } catch (InvalidEventException e) {
            log.warn("Rejected an unusable event: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
