package lk.swiftdrop.document.receipt;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Temporary local input adapter until the Parcel delivery-event contract is available. */
@RestController
@Profile("dev")
public class DevReceiptController {
    private final ReceiptService receipts;
    public DevReceiptController(ReceiptService receipts) { this.receipts = receipts; }

    @PostMapping("/api/v1/dev/receipts")
    public ResponseEntity<ReceiptMetadata> generate(@Valid @RequestBody ReceiptRequest snapshot) {
        return ResponseEntity.ok(receipts.generate(snapshot));
    }
}
