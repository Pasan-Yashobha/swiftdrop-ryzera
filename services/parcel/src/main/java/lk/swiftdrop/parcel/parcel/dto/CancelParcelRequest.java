package lk.swiftdrop.parcel.parcel.dto;

import jakarta.validation.constraints.Size;

public record CancelParcelRequest(
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason
) {
}
