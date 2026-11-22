package lk.swiftdrop.dispatch.dto;

import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.dispatch.domain.Assignment;
import lk.swiftdrop.dispatch.domain.AssignmentStatus;
import lk.swiftdrop.dispatch.domain.StrategyType;

public record AssignmentResponse(
        UUID assignmentId,
        UUID parcelId,
        UUID courierId,
        StrategyType strategy,
        AssignmentStatus status,
        UUID assignedBy,
        Instant assignedAt,
        String reason) {

    public static AssignmentResponse from(Assignment a) {
        return new AssignmentResponse(
                a.getId(),
                a.getParcelId(),
                a.getCourierId(),
                a.getStrategy(),
                a.getStatus(),
                a.getAssignedBy(),
                a.getAssignedAt(),
                a.getReason()
        );
    }
}

