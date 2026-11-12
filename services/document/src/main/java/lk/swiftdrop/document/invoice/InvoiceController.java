package lk.swiftdrop.document.invoice;

import jakarta.validation.Valid;
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

/**
 * Merchant invoices. Admin creates them and can read all; a merchant reads and downloads only
 * its own (matched on the JWT subject), and gets 404 for anyone else's.
 */
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {
    private final InvoiceService invoices;
    public InvoiceController(InvoiceService invoices) { this.invoices = invoices; }

    @PostMapping("/generate")
    public InvoiceMetadata generate(@Valid @RequestBody InvoiceRequest request) {
        return invoices.generate(request);
    }

    @GetMapping
    public InvoiceService.InvoicePage list(Authentication user,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return invoices.list(user.getName(), isAdmin(user), page, size);
    }

    @GetMapping("/{id}")
    public InvoiceDetail get(@PathVariable UUID id, Authentication user) {
        return invoices.get(id, user.getName(), isAdmin(user));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, Authentication user) {
        var pdf = invoices.download(id, user.getName(), isAdmin(user));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("invoice-" + pdf.invoiceNo() + ".pdf").build().toString())
                .body(pdf.bytes());
    }

    private static boolean isAdmin(Authentication user) {
        return user.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
