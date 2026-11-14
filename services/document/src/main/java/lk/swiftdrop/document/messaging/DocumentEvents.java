package lk.swiftdrop.document.messaging;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lk.swiftdrop.document.invoice.InvoiceMetadata;
import lk.swiftdrop.document.receipt.ReceiptMetadata;

/** The {@code data} of the events this service publishes. Amounts are decimal strings such as "1250.00". */
public final class DocumentEvents {
    public static final String RECEIPT_GENERATED = "receipt.generated";
    public static final String INVOICE_GENERATED = "invoice.generated";

    private DocumentEvents() { }

    /** Catalogue fields (parcelId, documentId, downloadUrl) plus what the notification needs. */
    public static Map<String, Object> receiptGenerated(ReceiptMetadata receipt, String customerId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("parcelId", receipt.parcelId().toString());
        data.put("documentId", receipt.id().toString());
        data.put("receiptNo", receipt.receiptNo());
        data.put("downloadUrl", "/api/v1/receipts/" + receipt.id() + "/download");
        data.put("customerId", customerId);
        data.put("trackingNo", receipt.trackingNumber());
        data.put("totalAmount", amount(receipt.totalAmount()));
        data.put("currency", receipt.currency());
        return data;
    }

    /**
     * {@code merchantId} is the merchant's id in the Merchant service (what webhooks look up) and is left out when it
     * is not known; {@code ownerUserId} is the Identity user id of the merchant's owner.
     */
    public static Map<String, Object> invoiceGenerated(InvoiceMetadata invoice, UUID merchantServiceId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceId", invoice.id().toString());
        data.put("invoiceNo", invoice.invoiceNo());
        if (merchantServiceId != null) data.put("merchantId", merchantServiceId.toString());
        data.put("ownerUserId", invoice.merchantId());
        data.put("periodStart", invoice.periodStart().toString());
        data.put("periodEnd", invoice.periodEnd().toString());
        data.put("totalAmount", amount(invoice.totalAmount()));
        data.put("currency", invoice.currency());
        data.put("lineCount", invoice.lineCount());
        data.put("downloadUrl", "/api/v1/invoices/" + invoice.id() + "/download");
        return data;
    }

    private static String amount(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
