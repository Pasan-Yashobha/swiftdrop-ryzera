package lk.swiftdrop.courier.client;

import java.time.Instant;
import java.util.UUID;

/**
 * Courier-side acknowledgement of a successful Parcel status update.
 * M4 returns HTTP 204 without a body: trackingNo, previousStatus and updatedAt
 * are then null, and parcelId/status identify the acknowledged request.
 * These optional metadata fields also support legacy HTTP 200 JSON responses.
 */
public record ParcelStatusUpdateResponse(
        UUID parcelId,
        String trackingNo,
        String previousStatus,
        String status,
        Instant updatedAt
) {}
