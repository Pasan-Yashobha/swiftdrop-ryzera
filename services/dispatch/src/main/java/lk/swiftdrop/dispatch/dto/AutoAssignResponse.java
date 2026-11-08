package lk.swiftdrop.dispatch.dto;

import java.util.List;
import java.util.UUID;

public record AutoAssignResponse(
        List<AssignedItem> assigned,
        List<SkippedItem> skipped) {

    public record AssignedItem(UUID parcelId, UUID courierId, String strategy) {
    }

    public record SkippedItem(UUID parcelId, String reason) {
    }
}

