package lk.swiftdrop.merchant.merchant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import lk.swiftdrop.merchant.common.ConflictException;
import lk.swiftdrop.merchant.common.ErrorCode;

/**
 * A business that sends parcels through SwiftDrop. The owner is the Identity user who registered it
 * ({@code owner_user_id}); all status changes go through the methods below, which enforce the transition
 * rules of CONTRACT section 5.2.
 */
@Entity
@Table(name = "merchants")
public class Merchant {

    public static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 60;

    @Id
    private UUID id;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private String ownerUserId;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "business_reg_no")
    private String businessRegNo;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false)
    private String contactPhone;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    @Column(nullable = false)
    private String city;

    private String website;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantStatus status;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "rate_limit_per_minute", nullable = false)
    private int rateLimitPerMinute;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Wrapper type on purpose: a null version tells Spring Data that the entity is new (plain INSERT). */
    @Version
    private Long version;

    protected Merchant() {
        // for JPA
    }

    /** A freshly registered merchant waits for approval. */
    public static Merchant register(UUID id, String ownerUserId, MerchantProfile profile, Instant now) {
        Merchant merchant = new Merchant();
        merchant.id = id;
        merchant.ownerUserId = ownerUserId;
        merchant.status = MerchantStatus.PENDING;
        merchant.rateLimitPerMinute = DEFAULT_RATE_LIMIT_PER_MINUTE;
        merchant.createdAt = now;
        merchant.applyProfile(profile, now);
        return merchant;
    }

    public void updateProfile(MerchantProfile profile, Instant now) {
        applyProfile(profile, now);
    }

    /** PENDING, REJECTED and SUSPENDED merchants can be approved. */
    public void approve(String adminId, Instant now) {
        requireStatus("approved", MerchantStatus.PENDING, MerchantStatus.REJECTED, MerchantStatus.SUSPENDED);
        decide(MerchantStatus.APPROVED, adminId, null, now);
    }

    public void reject(String adminId, String reason, Instant now) {
        requireStatus("rejected", MerchantStatus.PENDING);
        decide(MerchantStatus.REJECTED, adminId, reason, now);
    }

    public void suspend(String adminId, String reason, Instant now) {
        requireStatus("suspended", MerchantStatus.APPROVED);
        decide(MerchantStatus.SUSPENDED, adminId, reason, now);
    }

    public void reinstate(String adminId, Instant now) {
        requireStatus("reinstated", MerchantStatus.SUSPENDED);
        decide(MerchantStatus.APPROVED, adminId, null, now);
    }

    /** A rejected merchant fixed its details and asks for a new review. */
    public void resubmit(Instant now) {
        requireStatus("resubmitted", MerchantStatus.REJECTED);
        status = MerchantStatus.PENDING;
        decidedBy = null;
        decidedAt = null;
        decisionReason = null;
        updatedAt = now;
    }

    public void changeRateLimit(int requestsPerMinute, Instant now) {
        rateLimitPerMinute = requestsPerMinute;
        updatedAt = now;
    }

    public boolean isApproved() {
        return status == MerchantStatus.APPROVED;
    }

    private void applyProfile(MerchantProfile profile, Instant now) {
        businessName = profile.businessName();
        businessRegNo = profile.businessRegNo();
        contactEmail = profile.contactEmail();
        contactPhone = profile.contactPhone();
        addressLine = profile.addressLine();
        city = profile.city();
        website = profile.website();
        updatedAt = now;
    }

    private void decide(MerchantStatus newStatus, String adminId, String reason, Instant now) {
        status = newStatus;
        decidedBy = adminId;
        decidedAt = now;
        decisionReason = reason;
        updatedAt = now;
    }

    private void requireStatus(String action, MerchantStatus... allowed) {
        if (!Arrays.asList(allowed).contains(status)) {
            throw new ConflictException(ErrorCode.INVALID_STATE,
                    "A merchant that is " + status + " cannot be " + action);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getBusinessRegNo() {
        return businessRegNo;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public String getCity() {
        return city;
    }

    public String getWebsite() {
        return website;
    }

    public MerchantStatus getStatus() {
        return status;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
