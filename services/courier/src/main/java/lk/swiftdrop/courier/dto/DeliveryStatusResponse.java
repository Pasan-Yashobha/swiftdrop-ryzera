package lk.swiftdrop.courier.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Standard courier status update response.
 */
public record DeliveryStatusResponse(
        UUID parcelId,
        String trackingNo,
        String previousStatus,
        String status,
        Instant updatedAt
) {}
