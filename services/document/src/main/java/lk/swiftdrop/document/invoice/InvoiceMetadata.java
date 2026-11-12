package lk.swiftdrop.document.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceMetadata(UUID id, String invoiceNo, String merchantId, String merchantName,
                              LocalDate periodStart, LocalDate periodEnd, BigDecimal totalAmount,
                              String currency, int lineCount, Instant generatedAt) {
}
