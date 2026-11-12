package lk.swiftdrop.document.receipt;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/receipts")
public class ReceiptController {
    private final ReceiptService receipts;
    public ReceiptController(ReceiptService receipts) { this.receipts = receipts; }

    @GetMapping
    public ReceiptService.ReceiptPage list(Authentication user,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return receipts.list(user.getName(), isAdmin(user), page, size);
    }

    @GetMapping("/{parcelId}")
    public ReceiptMetadata get(@PathVariable UUID parcelId, Authentication user) {
        return receipts.getByParcel(parcelId, user.getName(), isAdmin(user));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, Authentication user) {
        var pdf = receipts.download(id, user.getName(), isAdmin(user));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("receipt-" + pdf.receiptNo() + ".pdf").build().toString())
                .body(pdf.bytes());
    }

    private static boolean isAdmin(Authentication user) {
        return user.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
