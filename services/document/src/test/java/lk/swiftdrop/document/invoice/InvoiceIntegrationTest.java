package lk.swiftdrop.document.invoice;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers
class InvoiceIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT_31 = LocalDate.of(2026, 10, 31);

    @Autowired InvoiceService invoices;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clearDisposableDatabase() {
        jdbc.sql("TRUNCATE invoice_line, invoice").update();
    }

    private static InvoiceRequest.Line line(String tracking, String description, String amount) {
        return new InvoiceRequest.Line(UUID.randomUUID(), tracking, description, new BigDecimal(amount));
    }

    private static InvoiceRequest request(String merchant, LocalDate start, LocalDate end,
                                          List<InvoiceRequest.Line> lines) {
        return new InvoiceRequest(merchant, "Ceylon Spice Traders", start, end, lines);
    }

    private static List<InvoiceRequest.Line> threeLines() {
        return List.of(line("SD-1001-0001", "Colombo to Kandy, Standard", "550.00"),
                line("SD-1001-0002", "Colombo to Kandy, Express", "687.50"),
                line("SD-1001-0003", "Kandy to Galle, Express", "618.75"));
    }

    private RequestPostProcessor user(String id, String role) {
        return jwt().jwt(token -> token.subject(id)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private ResultActions generateAs(String id, String role, Object body) throws Exception {
        return mvc.perform(post("/api/v1/invoices/generate").with(user(id, role))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    @Test
    void adminGeneratesInvoiceAndOwnerDownloadsPdfWithServerComputedTotal() throws Exception {
        var request = request("merchant-a", OCT_1, OCT_31, threeLines());
        String body = generateAs("admin", "ADMIN", request).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(1856.25))
                .andExpect(jsonPath("$.lineCount").value(3))
                .andExpect(jsonPath("$.merchantId").value("merchant-a"))
                .andReturn().getResponse().getContentAsString();
        var created = mapper.readTree(body);
        String id = created.get("id").asText();
        String number = created.get("invoiceNo").asText();
        assertThat(number).matches("INV-202610-[0-9A-F]{12}");

        byte[] pdf = mvc.perform(get("/api/v1/invoices/" + id + "/download").with(user("merchant-a", "MERCHANT")))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("invoice-" + number)))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("INVOICE", number, "BILLED TO", "Ceylon Spice Traders", "01 Oct 2026",
                    "31 Oct 2026", "SD-1001-0001", "SD-1001-0002", "SD-1001-0003", "550.00", "687.50", "618.75",
                    "TOTAL DUE (LKR)", "1856.25");
        } finally { reader.close(); }
        Path preview = Path.of("target", "qa", "invoice-sample.pdf");
        Files.createDirectories(preview.getParent());
        Files.write(preview, pdf);

        mvc.perform(get("/api/v1/invoices/" + id).with(user("merchant-a", "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.totalAmount").value(1856.25))
                .andExpect(jsonPath("$.lines.length()").value(3))
                .andExpect(jsonPath("$.lines[0].lineNo").value(1))
                .andExpect(jsonPath("$.lines[2].trackingNumber").value("SD-1001-0003"));
    }

    @Test
    void merchantsOnlySeeTheirOwnInvoicesWhileAdminSeesAll() throws Exception {
        var mine = invoices.generate(request("merchant-a", OCT_1, OCT_31, threeLines()));
        invoices.generate(request("merchant-b", OCT_1, OCT_31, List.of(line("SD-2001-0001", "Colombo to Jaffna", "900.00"))));

        mvc.perform(get("/api/v1/invoices/" + mine.id()).with(user("merchant-b", "MERCHANT")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/invoices/" + mine.id() + "/download").with(user("merchant-b", "MERCHANT")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/invoices").with(user("merchant-a", "MERCHANT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(mine.id().toString()));
        mvc.perform(get("/api/v1/invoices").with(user("someone-else", "MERCHANT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/v1/invoices").with(user("admin", "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/invoices/" + mine.id() + "/download").with(user("admin", "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void onlyAdminGeneratesAndOnlyMerchantsOrAdminRead() throws Exception {
        var request = request("merchant-a", OCT_1, OCT_31, threeLines());
        generateAs("merchant-a", "MERCHANT", request).andExpect(status().isForbidden());
        generateAs("customer-a", "CUSTOMER", request).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/invoices/generate").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))).andExpect(status().isUnauthorized());
        assertThat(count("invoice")).isZero();

        var invoice = invoices.generate(request);
        mvc.perform(get("/api/v1/invoices").with(user("customer-a", "CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/invoices/" + invoice.id()).with(user("courier-a", "COURIER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/invoices/" + invoice.id() + "/download")).andExpect(status().isUnauthorized());
    }

    @Test
    void resubmittingTheSameInvoiceReturnsTheExistingOneWhateverTheLineOrderOrScale() {
        var lines = threeLines();
        var original = invoices.generate(request("merchant-a", OCT_1, OCT_31, lines));

        var reordered = new ArrayList<>(lines);
        Collections.reverse(reordered);
        assertThat(invoices.generate(request("merchant-a", OCT_1, OCT_31, reordered)).id()).isEqualTo(original.id());

        var rescaled = lines.stream().map(l -> new InvoiceRequest.Line(l.parcelId(), l.trackingNumber(),
                l.description(), l.amount().stripTrailingZeros())).toList();
        assertThat(invoices.generate(request("merchant-a", OCT_1, OCT_31, rescaled)).id()).isEqualTo(original.id());

        assertThat(count("invoice")).isEqualTo(1);
        assertThat(count("invoice_line")).isEqualTo(3);
    }

    @Test
    void differentContentForTheSameMerchantAndPeriodIsRejectedAndTheOriginalKept() {
        var lines = threeLines();
        var original = invoices.generate(request("merchant-a", OCT_1, OCT_31, lines));
        var changed = new ArrayList<>(lines);
        changed.set(0, new InvoiceRequest.Line(lines.get(0).parcelId(), lines.get(0).trackingNumber(),
                lines.get(0).description(), new BigDecimal("999.00")));

        assertThatThrownBy(() -> invoices.generate(request("merchant-a", OCT_1, OCT_31, changed)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
        assertThat(invoices.get(original.id(), "merchant-a", false).invoice().totalAmount())
                .isEqualByComparingTo("1856.25");
        assertThat(count("invoice")).isEqualTo(1);
    }

    @Test
    void aParcelCanOnlyBeBilledOnceAndAFailedInvoiceLeavesNothingBehind() {
        var shared = line("SD-1001-0001", "Colombo to Kandy, Standard", "550.00");
        invoices.generate(request("merchant-a", OCT_1, OCT_31, List.of(shared)));

        var november = request("merchant-a", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                List.of(line("SD-3001-0001", "Colombo to Matara", "400.00"), shared));
        assertThatThrownBy(() -> invoices.generate(november)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(409));

        assertThat(count("invoice")).isEqualTo(1);
        assertThat(count("invoice_line")).isEqualTo(1);
    }

    @Test
    void simultaneousIdenticalSubmissionsProduceOneInvoiceWithOneSetOfLines() {
        var request = request("merchant-a", OCT_1, OCT_31, threeLines());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = CompletableFuture.supplyAsync(() -> invoices.generate(request), pool);
            var second = CompletableFuture.supplyAsync(() -> invoices.generate(request), pool);
            assertThat(first.join().id()).isEqualTo(second.join().id());
        }
        assertThat(count("invoice")).isEqualTo(1);
        assertThat(count("invoice_line")).isEqualTo(3);
    }

    @Test
    void invalidInvoicesAreRejectedBeforeAnythingIsStored() throws Exception {
        var one = List.of(line("SD-1001-0001", "Colombo to Kandy", "550.00"));
        UUID parcel = UUID.randomUUID();
        var twice = List.of(new InvoiceRequest.Line(parcel, "SD-1", "first", BigDecimal.TEN),
                new InvoiceRequest.Line(parcel, "SD-2", "second", BigDecimal.TEN));
        var tooMany = IntStream.range(0, InvoiceRequest.MAX_LINES + 1)
                .mapToObj(i -> line("SD-" + i, "Parcel " + i, "1.00")).toList();

        generateAs("admin", "ADMIN", request("merchant-a", OCT_1, OCT_31, List.of())).andExpect(status().isBadRequest());
        generateAs("admin", "ADMIN", request("merchant-a", OCT_31, OCT_1, one)).andExpect(status().isBadRequest());
        generateAs("admin", "ADMIN", request("merchant-a", OCT_1, OCT_31, twice)).andExpect(status().isBadRequest());
        generateAs("admin", "ADMIN", request("merchant-a", OCT_1, OCT_31, tooMany)).andExpect(status().isBadRequest());
        generateAs("admin", "ADMIN", request("merchant-a", OCT_1, OCT_31,
                List.of(line("SD-1001-0001", "Negative", "-1.00")))).andExpect(status().isBadRequest());
        generateAs("admin", "ADMIN", request("merchant-a", OCT_1, OCT_31,
                List.of(line("SD-1001-0001", "Too precise", "1.005")))).andExpect(status().isBadRequest());
        generateAs("admin", "ADMIN", request("merchant-a", OCT_1, OCT_31,
                List.of(line("not a tracking number!", "Bad tracking", "1.00")))).andExpect(status().isBadRequest());
        generateAs("admin", "ADMIN", new InvoiceRequest("merchant-a", " ", OCT_1, OCT_31, one))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/invoices?size=0").with(user("admin", "ADMIN"))).andExpect(status().isBadRequest());
        assertThat(count("invoice")).isZero();
    }

    @Test
    void largestSupportedInvoiceIsAcceptedAndALongInvoiceSpansPagesWithTheTotalOnTheLast() throws Exception {
        var maximum = IntStream.range(0, InvoiceRequest.MAX_LINES)
                .mapToObj(i -> line("SD-M" + i, "Maximum parcel " + i, "9999999999.99")).toList();
        var big = invoices.generate(request("merchant-big", OCT_1, OCT_31, maximum));
        assertThat(big.totalAmount()).isEqualByComparingTo("4999999999995.00");
        assertThat(big.lineCount()).isEqualTo(InvoiceRequest.MAX_LINES);

        var long120 = IntStream.range(0, 120).mapToObj(i -> line("SD-L" + i, "Parcel number " + i, "10.00")).toList();
        var longInvoice = invoices.generate(request("merchant-long", OCT_1, OCT_31, long120));
        PdfReader reader = new PdfReader(invoices.download(longInvoice.id(), "merchant-long", false).bytes());
        try {
            assertThat(reader.getNumberOfPages()).isGreaterThan(1);
            var text = new PdfTextExtractor(reader);
            assertThat(text.getTextFromPage(2)).contains("AMOUNT (LKR)", "Page 2");
            assertThat(text.getTextFromPage(reader.getNumberOfPages())).contains("TOTAL DUE (LKR)", "1200.00");
            assertThat(text.getTextFromPage(1)).doesNotContain("TOTAL DUE (LKR)");
        } finally { reader.close(); }
    }

    @Test
    void aZeroAmountInvoiceIsAllowed() {
        var free = invoices.generate(request("merchant-a", OCT_1, OCT_31,
                List.of(line("SD-0000-0001", "Fully discounted parcel", "0.00"))));
        assertThat(free.totalAmount()).isEqualByComparingTo("0.00");
    }
}
