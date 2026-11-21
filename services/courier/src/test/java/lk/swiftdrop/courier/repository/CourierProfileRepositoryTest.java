package lk.swiftdrop.courier.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.courier.domain.CourierProfile;
import lk.swiftdrop.courier.domain.VehicleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CourierProfileRepositoryTest {

    @Autowired
    private CourierProfileRepository repository;

    @Test
    @DisplayName("should save and find profile by courierId")
    void shouldSaveAndFindProfile() {
        UUID courierId = UUID.randomUUID();
        CourierProfile profile = CourierProfile.createNew(
                courierId,
                "Kamal Silva",
                "+94771234567",
                VehicleType.BIKE,
                "WP BCD-1234",
                8,
                null,
                Instant.now()
        );

        repository.save(profile);

        Optional<CourierProfile> found = repository.findByCourierId(courierId);
        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Kamal Silva");
        assertThat(found.get().getPhone()).isEqualTo("+94771234567");
        assertThat(found.get().getVehicleType()).isEqualTo(VehicleType.BIKE);
        assertThat(found.get().isAvailable()).isTrue();
        assertThat(found.get().getActiveDeliveries()).isEqualTo(0);
        assertThat(found.get().getMaxActiveDeliveries()).isEqualTo(8);

        assertThat(repository.existsByCourierId(courierId)).isTrue();
        assertThat(repository.existsByCourierId(UUID.randomUUID())).isFalse();
    }
    @Autowired private jakarta.persistence.EntityManager entityManager;

    @Test void locationThrottleTimestampSurvivesReloadAndLockedLookup() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-18T00:00:00Z");
        repository.saveAndFlush(CourierProfile.createNew(id, "Test", "", VehicleType.BIKE, "", 8, null, now));
        entityManager.clear();
        CourierProfile locked = repository.findForLocationUpdate(id).orElseThrow();
        assertThat(locked.getLastLocationReportAt()).isNull();
        locked.recordLocationReport(now);
        entityManager.flush(); entityManager.clear();
        assertThat(repository.findForLocationUpdate(id).orElseThrow().getLastLocationReportAt()).isEqualTo(now);
    }

}
