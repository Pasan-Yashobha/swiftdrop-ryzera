package lk.swiftdrop.courier.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lk.swiftdrop.courier.common.NotFoundException;
import lk.swiftdrop.courier.config.AppProperties;
import lk.swiftdrop.courier.domain.CourierProfile;
import lk.swiftdrop.courier.dto.CourierProfileResponse;
import lk.swiftdrop.courier.dto.UpdateProfileRequest;
import lk.swiftdrop.courier.repository.CourierProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourierProfileService {

    private static final Logger log = LoggerFactory.getLogger(CourierProfileService.class);

    private final CourierProfileRepository profileRepository;
    private final AppProperties properties;
    private final Clock clock;

    public CourierProfileService(CourierProfileRepository profileRepository,
                                 AppProperties properties,
                                 Clock clock) {
        this.profileRepository = profileRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CourierProfileResponse getProfile(UUID courierId) {
        Objects.requireNonNull(courierId, "courierId must not be null");
        CourierProfile profile = profileRepository.findByCourierId(courierId)
                .orElseThrow(() -> new NotFoundException("Courier profile not found for ID: " + courierId));
        return CourierProfileResponse.from(profile);
    }

    @Transactional
    public CourierProfileResponse updateProfile(UUID courierId, UpdateProfileRequest request) {
        Objects.requireNonNull(courierId, "courierId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        Instant now = clock.instant();
        int defaultMaxActive = properties.courier() != null ? properties.courier().defaultMaxActive() : 8;

        CourierProfile profile = profileRepository.findByCourierId(courierId)
                .map(existing -> {
                    existing.updateDetails(
                            request.fullName(),
                            request.phone(),
                            request.vehicleType(),
                            request.vehiclePlate(),
                            request.assignedZoneId(),
                            now
                    );
                    log.info("Updated existing courier profile for {}", courierId);
                    return existing;
                })
                .orElseGet(() -> {
                    log.info("Creating new courier profile for {}", courierId);
                    return CourierProfile.createNew(
                            courierId,
                            request.fullName(),
                            request.phone(),
                            request.vehicleType(),
                            request.vehiclePlate(),
                            defaultMaxActive,
                            request.assignedZoneId(),
                            now
                    );
                });

        CourierProfile saved = profileRepository.save(profile);
        return CourierProfileResponse.from(saved);
    }

    @Transactional
    public CourierProfileResponse updateAvailability(UUID courierId, boolean available) {
        Objects.requireNonNull(courierId, "courierId must not be null");

        CourierProfile profile = profileRepository.findByCourierId(courierId)
                .orElseThrow(() -> new NotFoundException("Courier profile not found for ID: " + courierId));

        profile.setAvailability(available, clock.instant());
        CourierProfile saved = profileRepository.save(profile);

        log.info("Updated availability for courier {} to {}", courierId, available);
        return CourierProfileResponse.from(saved);
    }
}
