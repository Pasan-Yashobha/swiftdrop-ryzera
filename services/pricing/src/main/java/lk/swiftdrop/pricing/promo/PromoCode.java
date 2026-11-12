package lk.swiftdrop.pricing.promo;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "promo_code")
public class PromoCode {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Version
    private Long version;
    @Column(nullable = false, unique = true, length = 30)
    private String code;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private PromoCodeRequest.DiscountType discountType;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;
    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal minOrderValue;
    private Integer maxUses;
    @Column(nullable = false)
    private int usedCount;
    @Column(nullable = false)
    private LocalDate validFrom;
    private LocalDate validTo;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected PromoCode() { }

    public PromoCode(PromoCodeRequest request, Instant now) {
        active = true;
        createdAt = now;
        update(request, now);
    }

    public void update(PromoCodeRequest request, Instant now) {
        code = request.code().toUpperCase(Locale.ROOT);
        discountType = request.discountType();
        discountValue = request.discountValue();
        minOrderValue = request.minOrderValue();
        maxUses = request.maxUses();
        validFrom = request.validFrom();
        validTo = request.validTo();
        updatedAt = now;
    }

    public void deactivate(Instant now) {
        active = false;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public PromoCodeRequest.DiscountType getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public BigDecimal getMinOrderValue() { return minOrderValue; }
    public Integer getMaxUses() { return maxUses; }
    public int getUsedCount() { return usedCount; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
