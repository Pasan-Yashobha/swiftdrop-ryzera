package lk.swiftdrop.notification.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** New wording of a template. The subject is used for email only (ignored for SMS). */
public record UpdateTemplateRequest(
        @Size(max = 200) String subject,
        @NotBlank @Size(max = 2000) String body,
        @NotNull Boolean active) {
}
