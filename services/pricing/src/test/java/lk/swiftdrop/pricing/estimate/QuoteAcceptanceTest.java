package lk.swiftdrop.pricing.estimate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import lk.swiftdrop.pricing.promo.PromoCodeRequest;
import lk.swiftdrop.pricing.promo.PromoCodeService;
import lk.swiftdrop.pricing.ratecard.RateCard;
import lk.swiftdrop.pricing.ratecard.RateCardRepository;
import lk.swiftdrop.pricing.ratecard.RateCardRequest;
import lk.swiftdrop.pricing.zone.Zone;
import lk.swiftdrop.pricing.zone.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The internal accept endpoint the Parcel service calls when a booking is confirmed. Not wrapped in a test
 * transaction: row locking and concurrent accepts only behave realistically with real commits.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers
@Import(QuoteAcceptanceTest.TimeConfig.class)
class QuoteAcceptanceTest {
    static final Instant NOW = Instant.parse("2026-11-11T20:00:00Z"); // already 12 November in Sri Lanka
    static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    static final String TOKEN = "dev-internal-token";

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

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired FeeEstimationService estimates;
    @Autowired QuoteAcceptanceService acceptance;
    @Autowired PromoCodeService promos;
    @Autowired ZoneRepository zones;
    @Autowired RateCardRepository cards;

    @BeforeEach
    void route() {
        jdbc.sql("DELETE FROM fee_quote").update();
        jdbc.sql("DELETE FROM promo_code").update();
        jdbc.sql("DELETE FROM rate_card").update();
        jdbc.sql("DELETE FROM zone").update();
        Zone colombo = zones.saveAndFlush(new Zone("COLOMBO", "Colombo", null, NOW));
        Zone kandy = zones.saveAndFlush(new Zone("KANDY", "Kandy", null, NOW));
        cards.saveAndFlush(new RateCard(new RateCardRequest(colombo.getId(), kandy.getId(),
                new BigDecimal("300.00"), new BigDecimal("100.00"), BigDecimal.ZERO,
                new BigDecimal("50.00"), new BigDecimal("100.00"), TODAY, null), NOW));
    }

    /** 2 kg medium Colombo to Kandy: 300 + 200 + 50 = 550, less the promo if one is given. */
    private EstimateResponse quote(String promo) {
        return estimates.estimate(new EstimateRequest("COLOMBO", "KANDY", new BigDecimal("2"),
                EstimateRequest.SizeClass.MEDIUM, EstimateRequest.DeliveryType.STANDARD, promo), "customer-1");
    }

    private UUID promo(String code, Integer maxUses) {
        return promos.create(new PromoCodeRequest(code, PromoCodeRequest.DiscountType.PERCENT,
                new BigDecimal("10"), BigDecimal.ZERO, maxUses, TODAY, null)).id();
    }

    private int usedCount(UUID promoId) {
        return jdbc.sql("SELECT used_count FROM promo_code WHERE id = :id").param("id", promoId)
                .query(Integer.class).single();
    }

    private boolean isAccepted(UUID quoteId) {
        return jdbc.sql("SELECT COUNT(*) FROM fee_quote WHERE id = :id AND accepted_at IS NOT NULL")
                .param("id", quoteId).query(Integer.class).single() == 1;
    }

    private ResultActions accept(UUID quoteId, UUID parcelId, String token) throws Exception {
        var request = post("/internal/quotes/{id}/accept", quoteId).contentType(MediaType.APPLICATION_JSON)
                .content(parcelId == null ? "{}" : "{\"parcelId\":\"" + parcelId + "\"}");
        if (token != null) request.header("X-Internal-Token", token);
        return mvc.perform(request);
    }

