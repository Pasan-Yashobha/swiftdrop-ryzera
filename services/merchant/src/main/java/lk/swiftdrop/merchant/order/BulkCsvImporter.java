package lk.swiftdrop.merchant.order;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lk.swiftdrop.merchant.common.ApiException;
import lk.swiftdrop.merchant.common.BadRequestException;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.config.AppProperties;
import lk.swiftdrop.merchant.order.dto.BulkResult;
import lk.swiftdrop.merchant.order.dto.BulkRowResult;
import lk.swiftdrop.merchant.order.dto.CreateOrderRequest;
import lk.swiftdrop.merchant.order.dto.OrderAddress;
import lk.swiftdrop.merchant.order.dto.OrderParcel;
import lk.swiftdrop.merchant.order.dto.OrderRecipient;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Creates live orders from an uploaded CSV file (CONTRACT section 5.4).
 *
 * <p>A file that cannot be processed as a whole is rejected before anything is created: too big (413), or empty,
 * not UTF-8, not CSV, with the wrong header or too many rows (400). Otherwise every row is validated by the same
 * Bean Validation rules as the JSON API and created through {@link OrderService}; a row that fails reports field
 * level messages and never stops the others. Row {@code n} of a file uses the idempotency key
 * {@code bulk:<first 16 hex digits of the file's SHA-256>:<n>}, so uploading the same file again creates nothing
 * new and reports the same orders.
 *
 * <p>Cell values are stored as given, including values that start with {@code = + - @}: protecting spreadsheets
 * is the job of whoever exports the data.
 */
@Component
public class BulkCsvImporter {

    /** The header row, exactly as the template provides it (case-sensitive, in this order). */
    static final List<String> HEADER = List.of("externalRef", "recipientName", "recipientPhone", "pickupAddress",
            "pickupCity", "pickupPostalCode", "dropoffAddress", "dropoffCity", "dropoffPostalCode", "weightKg",
            "size", "description", "notes");

    /** A row of the template. No cell starts with {@code = + - @}, so the template is safe to open in a spreadsheet. */
    private static final List<String> EXAMPLE_ROW = List.of("ORDER-1001", "Nimali Perera", "0771234567",
            "12 Galle Rd", "Colombo", "00300", "45 Temple St", "Kandy", "20000", "2.5", "SMALL", "Phone case",
            "Call on arrival");

    /** Bean Validation property paths of {@link CreateOrderRequest} and the column each one is read from. */
    private static final Map<String, String> COLUMN_OF_PROPERTY = Map.ofEntries(
            Map.entry("externalRef", "externalRef"),
            Map.entry("recipient.name", "recipientName"),
            Map.entry("recipient.phone", "recipientPhone"),
            Map.entry("pickup.addressLine", "pickupAddress"),
            Map.entry("pickup.city", "pickupCity"),
            Map.entry("pickup.postalCode", "pickupPostalCode"),
            Map.entry("dropoff.addressLine", "dropoffAddress"),
            Map.entry("dropoff.city", "dropoffCity"),
            Map.entry("dropoff.postalCode", "dropoffPostalCode"),
            Map.entry("parcel.weightKg", "weightKg"),
            Map.entry("parcel.size", "size"),
            Map.entry("parcel.description", "description"),
            Map.entry("notes", "notes"));

    private static final Set<String> CSV_CONTENT_TYPES = Set.of("text/csv", "application/csv", "application/vnd.ms-excel");
    private static final char BYTE_ORDER_MARK = '﻿';
    private static final int FILE_FINGERPRINT_LENGTH = 16;
    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder().setIgnoreEmptyLines(true).get();

    /** A field level problem of one row. */
    private record RowProblem(String column, String message) {

        String describe() {
            return column + ": " + message;
        }
    }

    private final OrderService orderService;
    private final Validator validator;
    private final AppProperties.Bulk limits;

    BulkCsvImporter(OrderService orderService, Validator validator, AppProperties properties) {
        this.orderService = orderService;
        this.validator = validator;
        this.limits = properties.bulk();
    }

    /** The CSV template: the header row and one example row. */
    public String template() {
        String lineEnd = FORMAT.getRecordSeparator();
        return FORMAT.format(HEADER.toArray()) + lineEnd + FORMAT.format(EXAMPLE_ROW.toArray()) + lineEnd;
    }

    /**
     * @throws ApiException {@code PAYLOAD_TOO_LARGE} (413) or {@code VALIDATION_FAILED} (400) for a file that is
     *                      rejected as a whole
     */
    public BulkResult importOrders(UUID merchantId, MultipartFile file) {
        requireCsv(file);
        if (file.getSize() > limits.maxBytes()) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "The file is larger than the limit of " + limits.maxBytes() + " bytes");
        }
        byte[] content = read(file);
        List<CSVRecord> records = parse(decode(content));
        if (records.isEmpty()) {
            throw invalid("The file is empty");
        }
        if (!records.get(0).toList().equals(HEADER)) {
            throw invalid("The first row must be exactly this header: " + String.join(",", HEADER));
        }
        List<CSVRecord> rows = records.subList(1, records.size());
        if (rows.isEmpty()) {
            throw invalid("The file contains a header but no orders");
        }
        if (rows.size() > limits.maxRows()) {
            throw invalid("The file has " + rows.size() + " orders but at most " + limits.maxRows() + " are allowed");
        }
        String keyPrefix = "bulk:" + Sha256.hex(content).substring(0, FILE_FINGERPRINT_LENGTH) + ":";
        return BulkResult.of(rows.stream().map(row -> importRow(merchantId, keyPrefix, row)).toList());
    }

    private static void requireCsv(MultipartFile file) {
        String name = file.getOriginalFilename();
        String type = file.getContentType();
        boolean csvName = name != null && name.toLowerCase(Locale.ROOT).endsWith(".csv");
        boolean csvType = type != null && CSV_CONTENT_TYPES.contains(type.split(";")[0].trim().toLowerCase(Locale.ROOT));
        if (!csvName && !csvType) {
            throw invalid("The file must be a CSV file (.csv)");
        }
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw invalid("The file could not be read");
        }
    }

    /** Strict UTF-8; a leading byte order mark (written by Excel) is dropped. */
    private static String decode(byte[] content) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
            return !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            throw invalid("The file must be encoded as UTF-8");
        }
    }

    private static List<CSVRecord> parse(String text) {
        try (CSVParser parser = FORMAT.parse(new StringReader(text))) {
            return parser.getRecords();
        } catch (IOException | UncheckedIOException e) {
            throw invalid("The file is not a valid CSV file");
        }
    }

    private BulkRowResult importRow(UUID merchantId, String keyPrefix, CSVRecord record) {
        int row = (int) record.getRecordNumber();
        if (record.size() != HEADER.size()) {
            return BulkRowResult.failed(row,
                    List.of("The row has " + record.size() + " columns but " + HEADER.size() + " are expected"));
        }
        List<RowProblem> problems = new ArrayList<>();
        CreateOrderRequest request = toRequest(record, problems);
        problems.addAll(violationsOf(request, problems));
        if (!problems.isEmpty()) {
            return BulkRowResult.failed(row, problems.stream()
                    .sorted(Comparator.comparingInt((RowProblem p) -> HEADER.indexOf(p.column()))
                            .thenComparing(RowProblem::message))
                    .map(RowProblem::describe)
                    .toList());
        }
        try {
            OrderCreation created =
                    orderService.create(new NewOrder(merchantId, request, OrderSource.BULK, false, keyPrefix + row));
            return BulkRowResult.created(row, created.order());
        } catch (ApiException e) {
            return BulkRowResult.failed(row, List.of(describe(e)));
        }
    }

    /** Messages that belong in a row report: fixed text, nothing taken from the order or from the parcel service. */
    private static String describe(ApiException e) {
        return switch (e.getCode()) {
            case DUPLICATE_EXTERNAL_REF -> "externalRef: already used by another order";
            case PARCEL_REJECTED -> "The parcel service rejected this order";
            default -> e.getMessage();
        };
    }

    /** Reads the cells; weight and size are parsed here and report their own problem when they cannot be. */
    private static CreateOrderRequest toRequest(CSVRecord record, List<RowProblem> problems) {
        return new CreateOrderRequest(
                cell(record, "externalRef"),
                new OrderRecipient(cell(record, "recipientName"), cell(record, "recipientPhone")),
                new OrderAddress(cell(record, "pickupAddress"), cell(record, "pickupCity"),
                        cell(record, "pickupPostalCode")),
                new OrderAddress(cell(record, "dropoffAddress"), cell(record, "dropoffCity"),
                        cell(record, "dropoffPostalCode")),
                new OrderParcel(weight(cell(record, "weightKg"), problems), size(cell(record, "size"), problems),
                        cell(record, "description")),
                cell(record, "notes"));
    }

    private List<RowProblem> violationsOf(CreateOrderRequest request, List<RowProblem> parseProblems) {
        Set<String> alreadyReported = parseProblems.stream().map(RowProblem::column).collect(Collectors.toSet());
        List<RowProblem> violations = new ArrayList<>();
        for (ConstraintViolation<CreateOrderRequest> violation : validator.validate(request)) {
            String property = violation.getPropertyPath().toString();
            String column = COLUMN_OF_PROPERTY.getOrDefault(property, property);
            if (!alreadyReported.contains(column)) {
                violations.add(new RowProblem(column, violation.getMessage()));
            }
        }
        return violations;
    }

    private static String cell(CSVRecord record, String column) {
        return record.get(HEADER.indexOf(column));
    }

    private static BigDecimal weight(String cell, List<RowProblem> problems) {
        if (cell.isBlank()) {
            problems.add(new RowProblem("weightKg", "must not be blank"));
            return null;
        }
        try {
            return new BigDecimal(cell.trim());
        } catch (NumberFormatException notANumber) {
            problems.add(new RowProblem("weightKg", "must be a number"));
            return null;
        }
    }

    private static ParcelSize size(String cell, List<RowProblem> problems) {
        if (cell.isBlank()) {
            problems.add(new RowProblem("size", "must not be blank"));
            return null;
        }
        try {
            return ParcelSize.valueOf(cell.trim());
        } catch (IllegalArgumentException unknownSize) {
            problems.add(new RowProblem("size", "must be SMALL, MEDIUM or LARGE"));
            return null;
        }
    }

    private static BadRequestException invalid(String message) {
        return new BadRequestException(ErrorCode.VALIDATION_FAILED, message);
    }
}
