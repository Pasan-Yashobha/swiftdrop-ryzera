package lk.swiftdrop.document.invoice;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service
@Validated
@Transactional(readOnly = true)
public class InvoiceService {
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("uuuuMM");
    /** Name Postgres gives the UNIQUE key on invoice_line.parcel_id. */
    private static final String PARCEL_ALREADY_BILLED = "invoice_line_parcel_id_key";

    private final InvoiceRepository invoices;
    private final InvoicePdfGenerator pdfs;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoices, InvoicePdfGenerator pdfs, Clock clock) {
        this.invoices = invoices;
        this.pdfs = pdfs;
        this.clock = clock;
    }

    /**
     * Creates the invoice for a merchant and period. Submitting the same content again returns the
     * invoice that already exists; different content for the same merchant and period is a 409.
     */
    @Transactional
    public InvoiceMetadata generate(@Valid InvoiceRequest request) {
        UUID id = UUID.randomUUID();
        String number = "INV-" + YEAR_MONTH.format(request.periodStart()) + "-"
                + id.toString().replace("-", "").substring(0, 12).toUpperCase();
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        BigDecimal total = request.total();
        String hash = fingerprint(request);
        byte[] pdf = pdfs.generate(number, request, total, now);
        InvoiceRepository.StoredInvoice stored;
        try {
            stored = invoices.store(id, number, request, total, hash, pdf, now);
        } catch (DuplicateKeyException exception) {
            if (String.valueOf(exception.getMostSpecificCause().getMessage()).contains(PARCEL_ALREADY_BILLED)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "One of these parcels is already billed on another invoice");
            }
            throw exception;
        }
        if (!stored.hash().equals(hash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An invoice already exists for this merchant and period with different content");
        }
        return stored.metadata();
    }

    public InvoiceDetail get(UUID id, String userId, boolean admin) {
        return invoices.findVisible(id, userId, admin).orElseThrow(InvoiceService::notFound);
    }

    public InvoiceRepository.PdfDownload download(UUID id, String userId, boolean admin) {
        return invoices.findVisiblePdf(id, userId, admin).orElseThrow(InvoiceService::notFound);
    }

    public InvoicePage list(String userId, boolean admin, int page, int size) {
        return new InvoicePage(invoices.list(userId, admin, page, size), page, size, invoices.count(userId, admin));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found");
    }

    /** Same content gives the same fingerprint whatever the line order or decimal scale. */
    private static String fingerprint(InvoiceRequest data) {
        StringJoiner canonical = new StringJoiner("|");
        canonical.add(data.merchantId().length() + ":" + data.merchantId())
                .add(data.merchantName().length() + ":" + data.merchantName())
                .add(data.periodStart().toString()).add(data.periodEnd().toString());
        data.lines().stream().sorted(Comparator.comparing(InvoiceRequest.Line::parcelId)).forEach(line ->
                canonical.add(line.parcelId() + "," + line.trackingNumber() + ","
                        + line.description().length() + ":" + line.description() + ","
                        + line.amount().setScale(2).toPlainString()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record InvoicePage(List<InvoiceMetadata> items, int page, int size, long totalElements) { }
}
