package lk.swiftdrop.courier.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.courier.common.NotFoundException;
import lk.swiftdrop.courier.config.AppProperties;
import lk.swiftdrop.courier.domain.CourierProfile;
import lk.swiftdrop.courier.domain.VehicleType;
import lk.swiftdrop.courier.dto.CourierProfileResponse;
import lk.swiftdrop.courier.dto.UpdateProfileRequest;
import lk.swiftdrop.courier.repository.CourierProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierProfileServiceTest {

    @Mock
    private CourierProfileRepository profileRepository;

    private AppProperties properties;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC);
    private CourierProfileService profileService;

    private final UUID courierId = UUID.fromString("c0000001-0000-0000-0000-000000000001");
    private final UUID zoneId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        properties = new AppProperties(
                new AppProperties.Courier(8, 4),
                new AppProperties.Integration("http://parcel:8080", "http://identity:8080", "test-token", true),
                new AppProperties.Security(null, "test-secret", null),
                new AppProperties.Storage("local", "target/test-storage", null, null, null),
                new AppProperties.Messaging("swiftdrop.events", "swiftdrop.dlx", "courier.assignments.q", "courier.assignments.dlq", "parcel.assigned", "parcel.reassigned")
        );
        profileService = new CourierProfileService(profileRepository, properties, clock);
    }

    @Test
    @DisplayName("getProfile returns profile when exists")
    void shouldReturnProfileWhenExists() {
        CourierProfile existing = new CourierProfile(
                courierId, "Saman Perera", "+94771234567", VehicleType.BIKE,
                "WP BCD-1234", true, 2, 8, zoneId, clock.instant()
        );
        when(profileRepository.findByCourierId(courierId)).thenReturn(Optional.of(existing));

        CourierProfileResponse response = profileService.getProfile(courierId);

        assertThat(response.courierId()).isEqualTo(courierId);
        assertThat(response.fullName()).isEqualTo("Saman Perera");
        assertThat(response.phone()).isEqualTo("+94771234567");
        assertThat(response.vehicleType()).isEqualTo(VehicleType.BIKE);
        assertThat(response.isAvailable()).isTrue();
        assertThat(response.activeDeliveries()).isEqualTo(2);
        assertThat(response.maxActiveDeliveries()).isEqualTo(8);
        assertThat(response.assignedZoneId()).isEqualTo(zoneId);
    }

    @Test
    @DisplayName("getProfile throws NotFoundException when not found")
    void shouldThrowNotFoundWhenProfileDoesNotExist() {
        when(profileRepository.findByCourierId(courierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getProfile(courierId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Courier profile not found for ID: " + courierId);
    }

    @Test
    @DisplayName("updateProfile creates new profile if none exists")
    void shouldCreateNewProfileWhenNoneExists() {
        UpdateProfileRequest request = new UpdateProfileRequest(
                "Nimal Silva", "+94719876543", VehicleType.VAN, "WP CA-5678", zoneId
        );
        when(profileRepository.findByCourierId(courierId)).thenReturn(Optional.empty());
        when(profileRepository.save(any(CourierProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourierProfileResponse response = profileService.updateProfile(courierId, request);

        assertThat(response.courierId()).isEqualTo(courierId);
        assertThat(response.fullName()).isEqualTo("Nimal Silva");
        assertThat(response.phone()).isEqualTo("+94719876543");
        assertThat(response.vehicleType()).isEqualTo(VehicleType.VAN);
        assertThat(response.vehiclePlate()).isEqualTo("WP CA-5678");
        assertThat(response.isAvailable()).isTrue();
        assertThat(response.activeDeliveries()).isEqualTo(0);
        assertThat(response.maxActiveDeliveries()).isEqualTo(8);
    }

    @Test
    @DisplayName("updateProfile updates editable fields and preserves system-managed active/max deliveries")
    void shouldUpdateEditableFieldsPreservingSystemManagedFields() {
        CourierProfile existing = new CourierProfile(
                courierId, "Old Name", "+94770000000", VehicleType.BIKE,
                "OLD-111", true, 3, 10, null, clock.instant().minusSeconds(3600)
        );
        UpdateProfileRequest request = new UpdateProfileRequest(
                "Updated Name", "+94779999999", VehicleType.THREE_WHEELER, "WP GH-9999", zoneId
        );
        when(profileRepository.findByCourierId(courierId)).thenReturn(Optional.of(existing));
        when(profileRepository.save(any(CourierProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourierProfileResponse response = profileService.updateProfile(courierId, request);

        assertThat(response.fullName()).isEqualTo("Updated Name");
        assertThat(response.phone()).isEqualTo("+94779999999");
        assertThat(response.vehicleType()).isEqualTo(VehicleType.THREE_WHEELER);
        assertThat(response.vehiclePlate()).isEqualTo("WP GH-9999");
        assertThat(response.assignedZoneId()).isEqualTo(zoneId);

        // System-managed fields MUST be preserved
        assertThat(response.activeDeliveries()).isEqualTo(3);
        assertThat(response.maxActiveDeliveries()).isEqualTo(10);
    }

    @Test
    @DisplayName("updateAvailability toggles availability flag")
    void shouldUpdateAvailability() {
        CourierProfile existing = new CourierProfile(
                courierId, "Kasun Silva", "+94771112233", VehicleType.BIKE,
                "WP AB-1234", true, 0, 8, zoneId, clock.instant()
        );
        when(profileRepository.findByCourierId(courierId)).thenReturn(Optional.of(existing));
        when(profileRepository.save(any(CourierProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourierProfileResponse response = profileService.updateAvailability(courierId, false);

        assertThat(response.isAvailable()).isFalse();

        ArgumentCaptor<CourierProfile> captor = ArgumentCaptor.forClass(CourierProfile.class);
        verify(profileRepository).save(captor.capture());
        assertThat(captor.getValue().isAvailable()).isFalse();
    }

    @Test
    @DisplayName("updateAvailability throws NotFoundException when profile not found")
    void shouldThrowNotFoundOnAvailabilityUpdateWhenNotExists() {
        when(profileRepository.findByCourierId(courierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.updateAvailability(courierId, true))
                .isInstanceOf(NotFoundException.class);
    }
}
