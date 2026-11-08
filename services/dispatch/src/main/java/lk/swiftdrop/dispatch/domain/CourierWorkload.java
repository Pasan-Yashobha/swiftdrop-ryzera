package lk.swiftdrop.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "courier_workload")
public class CourierWorkload {

    @Id
    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "active_deliveries", nullable = false)
    private int activeDeliveries;

    @Column(name = "max_active", nullable = false)
    private int maxActive;

    @Column(name = "available", nullable = false)
    private boolean available;

    @Column(name = "last_assigned_at")
    private Instant lastAssignedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected CourierWorkload() {
    }

    public CourierWorkload(UUID courierId, UUID zoneId, int activeDeliveries, int maxActive,
                           boolean available, Instant lastAssignedAt) {
        this.courierId = Objects.requireNonNull(courierId, "courierId cannot be null");
        this.zoneId = zoneId;
        this.activeDeliveries = activeDeliveries;
        this.maxActive = maxActive > 0 ? maxActive : 8;
        this.available = available;
        this.lastAssignedAt = lastAssignedAt;
    }

    public boolean isEligible() {
        return available && activeDeliveries < maxActive;
    }

    public void incrementActive(Instant assignmentTime) {
        this.activeDeliveries++;
        this.lastAssignedAt = assignmentTime != null ? assignmentTime : Instant.now();
    }

    public void decrementActive() {
        if (this.activeDeliveries > 0) {
            this.activeDeliveries--;
        }
    }

    public void updateStatus(boolean available, int activeDeliveries) {
        this.available = available;
        this.activeDeliveries = Math.max(0, activeDeliveries);
    }

    public void setMaxActive(int maxActive) {
        if (maxActive > 0) {
            this.maxActive = maxActive;
        }
    }

    public void setZoneId(UUID zoneId) {
        this.zoneId = zoneId;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public int getActiveDeliveries() {
        return activeDeliveries;
    }

    public int getMaxActive() {
        return maxActive;
    }

    public boolean isAvailable() {
        return available;
    }

    public Instant getLastAssignedAt() {
        return lastAssignedAt;
    }

    public Long getVersion() {
        return version;
    }
}
