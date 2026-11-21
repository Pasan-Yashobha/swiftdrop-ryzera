package lk.swiftdrop.courier.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.courier.common.ApiException;
import lk.swiftdrop.courier.dto.LocationReportRequest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LocationEventPublisherTest {
    final RabbitTemplate template = mock(RabbitTemplate.class);
    final ObjectMapper mapper = new ObjectMapper();
    final LocationEventPublisher publisher = new LocationEventPublisher(template, mapper, "swiftdrop.events");
    final Instant now = Instant.parse("2026-11-18T00:00:00Z");
    final UUID id = UUID.randomUUID();
    final LocationReportRequest request = new LocationReportRequest(new BigDecimal("6.123456"), new BigDecimal("80.123456"), now);

    @Test void publishesObjectEnvelopeAndWaitsForAck() throws Exception {
        doAnswer(call -> {
            Message message = call.getArgument(2);
            var json = mapper.readTree(message.getBody());
            assertThat(json.isObject()).isTrue();
            assertThat(json.get("producer").asText()).isEqualTo("courier-service");
            assertThat(json.get("aggregateId").asText()).isEqualTo(id.toString());
            assertThat(json.get("version").asInt()).isEqualTo(1);
            assertThat(json.get("eventId").asText()).isNotBlank();
            assertThat(json.get("correlationId").asText()).isNotBlank();
            assertThat(json.at("/data/lat").decimalValue()).isEqualByComparingTo("6.1235");
            assertThat(json.at("/data/lng").decimalValue()).isEqualByComparingTo("80.1235");
            assertThat(json.at("/data/recordedAt").asText()).isEqualTo(now.toString());
            assertThat(json.at("/data/courierId").asText()).isEqualTo(id.toString());
            assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
            assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            ((CorrelationData) call.getArgument(3)).getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(eq("swiftdrop.events"), eq("courier.location.updated"), any(Message.class), any(CorrelationData.class));
        publisher.publish(id, request, now);
        verify(template).send(eq("swiftdrop.events"), eq("courier.location.updated"), any(Message.class), any(CorrelationData.class));
    }

    @Test void rejectsNack() {
        doAnswer(call -> { ((CorrelationData) call.getArgument(3)).getFuture().complete(new CorrelationData.Confirm(false, "nack")); return null; })
            .when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThatThrownBy(() -> publisher.publish(id, request, now)).isInstanceOf(ApiException.class);
    }
    @Test void rejectsUnroutableEvenWhenAcknowledged() {
        doAnswer(call -> {
            CorrelationData data = call.getArgument(3);
            data.setReturned(new ReturnedMessage(call.getArgument(2), 312, "NO_ROUTE", "swiftdrop.events", "courier.location.updated"));
            data.getFuture().complete(new CorrelationData.Confirm(true, null)); return null;
        }).when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThatThrownBy(() -> publisher.publish(id, request, now)).isInstanceOf(ApiException.class);
    }
    @Test void rejectsConfirmationTimeout() {
        assertThatThrownBy(() -> publisher.publish(id, request, now)).isInstanceOf(ApiException.class);
    }
    @Test void rejectsConnectionFailure() {
        doThrow(new org.springframework.amqp.AmqpConnectException(new java.net.ConnectException()))
            .when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThatThrownBy(() -> publisher.publish(id, request, now)).isInstanceOf(ApiException.class);
    }
}
