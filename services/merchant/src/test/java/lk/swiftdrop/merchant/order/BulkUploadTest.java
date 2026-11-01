package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.ServiceUnavailableException;
import lk.swiftdrop.merchant.common.UnprocessableException;
import lk.swiftdrop.merchant.order.dto.Money;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/** Creating many orders from one CSV file. */
class BulkUploadTest extends AbstractOrderTest {

    private static final String HEADER = String.join(",", BulkCsvImporter.HEADER);
    private static final String EXPECTED_HEADER = "externalRef,recipientName,recipientPhone,pickupAddress,pickupCity,"
            + "pickupPostalCode,dropoffAddress,dropoffCity,dropoffPostalCode,weightKg,size,description,notes";
    private static final String BULK = PORTAL_ORDERS + "/bulk";

    // ---- building files ----

    private static Map<String, String> validCells(String externalRef) {
        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("externalRef", externalRef);
        cells.put("recipientName", "Nimali Perera");
        cells.put("recipientPhone", "+94771234567");
        cells.put("pickupAddress", "12 Galle Rd");
        cells.put("pickupCity", "Colombo");
        cells.put("pickupPostalCode", "00300");
        cells.put("dropoffAddress", "45 Temple St");
        cells.put("dropoffCity", "Kandy");
        cells.put("dropoffPostalCode", "20000");
        cells.put("weightKg", "2.5");
        cells.put("size", "SMALL");
        cells.put("description", "Phone case");
        cells.put("notes", "Call on arrival");
        return cells;
    }

    /** One CSV line with RFC 4180 quoting. */
    private static String line(Map<String, String> cells) {
        return cells.values().stream().map(BulkUploadTest::quote).collect(Collectors.joining(","));
    }

    private static String quote(String cell) {
        return cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")
                ? "\"" + cell.replace("\"", "\"\"") + "\"" : cell;
    }

    private static String valid(String externalRef) {
        return line(validCells(externalRef));
    }

    /** A valid row in which the given columns, passed as column and value pairs, are replaced. */
    private static String valid(String externalRef, String... columnsAndValues) {
        Map<String, String> cells = validCells(externalRef);
        for (int i = 0; i < columnsAndValues.length; i += 2) {
            cells.put(columnsAndValues[i], columnsAndValues[i + 1]);
        }
        return line(cells);
    }

    private static String file(String... lines) {
        return String.join("\r\n", lines) + "\r\n";
    }

