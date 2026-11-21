package lk.swiftdrop.courier.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.swiftdrop.courier.common.CorrelationIdFilter;
import lk.swiftdrop.courier.config.MessagingConfig;
import lk.swiftdrop.courier.service.DeliveryService;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AssignmentEventConsumer {
    private final DeliveryService deliveryService;
    private final AssignmentEventDecoder decoder;

    public AssignmentEventConsumer(DeliveryService deliveryService, ObjectMapper objectMapper) {
        this.deliveryService = deliveryService;
        this.decoder = new AssignmentEventDecoder(objectMapper);
    }

    @RabbitListener(queues = "${swiftdrop.messaging.queue:courier.assignments.q}",
            containerFactory = MessagingConfig.LISTENER_FACTORY)
    public void onMessage(Message message) {
        var event = decoder.decode(message.getBody());
        String previousCorrelation = MDC.get(CorrelationIdFilter.MDC_KEY);
        MDC.put(CorrelationIdFilter.MDC_KEY, event.correlationId());
        try {
            if (event.eventType().equals("parcel.assigned")) {
                deliveryService.handleAssignment(event.eventId(), event.parcelId(), event.courierId(),
                        event.trackingNo(), event.occurredAt());
            } else {
                deliveryService.handleReassignment(event.eventId(), event.parcelId(), event.oldCourierId(),
                        event.courierId(), event.reason(), event.occurredAt());
            }
        } finally {
            if (previousCorrelation == null) {
                MDC.remove(CorrelationIdFilter.MDC_KEY);
            } else {
                MDC.put(CorrelationIdFilter.MDC_KEY, previousCorrelation);
            }
        }
    }
}
