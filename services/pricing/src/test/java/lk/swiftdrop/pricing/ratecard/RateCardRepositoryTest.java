package lk.swiftdrop.pricing.ratecard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lk.swiftdrop.pricing.zone.Zone;
import lk.swiftdrop.pricing.zone.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RateCardService.class)
@Testcontainers
class RateCardRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired RateCardService service;
    @Autowired ZoneRepository zones;
    @Autowired EntityManager entityManager;
    UUID origin;
    UUID destination;

    @BeforeEach
    void createZones() {
        origin = zones.saveAndFlush(new Zone("COLOMBO", "Colombo", null, Instant.now())).getId();
        destination = zones.saveAndFlush(new Zone("KANDY", "Kandy", null, Instant.now())).getId();
    }

    private RateCardRequest request(LocalDate from, LocalDate to) {
        return new RateCardRequest(origin, destination, new BigDecimal("300.00"), new BigDecimal("100.00"),
                BigDecimal.ZERO, new BigDecimal("50.00"), new BigDecimal("100.00"), from, to);
    }

    @Test
    void savesAndReloadsExactMoneyAndZoneReferences() {
        RateCardResponse card = service.create(request(LocalDate.of(2026, 1, 1), null));
        entityManager.clear();
        RateCardResponse loaded = service.get(card.id());
        assertThat(loaded.baseFee()).isEqualByComparingTo("300.00");
        assertThat(loaded.perKgFee()).isEqualByComparingTo("100.00");
        assertThat(loaded.mediumSurcharge()).isEqualByComparingTo("50.00");
        assertThat(loaded.originZoneId()).isEqualTo(origin);
        assertThat(loaded.destinationZoneId()).isEqualTo(destination);
        assertThat(loaded.validTo()).isNull();
        assertThat(loaded.active()).isTrue();
    }

    @Test
    void rejectsOverlappingActiveRatesIncludingOpenEndedCards() {
        service.create(request(LocalDate.of(2026, 1, 1), null));
        assertThatThrownBy(() -> service.create(request(LocalDate.of(2026, 2, 1), null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void allowsAdjacentDateRangesAndSameZoneRoutes() {
        destination = origin;
        service.create(request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)));
        service.create(request(LocalDate.of(2026, 2, 1), null));
        assertThat(service.list(0, 20).totalElements()).isEqualTo(2);
    }

    @Test
    void updatesRatesAndCanDeactivateRepeatedly() {
        RateCardResponse card = service.create(request(LocalDate.of(2026, 1, 1), null));
        service.update(card.id(), request(LocalDate.of(2026, 2, 1), null));
        service.deactivate(card.id());
        service.deactivate(card.id());
        entityManager.clear();
        assertThat(service.get(card.id()).validFrom()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(service.get(card.id()).active()).isFalse();
        service.create(request(LocalDate.of(2026, 1, 1), null));
    }

    @Test
    void inactiveZoneIsRejected() {
        zones.findById(origin).orElseThrow().deactivate(Instant.now());
        assertThatThrownBy(() -> service.create(request(LocalDate.of(2026, 1, 1), null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void requestRejectsNegativeMoneyAndInvalidDateRanges() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        RateCardRequest invalid = new RateCardRequest(origin, destination, new BigDecimal("-1"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, date, date);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(invalid)).extracting(v -> v.getPropertyPath().toString())
                    .contains("baseFee", "dateRangeValid");
        }
    }
}
