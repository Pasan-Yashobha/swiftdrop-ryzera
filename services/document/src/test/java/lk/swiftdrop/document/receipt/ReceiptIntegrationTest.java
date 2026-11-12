package lk.swiftdrop.document.receipt;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers
class ReceiptIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired ReceiptService receipts;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clearDisposableDatabase() {
        jdbc.sql("TRUNCATE receipt").update();
    }

    private ReceiptRequest sample(UUID parcel) {
        return new ReceiptRequest(parcel, "customer-a", "SD-2641-7788", Instant.parse("2026-11-11T10:00:00Z"),
                new BigDecimal("300.00"), new BigDecimal("200.00"), new BigDecimal("50.00"),
                new BigDecimal("137.50"), new BigDecimal("68.75"), new BigDecimal("618.75"));
    }

    private RequestPostProcessor user(String id, String role) {
        return jwt().jwt(token -> token.subject(id)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void adminGeneratesPdfAndOwnerDownloadsConfirmedAmounts() throws Exception {
        var snapshot = sample(UUID.randomUUID());
        String body = mvc.perform(post("/api/v1/dev/receipts").with(user("admin", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(snapshot)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(body).get("id").asText();
        byte[] pdf = mvc.perform(get("/api/v1/receipts/" + id + "/download").with(user("customer-a", "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("SD-2641-7788", "618.75", "137.50", "-68.75", "DELIVERY RECEIPT");
        } finally { reader.close(); }
        Path preview = Path.of("target", "qa", "receipt-sample.pdf");
        Files.createDirectories(preview.getParent());
        Files.write(preview, pdf);
        mvc.perform(get("/api/v1/receipts/" + snapshot.parcelId()).with(user("customer-a", "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(618.75));
    }

    @Test
    void otherCustomersCannotReadDownloadOrListAnotherCustomersReceipt() throws Exception {
        var snapshot = sample(UUID.randomUUID());
        var receipt = receipts.generate(snapshot);
        mvc.perform(get("/api/v1/receipts/" + receipt.id() + "/download").with(user("customer-b", "CUSTOMER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/receipts/" + snapshot.parcelId()).with(user("customer-b", "CUSTOMER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/receipts").with(user("customer-b", "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/receipts/" + receipt.id() + "/download").with(user("admin", "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void simultaneousDuplicateSubmissionsProduceOneReceipt() {
        var snapshot = sample(UUID.randomUUID());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = CompletableFuture.supplyAsync(() -> receipts.generate(snapshot), pool);
            var second = CompletableFuture.supplyAsync(() -> receipts.generate(snapshot), pool);
            assertThat(first.join().id()).isEqualTo(second.join().id());
        }
        assertThat(jdbc.sql("SELECT count(*) FROM receipt").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void differentSnapshotForSameParcelIsRejectedAndOriginalPdfRetained() {
        UUID parcel = UUID.randomUUID();
        var receipt = receipts.generate(sample(parcel));
        var other = new ReceiptRequest(parcel, "customer-b", "SD-2641-7788", Instant.parse("2026-11-11T10:00:00Z"),
                new BigDecimal("300"), new BigDecimal("200"), new BigDecimal("50"),
                new BigDecimal("137.50"), new BigDecimal("68.75"), new BigDecimal("618.75"));
        assertThatThrownBy(() -> receipts.generate(other)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
        assertThat(receipts.getByParcel(parcel, "customer-a", false).id()).isEqualTo(receipt.id());
    }

    @Test
    void customerCannotGenerateReceiptAndAnonymousCannotDownload() throws Exception {
        mvc.perform(post("/api/v1/dev/receipts").with(user("customer-a", "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(sample(UUID.randomUUID()))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/receipts/" + UUID.randomUUID() + "/download"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inconsistentTotalIsRejectedBeforePersisting() throws Exception {
        var json = mapper.valueToTree(sample(UUID.randomUUID()));
        ((com.fasterxml.jackson.databind.node.ObjectNode) json).put("totalAmount", 1);
        mvc.perform(post("/api/v1/dev/receipts").with(user("admin", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(json)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.sql("SELECT count(*) FROM receipt").query(Long.class).single()).isZero();
    }

    @Test
    void decimalScaleDifferencesDoNotCreateConflictingSnapshots() {
        UUID parcel = UUID.randomUUID();
        var original = receipts.generate(sample(parcel));
        var same = new ReceiptRequest(parcel, "customer-a", "SD-2641-7788", Instant.parse("2026-11-11T10:00:00Z"),
                new BigDecimal("300"), new BigDecimal("200"), new BigDecimal("50"),
                new BigDecimal("137.5"), new BigDecimal("68.75"), new BigDecimal("618.75"));
        assertThat(receipts.generate(same).id()).isEqualTo(original.id());
    }

    @Test
    void maximumSupportedAmountFitsOnePageWithoutLosingDigits() throws Exception {
        BigDecimal amount = new BigDecimal("99999999999999.99");
        var snapshot = new ReceiptRequest(UUID.randomUUID(), "external|customer-42", "SD-9999-9999",
                Instant.parse("2026-11-11T10:00:00Z"), amount, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, amount);
        var receipt = receipts.generate(snapshot);
        PdfReader reader = new PdfReader(receipts.download(receipt.id(), "external|customer-42", false).bytes());
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains("99999999999999.99");
        } finally { reader.close(); }
    }
}
