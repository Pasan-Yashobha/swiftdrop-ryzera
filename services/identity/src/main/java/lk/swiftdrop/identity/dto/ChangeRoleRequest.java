package lk.swiftdrop.identity.dto;

import jakarta.validation.constraints.NotNull;
import lk.swiftdrop.identity.domain.Role;

public record ChangeRoleRequest(@NotNull(message = "Role is required") Role role) {
}
