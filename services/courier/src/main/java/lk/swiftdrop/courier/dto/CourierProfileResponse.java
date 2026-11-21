package lk.swiftdrop.courier.dto;

import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.courier.domain.CourierProfile;
import lk.swiftdrop.courier.domain.VehicleType;

public record CourierProfileResponse(
        UUID courierId,
        String fullName,
        String phone,
        VehicleType vehicleType,
        String vehiclePlate,
        boolean isAvailable,
        int activeDeliveries,
        int maxActiveDeliveries,
        UUID assignedZoneId,
        Instant createdAt,
        Instant updatedAt) {

    public static CourierProfileResponse from(CourierProfile profile) {
        return new CourierProfileResponse(
                profile.getCourierId(),
                profile.getFullName(),
                profile.getPhone(),
                profile.getVehicleType(),
                profile.getVehiclePlate(),
                profile.isAvailable(),
                profile.getActiveDeliveries(),
                profile.getMaxActiveDeliveries(),
                profile.getAssignedZoneId(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
