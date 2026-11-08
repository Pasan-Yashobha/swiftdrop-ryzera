package lk.swiftdrop.dispatch.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ManualAssignRequest(
        @NotNull(message = "parcelId must be provided")
        UUID parcelId,

        @NotNull(message = "courierId must be provided")
        UUID courierId) {
}

