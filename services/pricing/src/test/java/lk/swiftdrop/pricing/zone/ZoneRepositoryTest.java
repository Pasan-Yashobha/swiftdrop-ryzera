package lk.swiftdrop.pricing.zone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(ZoneService.class)
class ZoneRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    ZoneRepository zones;

    @Autowired
    EntityManager entityManager;

    @Autowired
    ZoneService service;

    @Test
    void serviceUpdatesDetailsWithoutReactivatingZoneOrChangingCreationTime() {
        Instant created = Instant.parse("2026-11-12T00:00:00Z");
        Zone zone = zones.saveAndFlush(new Zone("KANDY", "Old name", "Old description", created));
        service.deactivate(zone.getId());
        service.update(zone.getId(), new ZoneRequest("KANDY_CENTRAL", " Kandy Central ", "New description"));
        entityManager.clear();

        ZoneResponse loaded = service.get(zone.getId());
        assertThat(loaded.code()).isEqualTo("KANDY_CENTRAL");
        assertThat(loaded.name()).isEqualTo("Kandy Central");
        assertThat(loaded.description()).isEqualTo("New description");
        assertThat(loaded.createdAt()).isEqualTo(created);
        assertThat(loaded.active()).isFalse();
        assertThat(loaded.updatedAt()).isAfter(created);
    }

    @Test
    void deactivationCanBeRetriedAndKeepsTheRecord() {
        ZoneResponse zone = service.create(new ZoneRequest("KANDY", "Kandy", null));
        service.deactivate(zone.id());
        entityManager.clear();
        Instant firstDeactivation = service.get(zone.id()).updatedAt();
        service.deactivate(zone.id());
        entityManager.clear();

        assertThat(service.get(zone.id()).active()).isFalse();
        assertThat(service.get(zone.id()).updatedAt()).isEqualTo(firstDeactivation);
        assertThat(zones.findByCodeAndActiveTrue("KANDY")).isEmpty();
    }

    @Test
    void updateOfMissingZoneReturnsNotFound() {
        assertThatThrownBy(() -> service.update(java.util.UUID.randomUUID(),
                new ZoneRequest("KANDY", "Kandy", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void savesAndReloadsZoneUsingMigratedSchema() {
        Instant now = Instant.parse("2026-11-12T00:00:00Z");
        Zone saved = zones.saveAndFlush(new Zone("COLOMBO", "Colombo", "Colombo district", now));
        entityManager.clear();

        Zone loaded = zones.findByCodeAndActiveTrue("COLOMBO").orElseThrow();
        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getName()).isEqualTo("Colombo");
        assertThat(loaded.getDescription()).isEqualTo("Colombo district");
        assertThat(loaded.getCreatedAt()).isEqualTo(now);
        assertThat(loaded.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void inactiveZoneIsRetainedButExcludedFromActiveLookup() {
        Instant now = Instant.parse("2026-11-12T00:00:00Z");
        Zone zone = zones.saveAndFlush(new Zone("KANDY", "Kandy", null, now));
        zone.deactivate(now.plusSeconds(60));
        zones.flush();
        entityManager.clear();

        assertThat(zones.findByCodeAndActiveTrue("KANDY")).isEmpty();
        Zone loaded = zones.findById(zone.getId()).orElseThrow();
        assertThat(loaded.isActive()).isFalse();
        assertThat(loaded.getUpdatedAt()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void databaseRejectsDuplicateZoneCodes() {
        Instant now = Instant.now();
        zones.saveAndFlush(new Zone("GALLE", "Galle", null, now));

        assertThatThrownBy(() -> zones.saveAndFlush(new Zone("GALLE", "Duplicate", null, now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
