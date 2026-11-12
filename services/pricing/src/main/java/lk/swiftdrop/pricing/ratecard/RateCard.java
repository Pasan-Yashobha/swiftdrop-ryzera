package lk.swiftdrop.pricing.ratecard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "rate_card")
public class RateCard {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Version
    private Long version;
    @Column(nullable = false)
    private UUID originZoneId;
    @Column(nullable = false)
    private UUID destinationZoneId;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFee;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal perKgFee;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal smallSurcharge;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal mediumSurcharge;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal largeSurcharge;
    @Column(precision = 5, scale = 2)
    private BigDecimal expressMultiplier;
    @Column(nullable = false)
    private LocalDate validFrom;
    private LocalDate validTo;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected RateCard() {
        // JPA
    }

    public RateCard(RateCardRequest request, Instant now) {
        active = true;
        createdAt = now;
        update(request, now);
    }

    public void update(RateCardRequest request, Instant now) {
        originZoneId = request.originZoneId();
        destinationZoneId = request.destinationZoneId();
        baseFee = request.baseFee();
        perKgFee = request.perKgFee();
        smallSurcharge = request.smallSurcharge();
        mediumSurcharge = request.mediumSurcharge();
        largeSurcharge = request.largeSurcharge();
        expressMultiplier = request.expressMultiplier();
        validFrom = request.validFrom();
        validTo = request.validTo();
        updatedAt = now;
    }

    public void deactivate(Instant now) {
        active = false;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOriginZoneId() { return originZoneId; }
    public UUID getDestinationZoneId() { return destinationZoneId; }
    public BigDecimal getBaseFee() { return baseFee; }
    public BigDecimal getPerKgFee() { return perKgFee; }
    public BigDecimal getSmallSurcharge() { return smallSurcharge; }
    public BigDecimal getMediumSurcharge() { return mediumSurcharge; }
    public BigDecimal getLargeSurcharge() { return largeSurcharge; }
    public BigDecimal getExpressMultiplier() { return expressMultiplier; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
