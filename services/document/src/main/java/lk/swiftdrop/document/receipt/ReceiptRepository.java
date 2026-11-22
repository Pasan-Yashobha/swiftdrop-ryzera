package lk.swiftdrop.document.receipt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReceiptRepository {
    private static final String METADATA = "id, receipt_no, parcel_id, tracking_number, delivered_at, amount_total, currency, generated_at";
    private final JdbcClient jdbc;

    public ReceiptRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public StoredReceipt store(UUID id, String receiptNo, ReceiptRequest snapshot, String hash,
                                byte[] pdf, Instant generatedAt) {
        int inserted = jdbc.sql("""
                INSERT INTO receipt (id, receipt_no, parcel_id, customer_id, tracking_number,
                    delivered_at, amount_total, currency, snapshot_hash, pdf_content, generated_at)
                VALUES (:id, :number, :parcel, :customer, :tracking, :delivered, :total, 'LKR', :hash, :pdf, :generated)
                ON CONFLICT (parcel_id) DO NOTHING
                """)
                .param("id", id).param("number", receiptNo).param("parcel", snapshot.parcelId())
                .param("customer", snapshot.customerId()).param("tracking", snapshot.trackingNumber())
                .param("delivered", Timestamp.from(snapshot.deliveredAt())).param("total", snapshot.totalAmount())
                .param("hash", hash).param("pdf", pdf).param("generated", Timestamp.from(generatedAt)).update();
        return jdbc.sql("SELECT " + METADATA + ", snapshot_hash FROM receipt WHERE parcel_id = :parcel")
                .param("parcel", snapshot.parcelId())
                .query((row, index) -> new StoredReceipt(metadata(row), row.getString("snapshot_hash"), inserted == 1)).single();
    }

    /** For other services (Notification attaches the PDF to an email): no ownership check, the caller is trusted. */
    public Optional<PdfDownload> findPdfById(UUID id) {
        return jdbc.sql("SELECT receipt_no, pdf_content FROM receipt WHERE id = :id").param("id", id)
                .query((row, index) -> new PdfDownload(row.getString("receipt_no"), row.getBytes("pdf_content"))).optional();
    }

    public Optional<ReceiptMetadata> findVisibleByParcel(UUID parcelId, String userId, boolean admin) {
        return jdbc.sql("SELECT " + METADATA + " FROM receipt WHERE parcel_id = :parcel AND (:admin OR customer_id = :user)")
                .param("parcel", parcelId).param("admin", admin).param("user", userId)
                .query((row, index) -> metadata(row)).optional();
    }

    public Optional<PdfDownload> findVisiblePdf(UUID id, String userId, boolean admin) {
        return jdbc.sql("SELECT receipt_no, pdf_content FROM receipt WHERE id = :id AND (:admin OR customer_id = :user)")
                .param("id", id).param("admin", admin).param("user", userId)
                .query((row, index) -> new PdfDownload(row.getString("receipt_no"), row.getBytes("pdf_content"))).optional();
    }

    public List<ReceiptMetadata> list(String userId, boolean admin, int page, int size) {
        return jdbc.sql("SELECT " + METADATA + " FROM receipt WHERE (:admin OR customer_id = :user) "
                        + "ORDER BY generated_at DESC, id LIMIT :size OFFSET :offset")
                .param("admin", admin).param("user", userId).param("size", size).param("offset", (long) page * size)
                .query((row, index) -> metadata(row)).list();
    }

    public long count(String userId, boolean admin) {
        return jdbc.sql("SELECT count(*) FROM receipt WHERE (:admin OR customer_id = :user)")
                .param("admin", admin).param("user", userId).query(Long.class).single();
    }

    private static ReceiptMetadata metadata(ResultSet row) throws SQLException {
        return new ReceiptMetadata(row.getObject("id", UUID.class), row.getString("receipt_no"),
                row.getObject("parcel_id", UUID.class), row.getString("tracking_number"),
                row.getTimestamp("delivered_at").toInstant(), row.getBigDecimal("amount_total"),
                row.getString("currency"), row.getTimestamp("generated_at").toInstant());
    }

    /** {@code created} is false when this parcel already had a receipt and the stored one was returned. */
    public record StoredReceipt(ReceiptMetadata metadata, String hash, boolean created) { }
    public record PdfDownload(String receiptNo, byte[] bytes) { }
}
