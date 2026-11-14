package com.swiftdrop.reporting.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.swiftdrop.reporting.common.CorrelationIdFilter;
import com.swiftdrop.reporting.common.ParcelFactNotFoundException;
import com.swiftdrop.reporting.dto.EventEnvelope;
import com.swiftdrop.reporting.service.EventHandlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RabbitMQ consumer for reporting.q.
 *
 * Implements:
 * 1. Envelope validation: invalid JSON or missing fields -> reject without requeue (to DLQ).
 * 2. Correlation ID tracking in MDC.
 * 3. Delegation to EventHandlerService (runs in one DB transaction).
 * 4. Manual ack / nack / dead-letter handling.
 * 5. Bounded retry (max 3 times) for status events arriving before parcel_fact exists.
 */
@Component
public class ReportingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReportingEventConsumer.class);
    private static final int MAX_RETRIES = 3;

    private final ObjectMapper objectMapper;
    private final EventHandlerService eventHandlerService;
    private final Map<UUID, Integer> retryCounts = new ConcurrentHashMap<>();

    public ReportingEventConsumer(ObjectMapper objectMapper, EventHandlerService eventHandlerService) {
        this.objectMapper = objectMapper;
        this.eventHandlerService = eventHandlerService;
    }

    @RabbitListener(queues = "${reporting.queues.main:reporting.q}", ackMode = "MANUAL")
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        EventEnvelope envelope;

        // 1. Validate envelope & parse JSON
        try {
            envelope = objectMapper.readValue(message.getBody(), EventEnvelope.class);
            if (envelope == null || !envelope.isValid()) {
                log.warn("Invalid event envelope received (missing required fields), rejecting without requeue to DLQ");
                channel.basicReject(deliveryTag, false);
                return;
            }
        } catch (Exception ex) {
            log.warn("Failed to parse event message JSON, rejecting without requeue to DLQ: {}", ex.getMessage());
            channel.basicReject(deliveryTag, false);
            return;
        }

        // Propagate correlationId to MDC
        String correlationId = envelope.correlationId();
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, correlationId);
        } else {
            MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, UUID.randomUUID().toString());
        }

        try {
            // 2-5. Transactional processing in one DB transaction
            eventHandlerService.handle(envelope);

            // Clean up any retry record
            retryCounts.remove(envelope.eventId());

            // 6. Commit & Ack
            channel.basicAck(deliveryTag, false);
        } catch (ParcelFactNotFoundException ex) {
            int retries = retryCounts.getOrDefault(envelope.eventId(), 0);
            if (retries < MAX_RETRIES) {
                retryCounts.put(envelope.eventId(), retries + 1);
                log.warn("ParcelFact missing for event {} (retry {}/{}), requeuing for delayed retry",
                        envelope.eventId(), retries + 1, MAX_RETRIES);
                channel.basicNack(deliveryTag, false, true);
            } else {
                log.error("ParcelFact still missing after {} retries for event {}, dead-lettering",
                        MAX_RETRIES, envelope.eventId());
                retryCounts.remove(envelope.eventId());
                channel.basicReject(deliveryTag, false);
            }
        } catch (Exception ex) {
            log.error("Error processing event {}, requeuing: {}", envelope.eventId(), ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, true);
        } finally {
            MDC.remove(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        }
    }

    public Map<UUID, Integer> getRetryCounts() {
        return retryCounts;
    }
}
