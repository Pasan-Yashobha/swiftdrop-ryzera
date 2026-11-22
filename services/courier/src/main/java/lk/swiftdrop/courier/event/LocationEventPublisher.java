package lk.swiftdrop.courier.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lk.swiftdrop.courier.common.*;
import lk.swiftdrop.courier.config.AppProperties;
import lk.swiftdrop.courier.dto.LocationReportRequest;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class LocationEventPublisher {
    private final RabbitTemplate template;
    private final ObjectMapper mapper;
    private final String exchange;

    @org.springframework.beans.factory.annotation.Autowired
    public LocationEventPublisher(ConnectionFactory connectionFactory, ObjectMapper mapper, AppProperties properties) {
        this.template = new RabbitTemplate(connectionFactory);
        this.template.setMandatory(true);
        this.mapper = mapper;
        this.exchange = properties.messaging() == null ? "swiftdrop.events" : properties.messaging().exchange();
    }

    LocationEventPublisher(RabbitTemplate template, ObjectMapper mapper, String exchange) {
        this.template = template; this.mapper = mapper; this.exchange = exchange;
    }

    public void publish(UUID courierId, LocationReportRequest request, Instant now) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> envelope = Map.of(
                "eventId", id, "eventType", "courier.location.updated", "version", 1,
                "occurredAt", now.toString(), "producer", "courier-service",
                "aggregateId", courierId.toString(), "correlationId", CorrelationIdFilter.currentOrNew(),
                "data", Map.of("courierId", courierId.toString(),
                        "lat", request.lat().setScale(4, RoundingMode.HALF_UP),
                        "lng", request.lng().setScale(4, RoundingMode.HALF_UP),
                        "recordedAt", request.recordedAt().toString()));
        try {
            MessageProperties headers = new MessageProperties();
            headers.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            headers.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            headers.setMessageId(id);
            headers.setCorrelationId((String) envelope.get("correlationId"));
            CorrelationData confirmation = new CorrelationData(id);
            template.send(exchange, "courier.location.updated", new Message(mapper.writeValueAsBytes(envelope), headers), confirmation);
            if (!confirmation.getFuture().get(3, TimeUnit.SECONDS).isAck() || confirmation.getReturned() != null) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Location report could not be confirmed");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Location report was interrupted");
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Location report could not be confirmed; retry later");
        }
    }
}
