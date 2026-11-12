package lk.swiftdrop.document.receipt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReceiptMetadata(UUID id, String receiptNo, UUID parcelId, String trackingNumber,
                               Instant deliveredAt, BigDecimal totalAmount, String currency,
                               Instant generatedAt) {
}
