package lk.swiftdrop.pricing.estimate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lk.swiftdrop.pricing.ratecard.RateCard;
import lk.swiftdrop.pricing.ratecard.RateCardRepository;
import lk.swiftdrop.pricing.ratecard.RateCardRequest;
import lk.swiftdrop.pricing.zone.Zone;
import lk.swiftdrop.pricing.zone.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers
@Transactional(isolation = Isolation.REPEATABLE_READ)
@Import(FeeEstimationTest.TimeConfig.class)
class FeeEstimationTest {
    // This UTC instant is already 12 November in Sri Lanka.
    static final Instant NOW = Instant.parse("2026-11-11T20:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary Clock testClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    @Autowired FeeEstimationService estimates;
    @Autowired ZoneRepository zones;
    @Autowired RateCardRepository cards;
    @Autowired JdbcClient jdbc;
    @Autowired lk.swiftdrop.pricing.promo.PromoCodeService promos;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    Zone origin;
    Zone destination;

    private lk.swiftdrop.pricing.promo.PromoCodeRequest promo(String code,
            lk.swiftdrop.pricing.promo.PromoCodeRequest.DiscountType type, String value,
            String minimum, Integer maxUses, LocalDate from, LocalDate to) {
        return new lk.swiftdrop.pricing.promo.PromoCodeRequest(code, type, new BigDecimal(value),
                new BigDecimal(minimum), maxUses, from, to);
    }

    private EstimateRequest discounted(String code) {
        return new EstimateRequest("COLOMBO", "KANDY", new BigDecimal("2"),
                EstimateRequest.SizeClass.MEDIUM, EstimateRequest.DeliveryType.STANDARD, code);
    }

    @Test
    void percentageDiscountIsPersistedAndPreviewsDoNotConsumeUses() {
        card(TODAY, null);
        var promo = promos.create(promo("save10", lk.swiftdrop.pricing.promo.PromoCodeRequest.DiscountType.PERCENT,
                "10", "500", 1, TODAY, null));
        var first = estimates.estimate(discounted("save10"), "customer");
        estimates.estimate(discounted("SAVE10"), "customer");
        assertThat(first.amounts().discountAmount()).isEqualByComparingTo("55.00");
        assertThat(first.amounts().totalAmount()).isEqualByComparingTo("495.00");
        assertThat(first.promoCode()).isEqualTo("SAVE10");
        assertThat(jdbc.sql("SELECT promo_code_id FROM fee_quote WHERE id = :id")
                .param("id", first.quoteId()).query(java.util.UUID.class).single()).isEqualTo(promo.id());
        assertThat(promos.get(promo.id()).usedCount()).isZero();
    }

    @Test
    void fixedDiscountCannotMakeFareNegative() {
        card(TODAY, null);
        promos.create(promo("FREE", lk.swiftdrop.pricing.promo.PromoCodeRequest.DiscountType.FLAT,
                "1000", "0", null, TODAY, null));
        var quote = estimates.estimate(discounted("FREE"), "customer");
        assertThat(quote.amounts().discountAmount()).isEqualByComparingTo("550.00");
        assertThat(quote.amounts().totalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void percentageDiscountIncludesExpressSurchargeInSubtotal() {
        cards.saveAndFlush(new RateCard(new RateCardRequest(origin.getId(), destination.getId(),
                new BigDecimal("300"), new BigDecimal("100"), BigDecimal.ZERO,
                new BigDecimal("50"), BigDecimal.ZERO, TODAY, null, new BigDecimal("1.25")), NOW));
        promos.create(promo("EXPRESS10", lk.swiftdrop.pricing.promo.PromoCodeRequest.DiscountType.PERCENT,
                "10", "0", null, TODAY, null));
        var request = new EstimateRequest("COLOMBO", "KANDY", new BigDecimal("2"),
                EstimateRequest.SizeClass.MEDIUM, EstimateRequest.DeliveryType.EXPRESS, "EXPRESS10");
        var quote = estimates.estimate(request, "customer");
        assertThat(quote.amounts().expressAmount()).isEqualByComparingTo("137.50");
        assertThat(quote.amounts().discountAmount()).isEqualByComparingTo("68.75");
        assertThat(quote.amounts().totalAmount()).isEqualByComparingTo("618.75");
    }

    @Test
    void expiredAndNotYetValidPromosAreRejected() {
        card(TODAY, null);
        promos.create(promo("EXPIRED", lk.swiftdrop.pricing.promo.PromoCodeRequest.DiscountType.FLAT,
                "10", "0", null, TODAY.minusDays(1), TODAY));
        promos.create(promo("FUTURE", lk.swiftdrop.pricing.promo.PromoCodeRequest.DiscountType.FLAT,
                "10", "0", null, TODAY.plusDays(1), null));
        for (String code : new String[]{"EXPIRED", "FUTURE", "UNKNOWN"}) {
            assertThatThrownBy(() -> estimates.estimate(discounted(code), "customer"))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            error -> assertThat(error.getStatusCode().value()).isEqualTo(422));
        }
    }

    @Test
    void exhaustedAndBelowMinimumPromosAreRejected() {
        card(TODAY, null);
        var limited = promos.create(promo("LIMITED", lk.swiftdrop.pricing.promo.PromoCodeRequest.DiscountType.FLAT,
                "10", "0", 1, TODAY, null));
        jdbc.sql("UPDATE promo_code SET used_count = 1 WHERE id = :id").param("id", limited.id()).update();
        entityManager.clear();
        promos.create(promo("MINIMUM", lk.swiftdrop.pricing.promo.PromoCodeRequest.DiscountType.FLAT,
                "10", "600", null, TODAY, null));
        for (String code : new String[]{"LIMITED", "MINIMUM"}) {
            assertThatThrownBy(() -> estimates.estimate(discounted(code), "customer"))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            error -> assertThat(error.getStatusCode().value()).isEqualTo(422));
        }
    }

    @Test
    void adminCanUpdateAndDeactivatePromoWithoutDeletingIt() {
        card(TODAY, null);
        var promo = promos.create(promo("SAVE", lk.swiftdrop.pricing.promo.PromoCodeRequest.DiscountType.FLAT,
                "10", "0", null, TODAY, null));
        promos.update(promo.id(), promo("SAVE", lk.swiftdrop.pricing.promo.PromoCodeRequest.DiscountType.FLAT,
                "20", "0", null, TODAY, null));
        assertThat(estimates.estimate(discounted("SAVE"), "customer").amounts().discountAmount())
                .isEqualByComparingTo("20.00");
        promos.deactivate(promo.id());
        promos.deactivate(promo.id());
        assertThat(promos.get(promo.id()).active()).isFalse();
        assertThatThrownBy(() -> estimates.estimate(discounted("SAVE"), "customer"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(422));
    }

    @Test
    void developmentTokenAuthenticatesDirectoryAndRespectsAdminBoundary() throws Exception {
        origin.deactivate(NOW);
        zones.flush();
        String body = mvc.perform(post("/api/v1/dev/tokens")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"sub\":\"local-customer\",\"role\":\"CUSTOMER\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(body).get("accessToken").asText();
        mvc.perform(get("/api/v1/pricing/zones").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].code").value("KANDY"));
        mvc.perform(get("/api/v1/admin/zones").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @BeforeEach
    void prepareRoute() {
        var timestamps = new org.springframework.security.oauth2.jwt.JwtTimestampValidator();
        timestamps.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        ((org.springframework.security.oauth2.jwt.NimbusJwtDecoder) decoder).setJwtValidator(timestamps);
        origin = zones.saveAndFlush(new Zone("COLOMBO", "Colombo", null, NOW));
        destination = zones.saveAndFlush(new Zone("KANDY", "Kandy", null, NOW));
    }

    private RateCard card(LocalDate from, LocalDate to) {
        return cards.saveAndFlush(new RateCard(new RateCardRequest(origin.getId(), destination.getId(),
                new BigDecimal("300.00"), new BigDecimal("100.00"), BigDecimal.ZERO,
                new BigDecimal("50.00"), new BigDecimal("100.00"), from, to), NOW));
    }

    private EstimateRequest request(String weight, EstimateRequest.SizeClass size) {
        return new EstimateRequest("COLOMBO", "KANDY", new BigDecimal(weight), size,
                EstimateRequest.DeliveryType.STANDARD);
    }

    @Test
    void calculatesAndPersistsQuoteWithAuthenticatedOwnerAndExpiry() {
        RateCard rate = card(TODAY, null);
        EstimateResponse quote = estimates.estimate(request("2", EstimateRequest.SizeClass.MEDIUM), "customer-42");
        assertThat(quote.amounts().baseAmount()).isEqualByComparingTo("300.00");
        assertThat(quote.amounts().weightAmount()).isEqualByComparingTo("200.00");
        assertThat(quote.amounts().sizeAmount()).isEqualByComparingTo("50.00");
        assertThat(quote.amounts().totalAmount()).isEqualByComparingTo("550.00");
        assertThat(quote.rateCardId()).isEqualTo(rate.getId());
        assertThat(quote.currency()).isEqualTo("LKR");
        assertThat(quote.expiresAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(jdbc.sql("SELECT requested_by FROM fee_quote WHERE id = :id")
                .param("id", quote.quoteId()).query(String.class).single()).isEqualTo("customer-42");
        assertThat(jdbc.sql("SELECT total_amount FROM fee_quote WHERE id = :id")
                .param("id", quote.quoteId()).query(BigDecimal.class).single()).isEqualByComparingTo("550.00");
    }

    @Test
    void roundsFractionalWeightAndSupportsSmallAndLargeSizes() {
        card(TODAY, null);
        assertThat(estimates.estimate(request("0.125", EstimateRequest.SizeClass.SMALL), "customer")
                .amounts().totalAmount()).isEqualByComparingTo("312.50");
        assertThat(estimates.estimate(request("20", EstimateRequest.SizeClass.LARGE), "customer")
                .amounts().totalAmount()).isEqualByComparingTo("2400.00");
    }

    @Test
    void expressAddsConfiguredMultiplierAndPersistsBreakdown() {
        RateCardRequest configured = new RateCardRequest(origin.getId(), destination.getId(),
                new BigDecimal("300"), new BigDecimal("100"), BigDecimal.ZERO,
                new BigDecimal("50"), new BigDecimal("100"), TODAY, null, new BigDecimal("1.50"));
        cards.saveAndFlush(new RateCard(configured, NOW));
        EstimateRequest request = new EstimateRequest("COLOMBO", "KANDY", new BigDecimal("2"),
                EstimateRequest.SizeClass.MEDIUM, EstimateRequest.DeliveryType.EXPRESS);
        EstimateResponse quote = estimates.estimate(request, "customer");
        assertThat(quote.amounts().expressAmount()).isEqualByComparingTo("275.00");
        assertThat(quote.amounts().totalAmount()).isEqualByComparingTo("825.00");
        assertThat(quote.strategyUsed()).isEqualTo(EstimateRequest.DeliveryType.EXPRESS);
        assertThat(jdbc.sql("SELECT express_amount FROM fee_quote WHERE id = :id")
                .param("id", quote.quoteId()).query(BigDecimal.class).single()).isEqualByComparingTo("275.00");
        EstimateResponse standard = estimates.estimate(request("2", EstimateRequest.SizeClass.MEDIUM), "customer");
        assertThat(standard.amounts().expressAmount()).isEqualByComparingTo("0.00");
        assertThat(standard.amounts().totalAmount()).isEqualByComparingTo("550.00");
    }

    @Test
    void oldRateCardsSupportStandardAndRequireExplicitExpressConfiguration() {
        card(TODAY, null);
        EstimateRequest express = new EstimateRequest("COLOMBO", "KANDY", new BigDecimal("2"),
                EstimateRequest.SizeClass.MEDIUM, EstimateRequest.DeliveryType.EXPRESS);
        assertThatThrownBy(() -> estimates.estimate(express, "customer"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(422));
    }

    @Test
    void expressSurchargeRoundsHalfUpToTwoDecimals() {
        cards.saveAndFlush(new RateCard(new RateCardRequest(origin.getId(), destination.getId(),
                new BigDecimal("300"), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, TODAY, null, new BigDecimal("1.15")), NOW));
        var request = new EstimateRequest("COLOMBO", "KANDY", new BigDecimal("0.125"),
                EstimateRequest.SizeClass.SMALL, EstimateRequest.DeliveryType.EXPRESS);
        var result = estimates.estimate(request, "customer");
        assertThat(result.amounts().expressAmount()).isEqualByComparingTo("46.88");
        assertThat(result.amounts().totalAmount()).isEqualByComparingTo("359.38");
    }

    @Test
    void expiredCardIsExcludedOnItsEndDate() {
        card(TODAY.minusDays(1), TODAY);
        assertThatThrownBy(() -> estimates.estimate(request("2", EstimateRequest.SizeClass.SMALL), "customer"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(422));
    }

    @Test
    void futureAndInactiveCardsAreExcluded() {
        RateCard current = card(TODAY, TODAY.plusDays(1));
        current.deactivate(NOW);
        cards.flush();
        card(TODAY.plusDays(1), null);
        assertThatThrownBy(() -> estimates.estimate(request("2", EstimateRequest.SizeClass.SMALL), "customer"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(422));
    }

    @Test
    void inactiveZoneIsRejectedAndDoesNotCreateQuote() {
        card(TODAY, null);
        origin.deactivate(NOW);
        zones.flush();
        assertThatThrownBy(() -> estimates.estimate(request("2", EstimateRequest.SizeClass.SMALL), "customer"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(400));
        assertThat(jdbc.sql("SELECT count(*) FROM fee_quote").query(Long.class).single()).isZero();
    }
}
