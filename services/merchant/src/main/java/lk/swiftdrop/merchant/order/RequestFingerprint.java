package lk.swiftdrop.merchant.order;

import java.math.RoundingMode;
import java.util.List;
import lk.swiftdrop.merchant.order.dto.CreateOrderRequest;

/**
 * Fingerprint of an order request, stored with the order to tell a replay of the same request (same
 * Idempotency-Key, same payload) from a reuse of the key for a different one. Two requests that differ only in
 * surrounding blanks, an absent versus blank optional field or the notation of the weight (2.5 or 2.50) have the
 * same fingerprint; the environment is part of it.
 */
final class RequestFingerprint {

    /** A control character, which no validated field can contain, so values cannot run into each other. */
    private static final String SEPARATOR = "\u001f";

    private RequestFingerprint() {
    }

    static String of(CreateOrderRequest request, boolean sandbox) {
        List<String> parts = List.of(
                Boolean.toString(sandbox),
                canonical(request.externalRef()),
                canonical(request.recipient().name()),
                canonical(request.recipient().phone()),
                canonical(request.pickup().addressLine()),
                canonical(request.pickup().city()),
                canonical(request.pickup().postalCode()),
                canonical(request.dropoff().addressLine()),
                canonical(request.dropoff().city()),
                canonical(request.dropoff().postalCode()),
                request.parcel().weightKg().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                request.parcel().size().name(),
                canonical(request.parcel().description()),
                canonical(request.notes()));
        return Sha256.hex(String.join(SEPARATOR, parts));
    }

    private static String canonical(String value) {
        return value == null ? "" : value.strip();
    }
}
