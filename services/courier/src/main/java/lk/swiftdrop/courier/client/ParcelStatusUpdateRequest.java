package lk.swiftdrop.courier.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Agreed request payload for M4 Parcel Service status transition endpoint:
 * PATCH /internal/parcels/{parcelId}/status
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParcelStatusUpdateRequest(
        String status,
        UUID courierId,
        String recipientName,
        String photoObjectKey,
        String otp,
        String failureReason
) {}
