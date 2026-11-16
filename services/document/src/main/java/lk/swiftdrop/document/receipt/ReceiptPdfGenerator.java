package lk.swiftdrop.document.receipt;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class ReceiptPdfGenerator {
    private static final Color INK = new Color(14, 21, 19);
    private static final Color YELLOW = new Color(255, 199, 44);
    private static final Color MIST = new Color(220, 226, 221);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm 'Sri Lanka'")
            .withZone(ZoneId.of("Asia/Colombo"));

    public byte[] generate(String receiptNo, ReceiptRequest snapshot, Instant generatedAt) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A5, 28, 28, 28, 28);
        try {
            PdfWriter.getInstance(document, output);
            document.open();
            document.addTitle("SwiftDrop delivery receipt " + receiptNo);
            document.addAuthor("SwiftDrop");
            PdfPTable header = new PdfPTable(1);
            header.setWidthPercentage(100);
            PdfPCell brand = cell("SwiftDrop", 25, Font.BOLD, Color.WHITE);
            brand.setBackgroundColor(INK);
            brand.setPadding(18);
            header.addCell(brand);
            PdfPCell title = cell("DELIVERY RECEIPT", 12, Font.BOLD, INK);
            title.setBackgroundColor(YELLOW);
            title.setPadding(10);
            header.addCell(title);
            header.setSpacingAfter(18);
            document.add(header);

            document.add(new Paragraph(receiptNo, font(9, Font.NORMAL, INK)));
            Paragraph tracking = new Paragraph(snapshot.trackingNumber(), font(17, Font.BOLD, INK));
            tracking.setSpacingBefore(8);
            tracking.setSpacingAfter(8);
            document.add(tracking);
            document.add(new Paragraph("Delivered " + TIME.format(snapshot.deliveredAt()), font(10, Font.NORMAL, INK)));

            PdfPTable charges = new PdfPTable(new float[]{50, 50});
            charges.setWidthPercentage(100);
            charges.setSpacingBefore(24);
            row(charges, "Base fare", snapshot.baseAmount());
            row(charges, "Weight charge", snapshot.weightAmount());
            row(charges, "Parcel size", snapshot.sizeAmount());
            row(charges, "Express surcharge", snapshot.expressAmount());
            row(charges, "Discount", snapshot.discountAmount().negate());
            PdfPCell totalLabel = cell("TOTAL (LKR)", 12, Font.BOLD, Color.WHITE);
            totalLabel.setBackgroundColor(INK);
            totalLabel.setPadding(14);
            charges.addCell(totalLabel);
            PdfPCell total = cell(money(snapshot.totalAmount()), 14, Font.BOLD, YELLOW);
            total.setBackgroundColor(INK);
            total.setHorizontalAlignment(Element.ALIGN_RIGHT);
            total.setPadding(14);
            charges.addCell(total);
            document.add(charges);

            Paragraph footer = new Paragraph("Generated " + TIME.format(generatedAt)
                    + "\nParcel reference " + snapshot.parcelId(), font(8, Font.NORMAL, new Color(70, 85, 76)));
            footer.setSpacingBefore(24);
            document.add(footer);
        } catch (DocumentException exception) {
            throw new IllegalStateException("Receipt PDF could not be generated", exception);
        } finally {
            document.close();
        }
        return output.toByteArray();
    }

    private static void row(PdfPTable table, String label, BigDecimal amount) {
        PdfPCell name = cell(label, 11, Font.NORMAL, INK);
        name.setPadding(10);
        name.setBorderWidthBottom(0.5f);
        name.setBorderColorBottom(MIST);
        table.addCell(name);
        PdfPCell value = cell(money(amount), 11, Font.NORMAL, INK);
        value.setHorizontalAlignment(Element.ALIGN_RIGHT);
        value.setPadding(10);
        value.setBorderWidthBottom(0.5f);
        value.setBorderColorBottom(MIST);
        table.addCell(value);
    }

    private static PdfPCell cell(String text, int size, int style, Color color) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font(size, style, color)));
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private static Font font(int size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
