package lk.swiftdrop.merchant.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies parcel events to merchant orders (CONTRACT section 7). A whole event is handled in one transaction, so a
 * failure leaves no trace and the broker may redeliver it.
 *
 * <p>The rules, in order: a body that is not an event envelope is rejected ({@link InvalidEventException});
 * events without a merchant are ignored; an event id that was handled before is ignored (the id is recorded first,
 * with an insert that does not fail on a duplicate); the order is found by tracking number or, failing that, by
 * parcel id, always within the merchant of the event; unknown orders and sandbox orders are ignored. A status
 * event is written to the order's history, and moves the order only if it is newer than the last event applied, so
 * a late delivery never rolls an order back.
 */
@Service
@Transactional
public class ParcelEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ParcelEventHandler.class);

    private static final int MAX_EVENT_ID_LENGTH = 64;
    private static final int MAX_EVENT_TYPE_LENGTH = 100;
    private static final int MAX_FAILURE_REASON_LENGTH = 300;
    private static final int MAX_PARCEL_ID_LENGTH = 64;

    /** The validated parts of an event envelope. */
    private record ParcelEvent(String eventId, String eventType, Instant occurredAt, JsonNode data) {
    }

    private final MerchantOrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final ProcessedEventRepository processedEvents;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    ParcelEventHandler(MerchantOrderRepository orders, OrderStatusHistoryRepository history,
                       ProcessedEventRepository processedEvents, ObjectMapper objectMapper, Clock clock) {
        this.orders = orders;
        this.history = history;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * @param body the raw message body
     * @throws InvalidEventException when the body is not JSON or lacks {@code eventId}, {@code eventType} or
     *                               {@code occurredAt}
     */
    public void handle(byte[] body) {
        ParcelEvent event = parse(body);
        Optional<MerchantOrder> order = findOrder(event.data()).filter(o -> !o.isSandbox());
        if (order.isEmpty()) {
            log.debug("Event {} ({}) matches no live order, ignored", event.eventId(), event.eventType());
            return;
        }
        // Only events that concern an order are recorded: the table would otherwise grow with every parcel event
        // of every merchant on the platform.
        if (processedEvents.insertIfAbsent(event.eventId(), event.eventType(), clock.instant()) == 0) {
            log.debug("Event {} was handled before, ignored", event.eventId());
            return;
        }
        apply(order.get(), event);
    }

    private ParcelEvent parse(byte[] body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException notJson) {
            throw new InvalidEventException("The message is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            throw new InvalidEventException("The message is not a JSON object");
        }
        String eventId = text(root, "eventId");
        String eventType = text(root, "eventType");
        String occurredAt = text(root, "occurredAt");
        if (eventId == null || eventId.length() > MAX_EVENT_ID_LENGTH
                || eventType == null || eventType.length() > MAX_EVENT_TYPE_LENGTH || occurredAt == null) {
            throw new InvalidEventException("The event has no usable eventId, eventType or occurredAt");
        }
        try {
            return new ParcelEvent(eventId, eventType, Instant.parse(occurredAt), root.path("data"));
        } catch (DateTimeParseException badTimestamp) {
            throw new InvalidEventException("The event has an invalid occurredAt");
        }
    }

    private static Optional<UUID> merchantIdOf(JsonNode data) {
        String merchantId = text(data, "merchantId");
        if (merchantId == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(merchantId));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    /**
     * Finds the order an event is about. Events that name the merchant are looked up within that merchant. The
     * dispatch events ({@code parcel.assigned}, {@code parcel.cancelled}) carry only the parcel id, which the
     * parcel service assigned and which belongs to one order.
     */
    private Optional<MerchantOrder> findOrder(JsonNode data) {
        String trackingNo = text(data, "trackingNo");
        String parcelId = text(data, "parcelId");
        Optional<UUID> merchantId = merchantIdOf(data);
        if (merchantId.isEmpty()) {
            return parcelId == null ? Optional.empty() : orders.findFirstByParcelId(parcelId);
        }
        Optional<MerchantOrder> byTrackingNo = trackingNo == null ? Optional.empty()
                : orders.findByMerchantIdAndTrackingNo(merchantId.get(), trackingNo);
        return byTrackingNo.or(() -> parcelId == null ? Optional.empty()
                : orders.findByMerchantIdAndParcelId(merchantId.get(), parcelId));
    }

    private void apply(MerchantOrder order, ParcelEvent event) {
        if (ParcelEventTypes.CREATED.equals(event.eventType())) {
            String parcelId = text(event.data(), "parcelId");
            if (parcelId != null && parcelId.length() <= MAX_PARCEL_ID_LENGTH) {
                order.backfillParcelId(parcelId, clock.instant());
            }
            return;
        }
        ParcelEventTypes.statusFor(event.eventType()).ifPresentOrElse(
                status -> moveOrder(order, event, status),
                () -> log.debug("Event {} ({}) does not change an order, ignored", event.eventId(), event.eventType()));
    }

    private void moveOrder(MerchantOrder order, ParcelEvent event, OrderStatus status) {
        String failureReason = truncate(text(event.data(), "failureReason"));
        boolean newest = order.isNewerThanLastEvent(event.occurredAt());
        OrderStatus from = newest ? order.getStatus() : null;
        history.save(OrderStatusHistory.record(order.getId(), event.eventId(), from, status, event.occurredAt(),
                failureReason));
        if (newest) {
            order.moveTo(status, failureReason, event.occurredAt(), clock.instant());
        }
        log.info("Event {} ({}) {} order {}", event.eventId(), event.eventType(),
                newest ? "moved" : "was recorded late for", order.getId());
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_FAILURE_REASON_LENGTH
                ? value : value.substring(0, MAX_FAILURE_REASON_LENGTH);
    }

    /** The trimmed text of a string field, null when it is absent, not a string or blank. */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }
}
