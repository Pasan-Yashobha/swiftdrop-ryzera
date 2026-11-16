package lk.swiftdrop.document.invoice;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class InvoiceRepository {
    private static final String METADATA = "id, invoice_no, merchant_id, merchant_name, period_start, period_end, "
            + "total_amount, currency, line_count, generated_at";
    private final JdbcClient jdbc;

    public InvoiceRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    /**
     * Inserts the invoice and its lines, or returns the invoice that already exists for the same
     * merchant and period. Lines are written only by the call that created the invoice, so a
     * retry (or a concurrent duplicate) never adds a second set of lines.
     */
    public StoredInvoice store(UUID id, String invoiceNo, InvoiceRequest request, BigDecimal total,
                               String hash, byte[] pdf, Instant generatedAt) {
        int inserted = jdbc.sql("""
                INSERT INTO invoice (id, invoice_no, merchant_id, merchant_name, period_start, period_end,
                    total_amount, currency, line_count, snapshot_hash, pdf_content, generated_at, merchant_service_id)
                VALUES (:id, :number, :merchant, :name, :start, :end, :total, 'LKR', :count, :hash, :pdf, :generated,
                    :merchantService)
                ON CONFLICT (merchant_id, period_start, period_end) DO NOTHING
                """)
                .param("merchantService", request.merchantServiceId(), java.sql.Types.OTHER)
                .param("id", id).param("number", invoiceNo).param("merchant", request.merchantId())
                .param("name", request.merchantName()).param("start", request.periodStart())
                .param("end", request.periodEnd()).param("total", total).param("count", request.lines().size())
                .param("hash", hash).param("pdf", pdf).param("generated", Timestamp.from(generatedAt)).update();
        if (inserted == 1) {
            int lineNo = 1;
            for (InvoiceRequest.Line line : request.lines()) {
                jdbc.sql("""
                        INSERT INTO invoice_line (id, invoice_id, line_no, parcel_id, tracking_number, description, amount)
                        VALUES (:id, :invoice, :lineNo, :parcel, :tracking, :description, :amount)
                        """)
                        .param("id", UUID.randomUUID()).param("invoice", id).param("lineNo", lineNo++)
                        .param("parcel", line.parcelId()).param("tracking", line.trackingNumber())
                        .param("description", line.description()).param("amount", line.amount()).update();
            }
        }
        return jdbc.sql("SELECT " + METADATA + ", snapshot_hash FROM invoice "
                        + "WHERE merchant_id = :merchant AND period_start = :start AND period_end = :end")
                .param("merchant", request.merchantId()).param("start", request.periodStart())
                .param("end", request.periodEnd())
                .query((row, index) -> new StoredInvoice(metadata(row), row.getString("snapshot_hash"), inserted == 1)).single();
    }

    public Optional<InvoiceDetail> findVisible(UUID id, String userId, boolean admin) {
        return jdbc.sql("SELECT " + METADATA + " FROM invoice WHERE id = :id AND (:admin OR merchant_id = :user)")
                .param("id", id).param("admin", admin).param("user", userId)
                .query((row, index) -> metadata(row)).optional()
                .map(invoice -> new InvoiceDetail(invoice, lines(invoice.id())));
    }

    public Optional<PdfDownload> findVisiblePdf(UUID id, String userId, boolean admin) {
        return jdbc.sql("SELECT invoice_no, pdf_content FROM invoice WHERE id = :id AND (:admin OR merchant_id = :user)")
                .param("id", id).param("admin", admin).param("user", userId)
                .query((row, index) -> new PdfDownload(row.getString("invoice_no"), row.getBytes("pdf_content"))).optional();
    }

    public List<InvoiceMetadata> list(String userId, boolean admin, int page, int size) {
        return jdbc.sql("SELECT " + METADATA + " FROM invoice WHERE (:admin OR merchant_id = :user) "
                        + "ORDER BY generated_at DESC, id LIMIT :size OFFSET :offset")
                .param("admin", admin).param("user", userId).param("size", size).param("offset", (long) page * size)
                .query((row, index) -> metadata(row)).list();
    }

    public long count(String userId, boolean admin) {
        return jdbc.sql("SELECT count(*) FROM invoice WHERE (:admin OR merchant_id = :user)")
                .param("admin", admin).param("user", userId).query(Long.class).single();
    }

    private List<InvoiceDetail.Line> lines(UUID invoiceId) {
        return jdbc.sql("SELECT line_no, parcel_id, tracking_number, description, amount FROM invoice_line "
                        + "WHERE invoice_id = :invoice ORDER BY line_no")
                .param("invoice", invoiceId)
                .query((row, index) -> new InvoiceDetail.Line(row.getInt("line_no"),
                        row.getObject("parcel_id", UUID.class), row.getString("tracking_number"),
                        row.getString("description"), row.getBigDecimal("amount"))).list();
    }

    private static InvoiceMetadata metadata(ResultSet row) throws SQLException {
        return new InvoiceMetadata(row.getObject("id", UUID.class), row.getString("invoice_no"),
                row.getString("merchant_id"), row.getString("merchant_name"),
                row.getObject("period_start", LocalDate.class), row.getObject("period_end", LocalDate.class),
                row.getBigDecimal("total_amount"), row.getString("currency"), row.getInt("line_count"),
                row.getTimestamp("generated_at").toInstant());
    }

    /** {@code created} is false when the merchant already had an invoice for this period and it was returned. */
    public record StoredInvoice(InvoiceMetadata metadata, String hash, boolean created) { }
    public record PdfDownload(String invoiceNo, byte[] bytes) { }
}
