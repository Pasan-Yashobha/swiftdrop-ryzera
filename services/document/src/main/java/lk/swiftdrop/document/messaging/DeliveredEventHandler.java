package lk.swiftdrop.document.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.document.config.DocumentProperties;
import lk.swiftdrop.document.receipt.ReceiptRequest;
import lk.swiftdrop.document.receipt.ReceiptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Handles one {@code parcel.status.delivered} message: checks the envelope, skips duplicates, and makes the receipt
 * together with its "processed" mark and its {@code receipt.generated} outbox row in ONE transaction.
 *
 * <p>Every failure is classified. {@link PermanentEventException} (bad JSON, missing field, an unknown parcel, data
 * that fails validation) is never retried and goes straight to the dead-letter queue. {@link TransientProcessingException}
 * (database or Parcel service down) is retried by the listener, and goes to the dead-letter queue after the last try.
 */
@Component
public class DeliveredEventHandler {
    private static final Logger log = LoggerFactory.getLogger(DeliveredEventHandler.class);
    static final String CONSUMER = "document-receipts";

    private final ObjectMapper mapper;
    private final ProcessedEvents processed;
    private final DeliveredParcelMapper parcels;
    private final ReceiptService receipts;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String deliveredType;

    public DeliveredEventHandler(ObjectMapper mapper, ProcessedEvents processed, DeliveredParcelMapper parcels,
                                 ReceiptService receipts, TransactionTemplate transaction, Clock clock,
                                 DocumentProperties properties) {
        this.mapper = mapper;
        this.processed = processed;
        this.parcels = parcels;
        this.receipts = receipts;
        this.transaction = transaction;
        this.clock = clock;
        this.deliveredType = properties.messaging().deliveredRoutingKey();
    }

    public void handle(String body) {
        EventEnvelope event = parse(body);
        if (!deliveredType.equals(event.eventType())) {
            log.info("Ignoring event {} of type {}", event.eventId(), event.eventType());
            return;
        }
        UUID eventId = UUID.fromString(event.eventId());
        if (processed.exists(eventId, CONSUMER)) {
            log.info("Event {} was already handled; skipping the duplicate", eventId);
            return;
        }
        try {
            Optional<ReceiptRequest> snapshot = parcels.toSnapshot(event);
            transaction.executeWithoutResult(status -> {
                if (!processed.markProcessed(eventId, CONSUMER, clock.instant())) return;
                snapshot.ifPresent(delivery -> receipts.generate(delivery, event.correlationId()));
            });
            log.info("Handled event {}", eventId);
        } catch (PermanentEventException | TransientProcessingException exception) {
            throw exception;
        } catch (ConstraintViolationException | MethodValidationException exception) {
            throw new PermanentEventException("The delivery data is not valid", exception);
        } catch (ResponseStatusException exception) {
            // for example 409: a different receipt already exists for this parcel. Retrying cannot fix that.
            if (exception.getStatusCode().is4xxClientError()) {
                throw new PermanentEventException("The receipt was refused: " + exception.getStatusCode().value(), exception);
            }
            throw new TransientProcessingException("The receipt could not be made", exception);
        } catch (DataAccessException exception) {
            throw new TransientProcessingException("The database is not available", exception);
        } catch (RuntimeException exception) {
            throw new TransientProcessingException("Unexpected failure while handling the event", exception);
        }
    }

    /** All envelope fields are mandatory, except that a missing correlationId is replaced by a new one. */
    EventEnvelope parse(String body) {
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new PermanentEventException("The message is not valid JSON", exception);
        }
        if (root == null || !root.isObject()) throw new PermanentEventException("The message is not a JSON object");
        String eventId = required(root, "eventId");
        try {
            UUID.fromString(eventId);
        } catch (IllegalArgumentException exception) {
            throw new PermanentEventException("eventId is not a UUID");
        }
        String type = required(root, "eventType");
        if (!root.path("version").canConvertToInt() || root.path("version").asInt() < 1) {
            throw new PermanentEventException("version is missing or invalid");
        }
        Instant occurredAt;
        try {
            occurredAt = Instant.parse(required(root, "occurredAt"));
        } catch (DateTimeParseException exception) {
            throw new PermanentEventException("occurredAt is not a timestamp");
        }
        String producer = required(root, "producer");
        String aggregateId = required(root, "aggregateId");
        JsonNode data = root.path("data");
        if (!data.isObject()) throw new PermanentEventException("data is missing");
        String correlationId = root.path("correlationId").isTextual() && !root.path("correlationId").asText().isBlank()
                ? root.path("correlationId").asText() : UUID.randomUUID().toString();
        return new EventEnvelope(eventId, type, root.path("version").asInt(), occurredAt, producer, correlationId, aggregateId, data);
    }

    private static String required(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isTextual() || value.asText().isBlank()) throw new PermanentEventException(field + " is missing");
        return value.asText().trim();
    }
}