    private static String fileOf(String... rows) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.addAll(Arrays.asList(rows));
        return file(lines.toArray(String[]::new));
    }

    private ResultActions upload(byte[] bytes, String filename, String contentType) throws Exception {
        return mockMvc.perform(multipart(BULK).file(new MockMultipartFile("file", filename, contentType, bytes))
                .with(TestAuth.merchant(OWNER)));
    }

    private ResultActions upload(String csv) throws Exception {
        return upload(csv.getBytes(StandardCharsets.UTF_8), "orders.csv", "text/csv");
    }

    private static String fileKeyPrefix(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return "bulk:" + hex.substring(0, 16) + ":";
    }

    private static List<String> errorsOf(JsonNode row) {
        List<String> errors = new ArrayList<>();
        row.get("errors").forEach(error -> errors.add(error.asText()));
        return errors;
    }

    // ---- happy path ----

    @Test
    void createsAnOrderForEveryRow() throws Exception {
        byte[] bytes = fileOf(valid("B-1"), valid("B-2"), valid("B-3")).getBytes(StandardCharsets.UTF_8);

        JsonNode result = body(upload(bytes, "orders.csv", "text/csv")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[0].row").value(2))
                .andExpect(jsonPath("$.results[0].status").value("CREATED"))
                .andExpect(jsonPath("$.results[0].trackingNo").value("SD24000001"))
                .andExpect(jsonPath("$.results[1].row").value(3))
                .andExpect(jsonPath("$.results[2].row").value(4))
                .andExpect(jsonPath("$.results[0].errors").doesNotExist()));

        List<MerchantOrder> stored = orders.findAll();
        assertThat(stored).hasSize(3).allSatisfy(order -> {
            assertThat(order.getSource()).isEqualTo(OrderSource.BULK);
            assertThat(order.isSandbox()).isFalse();
            assertThat(order.getMerchantId()).isEqualTo(merchant.getId());
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        });
        String prefix = fileKeyPrefix(bytes);
        assertThat(stored).extracting(MerchantOrder::getIdempotencyKey)
                .containsExactlyInAnyOrder(prefix + "2", prefix + "3", prefix + "4");
        assertThat(result.get("results").get(1).get("orderId").asText())
                .isIn(stored.stream().map(order -> order.getId().toString()).toList());
        verify(parcelClient, times(3)).createParcel(any());
    }

    @Test
    void rowResultHasExactlyTheFieldsOfTheFrontendModel() throws Exception {
        JsonNode result = body(upload(fileOf(valid("B-1"), valid("B-2", "dropoffCity", ""))).andExpect(status().isOk()));

        List<String> top = new ArrayList<>();
        result.fieldNames().forEachRemaining(top::add);
        assertThat(top).containsExactlyInAnyOrder("totalRows", "created", "failed", "results");
        List<String> created = new ArrayList<>();
        result.get("results").get(0).fieldNames().forEachRemaining(created::add);
        assertThat(created).containsExactlyInAnyOrder("row", "status", "trackingNo", "orderId");
        List<String> failed = new ArrayList<>();
        result.get("results").get(1).fieldNames().forEachRemaining(failed::add);
        assertThat(failed).containsExactlyInAnyOrder("row", "status", "errors");
    }

    @Test
    void theTemplateItselfIsAValidFile() throws Exception {
        String template = mockMvc.perform(get(BULK + "/template").with(TestAuth.merchant(OWNER)))
                .andReturn().getResponse().getContentAsString();

        upload(template).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.failed").value(0));
    }

    // ---- bad rows never stop the others ----

    @Test
    void invalidRowsReportFieldLevelErrorsAndTheOthersAreStillCreated() throws Exception {
        String badRow = valid("B-2", "dropoffCity", "", "recipientPhone", "12ab");

        JsonNode result = body(upload(fileOf(valid("B-1"), badRow, valid("B-3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.results[0].status").value("CREATED"))
                .andExpect(jsonPath("$.results[1].row").value(3))
                .andExpect(jsonPath("$.results[1].status").value("FAILED"))
                .andExpect(jsonPath("$.results[1].trackingNo").doesNotExist())
                .andExpect(jsonPath("$.results[2].status").value("CREATED")));

        assertThat(errorsOf(result.get("results").get(1))).hasSize(2);
        assertThat(errorsOf(result.get("results").get(1)).get(0)).startsWith("recipientPhone: ");
        assertThat(errorsOf(result.get("results").get(1)).get(1)).isEqualTo("dropoffCity: must not be blank");
        assertThat(orders.count()).isEqualTo(2);
        verify(parcelClient, times(2)).createParcel(any());
    }

    @Test
    void everyKindOfRowProblemIsReportedByColumnWithoutEchoingTheValue() throws Exception {
        String badNumber = valid("B-1", "weightKg", "heavy-ish");
        String badSize = valid("B-2", "size", "HUGE");
        String blankWeight = valid("B-3", "weightKg", " ");
        String blankSize = valid("B-4", "size", "");
        String tooLight = valid("B-5", "weightKg", "0");
        String tooHeavy = valid("B-6", "weightKg", "50.01");
        String badRef = valid("B 7");
        String longName = valid("B-8", "recipientName", "N".repeat(121));
        String controlCharacter = valid("B-9", "notes", "bell\u0007here");
        String threeColumns = "B-10,Someone,+94771234567";
        String blankRecipient = valid("B-11", "recipientName", "   ");

        JsonNode result = body(upload(fileOf(badNumber, badSize, blankWeight, blankSize, tooLight, tooHeavy, badRef,
                longName, controlCharacter, threeColumns, blankRecipient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.failed").value(11)));

        List<JsonNode> rows = new ArrayList<>();
        result.get("results").forEach(rows::add);
        assertThat(errorsOf(rows.get(0))).containsExactly("weightKg: must be a number");
        assertThat(errorsOf(rows.get(1))).containsExactly("size: must be SMALL, MEDIUM or LARGE");
        assertThat(errorsOf(rows.get(2))).containsExactly("weightKg: must not be blank");
        assertThat(errorsOf(rows.get(3))).containsExactly("size: must not be blank");
        assertThat(errorsOf(rows.get(4))).singleElement().asString().startsWith("weightKg: ");
        assertThat(errorsOf(rows.get(5))).singleElement().asString().startsWith("weightKg: ");
        assertThat(errorsOf(rows.get(6))).singleElement().asString().startsWith("externalRef: ");
        assertThat(errorsOf(rows.get(7))).singleElement().asString().startsWith("recipientName: ");
        assertThat(errorsOf(rows.get(8))).singleElement().asString().startsWith("notes: ");
        assertThat(errorsOf(rows.get(9))).containsExactly("The row has 3 columns but 13 are expected");
        assertThat(errorsOf(rows.get(10))).containsExactly("recipientName: must not be blank");
        String allErrors = String.join("|", rows.stream().flatMap(row -> errorsOf(row).stream()).toList());
        assertThat(allErrors).doesNotContain("heavy-ish").doesNotContain("HUGE").doesNotContain("Nimali")
                .doesNotContain("B 7").doesNotContain("Someone");
        assertThat(orders.count()).isZero();
        verifyNoInteractions(parcelClient);
    }

    @Test
    void rowNumbersCountRecordsEvenWhenACellContainsLineBreaksAndBlankLinesAreSkipped() throws Exception {
        String withLineBreak = valid("B-2", "notes", "first line\nsecond line");
        String csv = HEADER + "\r\n" + valid("B-1") + "\r\n\r\n" + withLineBreak + "\r\n" + valid("B-3") + "\r\n";

        upload(csv).andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].row").value(2))
                .andExpect(jsonPath("$.results[0].status").value("CREATED"))
                .andExpect(jsonPath("$.results[1].row").value(3))
                .andExpect(jsonPath("$.results[1].status").value("FAILED"))
                .andExpect(jsonPath("$.results[1].errors[0]").value(startsWith("notes: ")))
                .andExpect(jsonPath("$.results[2].row").value(4))
                .andExpect(jsonPath("$.results[2].status").value("CREATED"));
    }

    @Test
    void sameExternalRefTwiceInTheFileFailsTheSecondRow() throws Exception {
        upload(fileOf(valid("B-1"), valid("B-1"), valid("B-2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.results[1].status").value("FAILED"))
                .andExpect(jsonPath("$.results[1].errors[0]").value("externalRef: already used by another order"))
                .andExpect(jsonPath("$.results[2].status").value("CREATED"));

        verify(parcelClient, times(2)).createParcel(any());
    }

    @Test
    void externalRefOfAnExistingOrderFailsOnlyThatRow() throws Exception {
        orderFixtures.order(merchant).externalRef("B-1").save();

        upload(fileOf(valid("B-1"), valid("B-2")))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.results[0].errors[0]").value("externalRef: already used by another order"))
                .andExpect(jsonPath("$.results[1].status").value("CREATED"));
    }

    @Test
    void parcelServiceProblemsFailOnlyTheAffectedRowsAndRevealNothingFromUpstream() throws Exception {
        doAnswer(invocation -> new ParcelBooking("p1", "SD1", new Money(new BigDecimal("450.00"), "LKR")))
                .doThrow(new UnprocessableException(ErrorCode.PARCEL_REJECTED, "secret upstream detail"))
                .doThrow(new ServiceUnavailableException(ErrorCode.PARCEL_SERVICE_UNAVAILABLE,
                        "The parcel service is unavailable. Please try again shortly"))
                .doAnswer(invocation -> new ParcelBooking("p4", "SD4", new Money(new BigDecimal("450.00"), "LKR")))
                .when(parcelClient).createParcel(any());

        JsonNode result = body(upload(fileOf(valid("B-1"), valid("B-2"), valid("B-3"), valid("B-4")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.failed").value(2)));

        assertThat(errorsOf(result.get("results").get(1))).containsExactly("The parcel service rejected this order");
        assertThat(errorsOf(result.get("results").get(2)))
                .containsExactly("The parcel service is unavailable. Please try again shortly");
        assertThat(orders.findAll()).extracting(MerchantOrder::getExternalRef).containsExactlyInAnyOrder("B-1", "B-4");
    }

    // ---- uploading the same file again ----

    @Test
    void uploadingTheSameFileAgainCreatesNothingNewAndReportsTheSameOrders() throws Exception {
        byte[] bytes = fileOf(valid("B-1"), valid("B-2")).getBytes(StandardCharsets.UTF_8);
        JsonNode first = body(upload(bytes, "orders.csv", "text/csv").andExpect(status().isOk()));

        JsonNode second = body(upload(bytes, "again.csv", "text/csv").andExpect(status().isOk()));

        assertThat(second).isEqualTo(first);
        assertThat(second.get("created").asInt()).isEqualTo(2);
        assertThat(orders.count()).isEqualTo(2);
        verify(parcelClient, times(2)).createParcel(any());
    }

    @Test
    void aChangedFileIsANewUploadThatCannotReuseExistingExternalRefs() throws Exception {
        upload(fileOf(valid("B-1"))).andExpect(jsonPath("$.created").value(1));

        upload(fileOf(valid("B-1"), valid("B-2")))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.results[0].errors[0]").value("externalRef: already used by another order"))
                .andExpect(jsonPath("$.results[1].status").value("CREATED"));
    }

    // ---- files that are rejected as a whole ----

    @ParameterizedTest
    @ValueSource(strings = {
            "recipientName,externalRef,recipientPhone,pickupAddress,pickupCity,pickupPostalCode,dropoffAddress,dropoffCity,dropoffPostalCode,weightKg,size,description,notes",
            "externalref,recipientName,recipientPhone,pickupAddress,pickupCity,pickupPostalCode,dropoffAddress,dropoffCity,dropoffPostalCode,weightKg,size,description,notes",
            "externalRef,recipientName,recipientPhone,pickupAddress,pickupCity,pickupPostalCode,dropoffAddress,dropoffCity,dropoffPostalCode,weightKg,size,description",
            "externalRef,recipientName,recipientPhone,pickupAddress,pickupCity,pickupPostalCode,dropoffAddress,dropoffCity,dropoffPostalCode,weightKg,size,description,notes,extra",
            "externalRef,recipientName,recipientPhone,pickupAddress,pickupCity,pickupPostalCode,dropoffAddress,dropoffCity,dropoffPostalCode,weightKg,size,description,notes ",
            "externalRef;recipientName;recipientPhone;pickupAddress;pickupCity;pickupPostalCode;dropoffAddress;dropoffCity;dropoffPostalCode;weightKg;size;description;notes",
            "ORDER-1,Nimali Perera,+94771234567,12 Galle Rd,Colombo,00300,45 Temple St,Kandy,20000,2.5,SMALL,Phone case,Call on arrival"})
    void wrongHeaderRejectsTheWholeFile(String header) throws Exception {
        upload(file(header, valid("B-1"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString(EXPECTED_HEADER)));

        assertThat(orders.count()).isZero();
        verifyNoInteractions(parcelClient);
    }

    @Test
    void headerWithoutRowsIsRejected() throws Exception {
        upload(HEADER + "\r\n").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("no orders")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\r\n\r\n", "\n", "﻿"})
    void emptyFileIsRejected(String content) throws Exception {
        upload(content).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("empty")));
    }

    @Test
    void fileWithMoreRowsThanAllowedIsRejectedBeforeAnythingIsCreated() throws Exception {
        String[] rows = new String[501];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = valid("B-" + i);
        }

        upload(fileOf(rows)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("501 orders but at most 500")));

        assertThat(orders.count()).isZero();
        verifyNoInteractions(parcelClient);
    }

    @Test
    void fileWithTheMaximumNumberOfRowsIsProcessed() throws Exception {
        String[] rows = new String[500];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = valid("B-" + i);
        }

        upload(fileOf(rows)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(500))
                .andExpect(jsonPath("$.created").value(500))
                .andExpect(jsonPath("$.results[499].row").value(501));
    }

    @Test
    void fileLargerThanTheLimitIsTooLarge() throws Exception {
        byte[] tooBig = new byte[1_048_577];
        Arrays.fill(tooBig, (byte) 'a');

        upload(tooBig, "orders.csv", "text/csv").andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));

        assertThat(orders.count()).isZero();
    }

    @Test
    void fileThatIsNotUtf8IsRejected() throws Exception {
        byte[] latin1 = fileOf(valid("B-1", "recipientName", "José")).getBytes(StandardCharsets.ISO_8859_1);

        upload(latin1, "orders.csv", "text/csv").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("UTF-8")));
    }

    @Test
    void unbalancedQuoteMakesTheFileInvalid() throws Exception {
        upload(HEADER + "\r\n" + "B-1,\"Nimali \"Perera\" ,x\r\n").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("valid CSV")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"orders.txt|text/plain", "orders.xlsx|application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "orders|application/octet-stream", "orders.csv.exe|application/x-msdownload"})
    void fileThatIsNotACsvIsRejected(String nameAndType) throws Exception {
        String[] parts = nameAndType.split("\\|");

        upload(fileOf(valid("B-1")).getBytes(StandardCharsets.UTF_8), parts[0], parts[1])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("CSV")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"orders.csv|text/csv", "ORDERS.CSV|application/octet-stream", "export.dat|text/csv",
            "export.dat|text/csv; charset=utf-8", "export.dat|application/vnd.ms-excel", "export.dat|application/csv"})
    void csvIsRecognisedByNameOrByContentType(String nameAndType) throws Exception {
        String[] parts = nameAndType.split("\\|");

        upload(fileOf(valid("B-1")).getBytes(StandardCharsets.UTF_8), parts[0], parts[1])
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));
    }

    @Test
    void fileWithoutThePartNamedFileIsRejected() throws Exception {
        mockMvc.perform(multipart(BULK).file(new MockMultipartFile("upload", "o.csv", "text/csv", new byte[] {1}))
                        .with(TestAuth.merchant(OWNER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("file"));
    }

    @Test
    void jsonBodyInsteadOfMultipartIsUnsupported() throws Exception {
        mockMvc.perform(post(BULK).with(TestAuth.merchant(OWNER)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ---- cell content ----

    @Test
    void byteOrderMarkAtTheStartOfTheFileIsTolerated() throws Exception {
        byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] text = fileOf(valid("B-1")).getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[bom.length + text.length];
        System.arraycopy(bom, 0, bytes, 0, bom.length);
        System.arraycopy(text, 0, bytes, bom.length, text.length);

        upload(bytes, "orders.csv", "text/csv").andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));
    }

    @Test
    void quotedCellsMayContainCommasAndQuotes() throws Exception {
        Map<String, String> cells = validCells("B-1");
        cells.put("pickupAddress", "12, Galle Rd, Fort");
        cells.put("description", "The \"best\" case, ever");

        upload(fileOf(line(cells))).andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1));

        MerchantOrder stored = orders.findAll().get(0);
        assertThat(stored.getPickupAddress()).isEqualTo("12, Galle Rd, Fort");
        assertThat(stored.getDescription()).isEqualTo("The \"best\" case, ever");
    }

    @Test
    void multiByteCharactersSurviveTheUpload() throws Exception {
        upload(fileOf(valid("B-1", "recipientName", "සුනිල් පෙරේරා"))).andExpect(jsonPath("$.created").value(1));

        assertThat(orders.findAll().get(0).getRecipientName()).isEqualTo("සුනිල් පෙරේරා");
    }

    @Test
    void cellsThatLookLikeFormulasAreStoredAsTheyAreAndReturnedAsTheyAre() throws Exception {
        Map<String, String> cells = validCells("B-1");
        cells.put("recipientName", "-Bob");
        cells.put("pickupAddress", "@home");
        cells.put("description", "+SUM(1,1)");
        cells.put("notes", "=HYPERLINK(\"http://evil.example\",\"click\")");

        upload(fileOf(line(cells))).andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1));

        MerchantOrder stored = orders.findAll().get(0);
        assertThat(stored.getRecipientName()).isEqualTo("-Bob");
        assertThat(stored.getPickupAddress()).isEqualTo("@home");
        assertThat(stored.getDescription()).isEqualTo("+SUM(1,1)");
        assertThat(stored.getNotes()).isEqualTo("=HYPERLINK(\"http://evil.example\",\"click\")");
        mockMvc.perform(get(PORTAL_ORDERS + "/" + stored.getId()).with(TestAuth.merchant(OWNER)))
                .andExpect(jsonPath("$.notes").value("=HYPERLINK(\"http://evil.example\",\"click\")"));
    }

    // ---- template ----

    @Test
    void templateHasTheExactHeaderAndOneExampleRow() throws Exception {
        String body = mockMvc.perform(get(BULK + "/template").with(TestAuth.merchant(OWNER)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"swiftdrop-orders-template.csv\""))
                .andReturn().getResponse().getContentAsString();

        String[] lines = body.split("\r\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).isEqualTo(EXPECTED_HEADER);
        assertThat(body).endsWith("\r\n");
        assertThat(lines[1].split(",", -1)).hasSize(13);
    }

    @Test
    void noCellOfTheTemplateStartsWithAFormulaCharacter() throws Exception {
        String body = mockMvc.perform(get(BULK + "/template").with(TestAuth.merchant(OWNER)))
                .andReturn().getResponse().getContentAsString();

        for (String line : body.split("\r\n")) {
            for (String cell : line.split(",", -1)) {
                assertThat(cell).doesNotStartWith("=").doesNotStartWith("+").doesNotStartWith("-")
                        .doesNotStartWith("@");
            }
        }
    }
}
