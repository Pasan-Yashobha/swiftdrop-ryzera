package lk.swiftdrop.courier.dto;

import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.courier.domain.AssignmentStatus;
import lk.swiftdrop.courier.domain.CourierAssignment;

public record AssignedDeliveryResponse(
        UUID assignmentId,
        UUID parcelId,
        UUID courierId,
        String trackingNo,
        AssignmentStatus status,
        Instant assignedAt,
        Instant completedAt,
        String failureReason) {

    public static AssignedDeliveryResponse from(CourierAssignment assignment) {
        if (assignment == null) {
            return null;
        }
        return new AssignedDeliveryResponse(
                assignment.getId(),
                assignment.getParcelId(),
                assignment.getCourierId(),
                assignment.getTrackingNo(),
                assignment.getStatus(),
                assignment.getAssignedAt(),
                assignment.getCompletedAt(),
                assignment.getFailureReason()
        );
    }
}
