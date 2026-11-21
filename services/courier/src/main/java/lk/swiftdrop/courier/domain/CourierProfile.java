package lk.swiftdrop.courier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "courier_profiles")
public class CourierProfile {

    @Column(name = "last_location_report_at")
    private Instant lastLocationReportAt;

    public Instant getLastLocationReportAt() { return lastLocationReportAt; }
    public void recordLocationReport(Instant now) { this.lastLocationReportAt = now; }

    @Id
    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "phone", length = 32)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 32)
    private VehicleType vehicleType;

    @Column(name = "vehicle_plate", length = 32)
    private String vehiclePlate;

    @Column(name = "is_available", nullable = false)
    private boolean available;

    @Column(name = "active_deliveries", nullable = false)
    private int activeDeliveries;

    @Column(name = "max_active_deliveries", nullable = false)
    private int maxActiveDeliveries;

    @Column(name = "assigned_zone_id")
    private UUID assignedZoneId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourierProfile() {
    }

    public CourierProfile(UUID courierId, String fullName, String phone, VehicleType vehicleType,
                          String vehiclePlate, boolean available, int activeDeliveries,
                          int maxActiveDeliveries, UUID assignedZoneId, Instant now) {
        this.courierId = Objects.requireNonNull(courierId, "courierId must not be null");
        this.fullName = fullName;
        this.phone = phone;
        this.vehicleType = vehicleType != null ? vehicleType : VehicleType.BIKE;
        this.vehiclePlate = vehiclePlate;
        this.available = available;
        this.activeDeliveries = Math.max(0, activeDeliveries);
        this.maxActiveDeliveries = maxActiveDeliveries > 0 ? maxActiveDeliveries : 8;
        this.assignedZoneId = assignedZoneId;
        this.createdAt = Objects.requireNonNull(now, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(now, "updatedAt must not be null");
    }

    public static CourierProfile createNew(UUID courierId, String fullName, String phone,
                                           VehicleType vehicleType, String vehiclePlate,
                                           int defaultMaxActive, UUID assignedZoneId, Instant now) {
        return new CourierProfile(
                courierId,
                fullName,
                phone,
                vehicleType,
                vehiclePlate,
                true,
                0,
                defaultMaxActive > 0 ? defaultMaxActive : 8,
                assignedZoneId,
                now
        );
    }

    public void updateDetails(String fullName, String phone, VehicleType vehicleType,
                              String vehiclePlate, UUID assignedZoneId, Instant now) {
        this.fullName = fullName;
        this.phone = phone;
        if (vehicleType != null) {
            this.vehicleType = vehicleType;
        }
        this.vehiclePlate = vehiclePlate;
        this.assignedZoneId = assignedZoneId;
        this.updatedAt = Objects.requireNonNull(now, "updatedAt must not be null");
    }

    public void setAvailability(boolean available, Instant now) {
        this.available = available;
        this.updatedAt = Objects.requireNonNull(now, "updatedAt must not be null");
    }

    public void incrementActiveDeliveries(Instant now) {
        this.activeDeliveries++;
        this.updatedAt = Objects.requireNonNull(now, "updatedAt must not be null");
    }

    public void decrementActiveDeliveries(Instant now) {
        if (this.activeDeliveries > 0) {
            this.activeDeliveries--;
        }
        this.updatedAt = Objects.requireNonNull(now, "updatedAt must not be null");
    }

    public void setMaxActiveDeliveries(int maxActiveDeliveries, Instant now) {
        if (maxActiveDeliveries > 0) {
            this.maxActiveDeliveries = maxActiveDeliveries;
            this.updatedAt = Objects.requireNonNull(now, "updatedAt must not be null");
        }
    }

    // Getters

    public UUID getCourierId() {
        return courierId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public String getVehiclePlate() {
        return vehiclePlate;
    }

    public boolean isAvailable() {
        return available;
    }

    public int getActiveDeliveries() {
        return activeDeliveries;
    }

    public int getMaxActiveDeliveries() {
        return maxActiveDeliveries;
    }

    public UUID getAssignedZoneId() {
        return assignedZoneId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
