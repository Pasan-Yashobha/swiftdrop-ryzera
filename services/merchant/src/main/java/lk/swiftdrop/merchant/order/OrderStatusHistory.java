package lk.swiftdrop.merchant.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** One entry of an order's timeline. Entries written for parcel events carry the event id (unique per order). */
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "event_id", updatable = false)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private OrderStatus toStatus;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(updatable = false)
    private String note;

    protected OrderStatusHistory() {
        // for JPA
    }

    public static OrderStatusHistory record(UUID orderId, String eventId, OrderStatus fromStatus,
                                            OrderStatus toStatus, Instant occurredAt, String note) {
        OrderStatusHistory entry = new OrderStatusHistory();
        entry.id = UUID.randomUUID();
        entry.orderId = orderId;
        entry.eventId = eventId;
        entry.fromStatus = fromStatus;
        entry.toStatus = toStatus;
        entry.occurredAt = occurredAt.truncatedTo(ChronoUnit.MICROS);
        entry.note = note;
        return entry;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getEventId() {
        return eventId;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getNote() {
        return note;
    }
}
