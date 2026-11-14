package lk.swiftdrop.document.receipt;

import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * For other services, authenticated by {@code X-Internal-Token} only. The Notification service fetches the PDF here to
 * attach it to the receipt email. Not reachable with a user's JWT, and not routed through the gateway.
 */
@RestController
public class InternalReceiptController {
    private final ReceiptService receipts;

    public InternalReceiptController(ReceiptService receipts) { this.receipts = receipts; }

    @GetMapping("/internal/receipts/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        var pdf = receipts.internalPdf(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("receipt-" + pdf.receiptNo() + ".pdf").build().toString())
                .body(pdf.bytes());
    }
}
