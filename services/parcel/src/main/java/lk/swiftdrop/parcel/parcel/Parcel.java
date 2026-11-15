package lk.swiftdrop.parcel.parcel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.parcel.pricing.PricingSnapshot;

@Entity
@Table(name = "parcels")
public class Parcel {

    @Id
    private UUID id;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 40)
    private String trackingNumber;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "recipient_name", nullable = false, length = 120)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 32)
    private String recipientPhone;

    @Column(name = "recipient_email", length = 254)
    private String recipientEmail;

    @Column(name = "pickup_address_line", nullable = false, length = 300)
    private String pickupAddressLine;

    @Column(name = "pickup_city", nullable = false, length = 100)
    private String pickupCity;

    @Column(name = "pickup_postal_code", length = 16)
    private String pickupPostalCode;

    @Column(name = "dropoff_address_line", nullable = false, length = 300)
    private String dropoffAddressLine;

    @Column(name = "dropoff_city", nullable = false, length = 100)
    private String dropoffCity;

    @Column(name = "dropoff_postal_code", length = 16)
    private String dropoffPostalCode;

    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ParcelSize size;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false, length = 16)
    private DeliveryType deliveryType;

    @Column(length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ParcelStatus status;

    @Column(name = "quote_id")
    private UUID quoteId;

    @Column(name = "base_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseAmount;

    @Column(name = "weight_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal weightAmount;

    @Column(name = "size_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal sizeAmount;

    @Column(name = "express_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal expressAmount;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "total_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalFee;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "courier_id", length = 64)
    private String courierId;

    @Column(name = "proof_recipient_name", length = 120)
    private String proofRecipientName;

    @Column(name = "proof_photo_key", length = 255)
    private String proofPhotoKey;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Parcel() {
    }

    public static Parcel create(
            UUID id,
            String trackingNumber,
            String customerId,
            UUID merchantId,
            String recipientName,
            String recipientPhone,
            String recipientEmail,
            String pickupAddressLine,
            String pickupCity,
            String pickupPostalCode,
            String dropoffAddressLine,
            String dropoffCity,
            String dropoffPostalCode,
            BigDecimal weightKg,
            ParcelSize size,
            String description,
            DeliveryType deliveryType,
            String notes,
            PricingSnapshot pricing,
            Instant now) {
        Parcel p = new Parcel();
        p.id = id;
        p.trackingNumber = trackingNumber;
        p.customerId = customerId;
        p.merchantId = merchantId;
        p.recipientName = recipientName;
        p.recipientPhone = recipientPhone;
        p.recipientEmail = recipientEmail;
        p.pickupAddressLine = pickupAddressLine;
        p.pickupCity = pickupCity;
        p.pickupPostalCode = pickupPostalCode;
        p.dropoffAddressLine = dropoffAddressLine;
        p.dropoffCity = dropoffCity;
        p.dropoffPostalCode = dropoffPostalCode;
        p.weightKg = weightKg;
        p.size = size;
        p.description = description;
        p.deliveryType = deliveryType != null ? deliveryType : DeliveryType.STANDARD;
        p.notes = notes;
        p.status = ParcelStatus.CREATED;

        if (pricing != null) {
            p.quoteId = pricing.quoteId();
            p.baseAmount = pricing.baseAmount();
            p.weightAmount = pricing.weightAmount();
            p.sizeAmount = pricing.sizeAmount();
            p.expressAmount = pricing.expressAmount();
            p.discountAmount = pricing.discountAmount();
            p.totalFee = pricing.totalAmount();
            p.currency = pricing.currency();
        } else {
            p.baseAmount = BigDecimal.ZERO;
            p.weightAmount = BigDecimal.ZERO;
            p.sizeAmount = BigDecimal.ZERO;
            p.expressAmount = BigDecimal.ZERO;
            p.discountAmount = BigDecimal.ZERO;
            p.totalFee = BigDecimal.ZERO;
            p.currency = "LKR";
        }

        p.createdAt = now;
        p.updatedAt = now;
        p.version = 0L;
        return p;
    }

    public void updateStatus(ParcelStatus newStatus, String courierId, String recipientName,
                             String photoKey, String failureReason, Instant now) {
        this.status = newStatus;
        if (courierId != null && !courierId.isBlank()) {
            this.courierId = courierId;
        }
        if (recipientName != null && !recipientName.isBlank()) {
            this.proofRecipientName = recipientName;
        }
        if (photoKey != null && !photoKey.isBlank()) {
            this.proofPhotoKey = photoKey;
        }
        if (failureReason != null && !failureReason.isBlank()) {
            this.failureReason = failureReason;
        }
        if (newStatus == ParcelStatus.DELIVERED) {
            this.deliveredAt = now;
        }
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getTrackingNumber() { return trackingNumber; }
    public String getCustomerId() { return customerId; }
    public UUID getMerchantId() { return merchantId; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getPickupAddressLine() { return pickupAddressLine; }
    public String getPickupCity() { return pickupCity; }
    public String getPickupPostalCode() { return pickupPostalCode; }
    public String getDropoffAddressLine() { return dropoffAddressLine; }
    public String getDropoffCity() { return dropoffCity; }
    public String getDropoffPostalCode() { return dropoffPostalCode; }
    public BigDecimal getWeightKg() { return weightKg; }
    public ParcelSize getSize() { return size; }
    public String getDescription() { return description; }
    public DeliveryType getDeliveryType() { return deliveryType; }
    public String getNotes() { return notes; }
    public ParcelStatus getStatus() { return status; }
    public UUID getQuoteId() { return quoteId; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public BigDecimal getWeightAmount() { return weightAmount; }
    public BigDecimal getSizeAmount() { return sizeAmount; }
    public BigDecimal getExpressAmount() { return expressAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTotalFee() { return totalFee; }
    public String getCurrency() { return currency; }
    public String getCourierId() { return courierId; }
    public String getProofRecipientName() { return proofRecipientName; }
    public String getProofPhotoKey() { return proofPhotoKey; }
    public String getFailureReason() { return failureReason; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
