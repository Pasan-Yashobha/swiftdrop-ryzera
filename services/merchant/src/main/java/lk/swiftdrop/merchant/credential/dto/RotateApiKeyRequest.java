package lk.swiftdrop.merchant.credential.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Body of {@code POST /merchants/me/api-keys/{id}/rotate}; the body and the field are both optional.
 *
 * @param graceHours how long the old key keeps working: 0 to 72, default 24; 0 revokes it at once
 */
public record RotateApiKeyRequest(@Min(0) @Max(72) Integer graceHours) {

    public static final int DEFAULT_GRACE_HOURS = 24;

    /** The grace period requested by {@code request}, which may be null (no body) or leave the field out. */
    public static int graceHoursOf(RotateApiKeyRequest request) {
        return request == null || request.graceHours == null ? DEFAULT_GRACE_HOURS : request.graceHours;
    }
}
