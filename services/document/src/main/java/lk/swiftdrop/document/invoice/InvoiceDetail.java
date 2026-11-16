package lk.swiftdrop.document.invoice;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** One invoice with its billing lines, as stored when it was generated. */
public record InvoiceDetail(InvoiceMetadata invoice, List<Line> lines) {
    public record Line(int lineNo, UUID parcelId, String trackingNumber, String description, BigDecimal amount) { }
}
