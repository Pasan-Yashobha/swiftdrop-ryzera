package lk.swiftdrop.dispatch.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.dispatch.domain.StrategyType;

public record AutoAssignRequest(
        @NotEmpty(message = "parcelIds cannot be empty")
        List<UUID> parcelIds,

        @NotNull(message = "strategy must be specified")
        StrategyType strategy) {
}