    @Test
    void acceptingReturnsTheBreakdownSnapshotAndRecordsTheParcel() throws Exception {
        UUID promoId = promo("SAVE10", null);
        EstimateResponse quote = quote("SAVE10");
        UUID parcel = UUID.randomUUID();

        accept(quote.quoteId(), parcel, TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteId").value(quote.quoteId().toString()))
                .andExpect(jsonPath("$.parcelId").value(parcel.toString()))
                .andExpect(jsonPath("$.originZone").value("COLOMBO"))
                .andExpect(jsonPath("$.destinationZone").value("KANDY"))
                .andExpect(jsonPath("$.promoCode").value("SAVE10"))
                .andExpect(jsonPath("$.currency").value("LKR"))
                .andExpect(jsonPath("$.pricing.baseAmount").value(300.00))
                .andExpect(jsonPath("$.pricing.weightAmount").value(200.00))
                .andExpect(jsonPath("$.pricing.sizeAmount").value(50.00))
                .andExpect(jsonPath("$.pricing.expressAmount").value(0))
                .andExpect(jsonPath("$.pricing.discountAmount").value(55.00))
                .andExpect(jsonPath("$.pricing.totalAmount").value(495.00));

        assertThat(usedCount(promoId)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT accepted_parcel_id FROM fee_quote WHERE id = :id")
                .param("id", quote.quoteId()).query(UUID.class).single()).isEqualTo(parcel);
    }

    @Test
    void aRetryReturnsTheSameSnapshotAndDoesNotUseThePromoAgain() throws Exception {
        UUID promoId = promo("SAVE10", 5);
        EstimateResponse quote = quote("SAVE10");
        UUID parcel = UUID.randomUUID();

        accept(quote.quoteId(), parcel, TOKEN).andExpect(status().isOk());
        accept(quote.quoteId(), parcel, TOKEN).andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelId").value(parcel.toString()))
                .andExpect(jsonPath("$.pricing.totalAmount").value(495.00));
        accept(quote.quoteId(), null, TOKEN).andExpect(status().isOk());

        assertThat(usedCount(promoId)).isEqualTo(1);
    }

    @Test
    void aQuoteAlreadyAcceptedForAnotherParcelIsAConflict() throws Exception {
        EstimateResponse quote = quote(null);
        accept(quote.quoteId(), UUID.randomUUID(), TOKEN).andExpect(status().isOk());

        accept(quote.quoteId(), UUID.randomUUID(), TOKEN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This quote was already accepted for another parcel"));
    }

    @Test
    void anExpiredQuoteIsGoneAndUsesNoPromo() throws Exception {
        UUID promoId = promo("SAVE10", null);
        EstimateResponse quote = quote("SAVE10");
        jdbc.sql("UPDATE fee_quote SET created_at = :created, expires_at = :expired WHERE id = :id")
                .param("created", Timestamp.from(NOW.minusSeconds(20 * 60)))
                .param("expired", Timestamp.from(NOW.minusSeconds(60)))
                .param("id", quote.quoteId())
                .update();

        accept(quote.quoteId(), UUID.randomUUID(), TOKEN)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.detail").value("This quote has expired; request a new estimate"));
        assertThat(usedCount(promoId)).isZero();
    }

    @Test
    void theLastUseOfAPromoCannotBeTakenTwice() throws Exception {
        UUID promoId = promo("ONCE", 1);
        EstimateResponse first = quote("ONCE");
        EstimateResponse second = quote("ONCE");

        accept(first.quoteId(), UUID.randomUUID(), TOKEN).andExpect(status().isOk());
        accept(second.quoteId(), UUID.randomUUID(), TOKEN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The promo code can no longer be used; request a new estimate"));

        assertThat(usedCount(promoId)).isEqualTo(1);
        assertThat(isAccepted(second.quoteId())).isFalse();
    }

    @Test
    void twoSimultaneousAcceptsOfTheSameQuoteUseThePromoOnce() throws Exception {
        UUID promoId = promo("SAVE10", 5);
        EstimateResponse quote = quote("SAVE10");
        UUID parcel = UUID.randomUUID();
        CyclicBarrier start = new CyclicBarrier(2);

        var one = CompletableFuture.supplyAsync(() -> acceptTogether(start, quote.quoteId(), parcel));
        var two = CompletableFuture.supplyAsync(() -> acceptTogether(start, quote.quoteId(), parcel));

        assertThat(one.get(30, TimeUnit.SECONDS)).isEqualTo(two.get(30, TimeUnit.SECONDS));
        assertThat(usedCount(promoId)).isEqualTo(1);
    }

    private AcceptedQuote acceptTogether(CyclicBarrier start, UUID quoteId, UUID parcel) {
        try {
            start.await(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        return acceptance.accept(quoteId, parcel);
    }

    @Test
    void anUnknownQuoteIsNotFound() throws Exception {
        accept(UUID.randomUUID(), null, TOKEN).andExpect(status().isNotFound());
    }

    @Test
    void withoutTheRightInternalTokenNothingIsAccepted() throws Exception {
        EstimateResponse quote = quote(null);

        accept(quote.quoteId(), null, null).andExpect(status().isUnauthorized());
        accept(quote.quoteId(), null, "wrong-token").andExpect(status().isUnauthorized());
        assertThat(isAccepted(quote.quoteId())).isFalse();
    }

    @Test
    void aUserJwtCannotOpenTheInternalEndpoint() throws Exception {
        EstimateResponse quote = quote(null);
        mvc.perform(post("/internal/quotes/{id}/accept", quote.quoteId())
                        .header("Authorization", "Bearer some.user.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theServiceRefusesAnUnknownQuoteWithA404Reason() {
        assertThatThrownBy(() -> acceptance.accept(UUID.randomUUID(), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
                        .isEqualTo(404));
    }
}
