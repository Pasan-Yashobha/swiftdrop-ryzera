package lk.swiftdrop.document.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.FilterChain;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.document.security.InternalTokenFilter;
import lk.swiftdrop.document.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** {@code GET /internal/receipts/{id}/pdf}: for the Notification service, protected by the shared internal token only. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = "document.messaging.enabled=false")
@Testcontainers
class InternalReceiptPdfTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** The dev-profile default of INTERNAL_API_TOKEN, shared with the other services' compose defaults. */
    static final String TOKEN = "dev-internal-token";

    @Autowired MockMvc mvc;
    @Autowired ReceiptService receipts;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE receipt, outbox_event").update();
    }

    private ReceiptMetadata receipt() {
        return receipts.generate(new ReceiptRequest(UUID.randomUUID(), "customer-a", "SD-2641-7788",
                Instant.parse("2026-11-11T10:00:00Z"), new BigDecimal("300.00"), new BigDecimal("200.00"),
                new BigDecimal("50.00"), new BigDecimal("137.50"), new BigDecimal("68.75"), new BigDecimal("618.75")));
    }

    @Test
    void anInternalServiceWithTheTokenGetsThePdfOfAnyReceipt() throws Exception {
        var receipt = receipt();
        byte[] pdf = mvc.perform(get("/internal/receipts/" + receipt.id() + "/pdf").header("X-Internal-Token", TOKEN))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("receipt-" + receipt.receiptNo())))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void aMissingOrWrongTokenIsA401EvenForAnAdminWithAValidJwt() throws Exception {
        var receipt = receipt();
        String url = "/internal/receipts/" + receipt.id() + "/pdf";
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("X-Internal-Token", "wrong-token")).andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("X-Internal-Token", "")).andExpect(status().isUnauthorized());
        // a user's real, validly signed JWT is not an internal credential
        mvc.perform(get(url).header("Authorization", TestTokens.bearer("admin-1", "ADMIN"))).andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("Authorization", TestTokens.bearer("customer-a", "CUSTOMER"))).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownReceiptIs404AndABadIdIs400() throws Exception {
        mvc.perform(get("/internal/receipts/" + UUID.randomUUID() + "/pdf").header("X-Internal-Token", TOKEN))
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/receipts/not-a-uuid/pdf").header("X-Internal-Token", TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theInternalTokenDoesNotOpenAnyOtherEndpoint() throws Exception {
        var receipt = receipt();
        mvc.perform(get("/api/v1/receipts/" + receipt.id() + "/download").header("X-Internal-Token", TOKEN))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/invoices").header("X-Internal-Token", TOKEN)).andExpect(status().isUnauthorized());
    }

    @Test
    void theFilterNeverAcceptsAnythingWhenNoSecretIsConfigured() throws Exception {
        for (String configured : new String[]{null, "", "   "}) {
            SecurityContextHolder.clearContext();
            var request = new MockHttpServletRequest("GET", "/internal/receipts/x/pdf");
            request.addHeader("X-Internal-Token", configured == null ? "" : configured);
            FilterChain chain = (req, res) -> { };
            new InternalTokenFilter(configured).doFilter(request, new MockHttpServletResponse(), chain);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
        SecurityContextHolder.clearContext();
        var request = new MockHttpServletRequest("GET", "/internal/receipts/x/pdf");
        request.addHeader("X-Internal-Token", "right");
        new InternalTokenFilter("right").doFilter(request, new MockHttpServletResponse(), (req, res) -> { });
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_INTERNAL");
        SecurityContextHolder.clearContext();
    }
}
