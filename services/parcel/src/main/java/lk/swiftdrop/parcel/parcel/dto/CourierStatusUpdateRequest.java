package lk.swiftdrop.parcel.parcel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.swiftdrop.parcel.parcel.ParcelStatus;

public record CourierStatusUpdateRequest(
        @NotBlank @Size(max = 64) String courierId,
        @NotNull ParcelStatus status,
        @Size(max = 120) String recipientName,
        @Size(max = 255) String photoObjectKey,
        @Size(max = 10) String otp,
        @Size(max = 500) String failureReason,
        @Size(max = 500) String note) {
}
