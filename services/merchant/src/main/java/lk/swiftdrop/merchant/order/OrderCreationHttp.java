package lk.swiftdrop.merchant.order;

import java.net.URI;
import java.util.List;
import lk.swiftdrop.merchant.common.ApiError;
import lk.swiftdrop.merchant.common.BadRequestException;
import lk.swiftdrop.merchant.common.Inputs;
import lk.swiftdrop.merchant.order.dto.OrderResponse;
import org.springframework.http.ResponseEntity;

/** The HTTP details that the portal and the merchant API share when they create an order. */
final class OrderCreationHttp {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final String REPLAYED_HEADER = "Idempotent-Replayed";

    /** Size of the {@code idempotency_key} column. */
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private OrderCreationHttp() {
    }

    /** The trimmed key, or null when the caller sent none (or only blanks). */
    static String idempotencyKey(String header) {
        String key = Inputs.trimToNull(header);
        if (key != null && key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BadRequestException("Validation failed", List.of(new ApiError.Detail(IDEMPOTENCY_KEY_HEADER,
                    "must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters")));
        }
        return key;
    }

    /**
     * 201 with a Location header for a new order; 200 with {@code Idempotent-Replayed: true} when the request
     * repeated an earlier one.
     *
     * @param collectionPath path of the order collection; the new order is addressed as {@code path/identifier}
     */
    static ResponseEntity<OrderResponse> response(OrderCreation creation, String collectionPath, String identifier) {
        if (creation.replayed()) {
            return ResponseEntity.ok().header(REPLAYED_HEADER, "true").body(creation.order());
        }
        return ResponseEntity.created(URI.create(collectionPath + "/" + identifier)).body(creation.order());
    }
}
