package lk.swiftdrop.merchant.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Outcome of one CSV row. A created row carries {@code trackingNo} and {@code orderId}, a failed row carries
 * {@code errors}; the other fields are left out of the JSON.
 *
 * @param row 1-based record number in the file (the header is row 1)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BulkRowResult(int row, BulkRowStatus status, String trackingNo, UUID orderId, List<String> errors) {

    public static BulkRowResult created(int row, OrderResponse order) {
        return new BulkRowResult(row, BulkRowStatus.CREATED, order.trackingNo(), order.id(), null);
    }

    public static BulkRowResult failed(int row, List<String> errors) {
        return new BulkRowResult(row, BulkRowStatus.FAILED, null, null, List.copyOf(errors));
    }
}
