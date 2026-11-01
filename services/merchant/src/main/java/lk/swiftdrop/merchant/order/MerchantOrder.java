package lk.swiftdrop.merchant.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lk.swiftdrop.merchant.order.dto.CreateOrderRequest;
import lk.swiftdrop.merchant.order.dto.Money;

/**
 * A parcel that a merchant asked SwiftDrop to deliver. The row is first inserted as a {@code PENDING} placeholder
 * so that its unique idempotency key and external reference arbitrate concurrent duplicates, and completed once
 * the parcel is booked. Later status changes arrive as parcel events (live orders) or sandbox advances.
 */
@Entity
@Table(name = "merchant_orders")
public class MerchantOrder {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "external_ref", updatable = false)
    private String externalRef;

    @Column(name = "idempotency_key", updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", updatable = false)
    private String requestHash;

    @Column(name = "parcel_id")
    private String parcelId;

    @Column(name = "tracking_no")
    private String trackingNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, updatable = false)
    private boolean sandbox;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OrderSource source;

    @Column(name = "recipient_name", nullable = false, updatable = false)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, updatable = false)
    private String recipientPhone;

    @Column(name = "pickup_address", nullable = false, updatable = false)
    private String pickupAddress;

    @Column(name = "pickup_city", nullable = false, updatable = false)
    private String pickupCity;

    @Column(name = "pickup_postal", updatable = false)
    private String pickupPostal;

    @Column(name = "dropoff_address", nullable = false, updatable = false)
    private String dropoffAddress;

    @Column(name = "dropoff_city", nullable = false, updatable = false)
    private String dropoffCity;

    @Column(name = "dropoff_postal", updatable = false)
    private String dropoffPostal;

    @Column(name = "weight_kg", nullable = false, updatable = false)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "size", nullable = false, updatable = false)
    private ParcelSize parcelSize;

    @Column(updatable = false)
    private String description;

    @Column(updatable = false)
    private String notes;

    @Column(name = "fee_amount")
    private BigDecimal feeAmount;

    @Column(name = "fee_currency")
    private String feeCurrency;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "last_event_at")
    private Instant lastEventAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Wrapper type on purpose: a null version tells Spring Data that the entity is new (plain INSERT). */
    @Version
    private Long version;

    protected MerchantOrder() {
        // for JPA
    }

    /** A {@code PENDING} row that reserves the idempotency key and external reference before the parcel is booked. */
    public static MerchantOrder placeholder(UUID id, UUID merchantId, CreateOrderRequest request, OrderSource source,
                                            boolean sandbox, String idempotencyKey, String requestHash, Instant now) {
        MerchantOrder order = new MerchantOrder();
        order.id = id;
        order.merchantId = merchantId;
        order.externalRef = request.externalRef();
        order.idempotencyKey = idempotencyKey;
        order.requestHash = requestHash;
        order.status = OrderStatus.PENDING;
        order.sandbox = sandbox;
        order.source = source;
        order.recipientName = request.recipient().name();
        order.recipientPhone = request.recipient().phone();
        order.pickupAddress = request.pickup().addressLine();
        order.pickupCity = request.pickup().city();
        order.pickupPostal = request.pickup().postalCode();
        order.dropoffAddress = request.dropoff().addressLine();
        order.dropoffCity = request.dropoff().city();
        order.dropoffPostal = request.dropoff().postalCode();
        order.weightKg = request.parcel().weightKg();
        order.parcelSize = request.parcel().size();
        order.description = request.parcel().description();
        order.notes = request.notes();
        order.createdAt = stamp(now);
        order.updatedAt = order.createdAt;
        return order;
    }

    /** The parcel is booked: the order gets its tracking number and fee and becomes {@code CREATED}. */
    public void complete(String parcelId, String trackingNo, Money fee, Instant now) {
        this.parcelId = parcelId;
        this.trackingNo = trackingNo;
        this.feeAmount = fee.amount();
        this.feeCurrency = fee.currency();
        this.status = OrderStatus.CREATED;
        this.updatedAt = stamp(now);
    }

    /** True when an event that happened at {@code occurredAt} is not older than everything applied so far. */
    public boolean isNewerThanLastEvent(Instant occurredAt) {
        // Not strictly after: two transitions stamped within the same instant (the timestamps have second
        // precision) must both apply; a repeated event is dropped by its event id before it gets here.
        return lastEventAt == null || !stamp(occurredAt).isBefore(lastEventAt);
    }

    /** Applies a status change that happened at {@code occurredAt}; a missing failure reason clears the old one. */
    public void moveTo(OrderStatus newStatus, String newFailureReason, Instant occurredAt, Instant now) {
        this.status = newStatus;
        this.failureReason = newFailureReason;
        this.lastEventAt = occurredAt == null ? null : stamp(occurredAt);
        this.updatedAt = stamp(now);
    }

    /** Fills in the parcel id of an order that was matched by its tracking number. */
    public void backfillParcelId(String id, Instant now) {
        if (parcelId == null) {
            parcelId = id;
            updatedAt = stamp(now);
        }
    }

    /** The database stores microseconds; keeping the same precision in memory makes a stored order read back equal. */
    private static Instant stamp(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getParcelId() {
        return parcelId;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public boolean isSandbox() {
        return sandbox;
    }

    public OrderSource getSource() {
        return source;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public String getPickupAddress() {
        return pickupAddress;
    }

    public String getPickupCity() {
        return pickupCity;
    }

    public String getPickupPostal() {
        return pickupPostal;
    }

    public String getDropoffAddress() {
        return dropoffAddress;
    }

    public String getDropoffCity() {
        return dropoffCity;
    }

    public String getDropoffPostal() {
        return dropoffPostal;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public ParcelSize getParcelSize() {
        return parcelSize;
    }

    public String getDescription() {
        return description;
    }

    public String getNotes() {
        return notes;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public String getFeeCurrency() {
        return feeCurrency;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
