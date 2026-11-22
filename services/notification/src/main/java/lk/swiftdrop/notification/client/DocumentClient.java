package lk.swiftdrop.notification.client;

import lk.swiftdrop.notification.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Receipt PDFs from the Document Service ({@code GET /internal/receipts/{id}/pdf}, internal token). A failed fetch
 * counts as a failed email attempt and goes through the normal channel retry.
 */
@Component
public class DocumentClient extends InternalServiceClient {

    public DocumentClient(AppProperties properties) {
        super("Document Service", properties.clients().documentUrl(), properties);
    }

    public byte[] receiptPdf(String receiptId) {
        return get("/internal/receipts/{id}/pdf", byte[].class, receiptId);
    }
}
