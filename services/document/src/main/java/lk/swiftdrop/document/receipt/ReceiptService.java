package lk.swiftdrop.document.receipt;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service
@Validated
@Transactional(readOnly = true)
public class ReceiptService {
    private final ReceiptRepository receipts;
    private final ReceiptPdfGenerator pdfs;
    private final Clock clock;

    public ReceiptService(ReceiptRepository receipts, ReceiptPdfGenerator pdfs, Clock clock) {
        this.receipts = receipts;
        this.pdfs = pdfs;
        this.clock = clock;
    }

    @Transactional
    public ReceiptMetadata generate(@Valid ReceiptRequest snapshot) {
        UUID id = UUID.randomUUID();
        String number = "RCP-" + id;
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String hash = fingerprint(snapshot);
        byte[] pdf = pdfs.generate(number, snapshot, now);
        var stored = receipts.store(id, number, snapshot, hash, pdf, now);
        if (!stored.hash().equals(hash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A receipt already exists for this parcel with different delivery data");
        }
        return stored.metadata();
    }

    public ReceiptMetadata getByParcel(UUID parcelId, String userId, boolean admin) {
        return receipts.findVisibleByParcel(parcelId, userId, admin).orElseThrow(ReceiptService::notFound);
    }

    public ReceiptRepository.PdfDownload download(UUID id, String userId, boolean admin) {
        return receipts.findVisiblePdf(id, userId, admin).orElseThrow(ReceiptService::notFound);
    }

    public ReceiptPage list(String userId, boolean admin, int page, int size) {
        return new ReceiptPage(receipts.list(userId, admin, page, size), page, size, receipts.count(userId, admin));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found");
    }

    private static String fingerprint(ReceiptRequest data) {
        StringJoiner canonical = new StringJoiner("|");
        canonical.add(data.parcelId().toString()).add(data.customerId().length() + ":" + data.customerId()).add(data.trackingNumber())
                .add(data.deliveredAt().toString());
        for (BigDecimal amount : List.of(data.baseAmount(), data.weightAmount(), data.sizeAmount(),
                data.expressAmount(), data.discountAmount(), data.totalAmount())) {
            canonical.add(amount.setScale(2).toPlainString());
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record ReceiptPage(List<ReceiptMetadata> items, int page, int size, long totalElements) { }
}
