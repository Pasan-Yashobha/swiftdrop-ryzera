package lk.swiftdrop.document.invoice;

import com.lowagie.text.*;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/** Renders an A4 merchant invoice in the SwiftDrop style. The lines table repeats its header on each page. */
@Component
public class InvoicePdfGenerator {
    private static final Color INK = new Color(14, 21, 19);
    private static final Color YELLOW = new Color(255, 199, 44);
    private static final Color MIST = new Color(220, 226, 221);
    private static final Color MUTED = new Color(70, 85, 76);
    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM uuuu");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm 'Sri Lanka'")
            .withZone(COLOMBO);

    public byte[] generate(String invoiceNo, InvoiceRequest request, BigDecimal total, Instant generatedAt) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 56);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPageEvent(new PageNumbers(invoiceNo));
            document.open();
            document.addTitle("SwiftDrop invoice " + invoiceNo);
            document.addAuthor("SwiftDrop");

            PdfPTable header = new PdfPTable(1);
            header.setWidthPercentage(100);
            PdfPCell brand = cell("SwiftDrop", 25, Font.BOLD, Color.WHITE);
            brand.setBackgroundColor(INK);
            brand.setPadding(18);
            header.addCell(brand);
            PdfPCell title = cell("INVOICE", 12, Font.BOLD, INK);
            title.setBackgroundColor(YELLOW);
            title.setPadding(10);
            header.addCell(title);
            header.setSpacingAfter(20);
            document.add(header);

            PdfPTable info = new PdfPTable(new float[]{55, 45});
            info.setWidthPercentage(100);
            PdfPCell billedTo = cell("", 8, Font.BOLD, MUTED);
            billedTo.addElement(new Paragraph("BILLED TO", font(8, Font.BOLD, MUTED)));
            billedTo.addElement(new Paragraph(request.merchantName(), font(14, Font.BOLD, INK)));
            billedTo.addElement(new Paragraph("Merchant account " + request.merchantId(), font(8, Font.NORMAL, MUTED)));
            info.addCell(billedTo);
            PdfPCell details = cell("", 10, Font.NORMAL, INK);
            details.addElement(new Paragraph("INVOICE NUMBER", font(8, Font.BOLD, MUTED)));
            details.addElement(new Paragraph(invoiceNo, font(11, Font.BOLD, INK)));
            Paragraph period = new Paragraph("BILLING PERIOD", font(8, Font.BOLD, MUTED));
            period.setSpacingBefore(6);
            details.addElement(period);
            details.addElement(new Paragraph(DAY.format(request.periodStart()) + " to "
                    + DAY.format(request.periodEnd()), font(10, Font.NORMAL, INK)));
            Paragraph issued = new Paragraph("ISSUED", font(8, Font.BOLD, MUTED));
            issued.setSpacingBefore(6);
            details.addElement(issued);
            details.addElement(new Paragraph(DAY.format(generatedAt.atZone(COLOMBO)), font(10, Font.NORMAL, INK)));
            info.addCell(details);
            info.setSpacingAfter(22);
            document.add(info);

            PdfPTable lines = new PdfPTable(new float[]{7, 25, 48, 20});
            lines.setWidthPercentage(100);
            lines.setHeaderRows(1);
            headerCell(lines, "NO", Element.ALIGN_LEFT);
            headerCell(lines, "TRACKING", Element.ALIGN_LEFT);
            headerCell(lines, "DESCRIPTION", Element.ALIGN_LEFT);
            headerCell(lines, "AMOUNT (LKR)", Element.ALIGN_RIGHT);
            int number = 1;
            for (InvoiceRequest.Line line : request.lines()) {
                bodyCell(lines, String.valueOf(number++), Element.ALIGN_LEFT);
                bodyCell(lines, line.trackingNumber(), Element.ALIGN_LEFT);
                bodyCell(lines, line.description(), Element.ALIGN_LEFT);
                bodyCell(lines, money(line.amount()), Element.ALIGN_RIGHT);
            }
            document.add(lines);

            PdfPTable totals = new PdfPTable(new float[]{70, 30});
            totals.setWidthPercentage(100);
            totals.setSpacingBefore(12);
            totals.setKeepTogether(true);
            PdfPCell totalLabel = cell("TOTAL DUE (LKR)", 12, Font.BOLD, Color.WHITE);
            totalLabel.setBackgroundColor(INK);
            totalLabel.setPadding(14);
            totals.addCell(totalLabel);
            PdfPCell totalValue = cell(money(total), 14, Font.BOLD, YELLOW);
            totalValue.setBackgroundColor(INK);
            totalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalValue.setPadding(14);
            totals.addCell(totalValue);
            document.add(totals);

            Paragraph note = new Paragraph(request.lines().size() + (request.lines().size() == 1 ? " parcel" : " parcels")
                    + " delivered in the period above. Amounts are in Sri Lankan rupees (LKR).\nGenerated "
                    + TIME.format(generatedAt), font(8, Font.NORMAL, MUTED));
            note.setSpacingBefore(16);
            document.add(note);
        } catch (DocumentException exception) {
            throw new IllegalStateException("Invoice PDF could not be generated", exception);
        } finally {
            document.close();
        }
        return output.toByteArray();
    }

    private static void headerCell(PdfPTable table, String text, int alignment) {
        PdfPCell cell = cell(text, 8, Font.BOLD, Color.WHITE);
        cell.setBackgroundColor(INK);
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(8);
        table.addCell(cell);
    }

    private static void bodyCell(PdfPTable table, String text, int alignment) {
        PdfPCell cell = cell(text, 10, Font.NORMAL, INK);
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(8);
        cell.setBorderWidthBottom(0.5f);
        cell.setBorderColorBottom(MIST);
        table.addCell(cell);
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

    /** Writes "invoice number | Page n" at the bottom of every page. */
    private static final class PageNumbers extends PdfPageEventHelper {
        private final String invoiceNo;

        PageNumbers(String invoiceNo) { this.invoiceNo = invoiceNo; }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER,
                    new Phrase(invoiceNo + "  |  Page " + writer.getPageNumber(), font(8, Font.NORMAL, MUTED)),
                    (document.left() + document.right()) / 2, document.bottom() - 24, 0);
        }
    }
}
