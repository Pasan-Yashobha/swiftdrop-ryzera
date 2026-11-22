package lk.swiftdrop.courier.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.courier.common.*;
import lk.swiftdrop.courier.dto.LocationReportRequest;
import lk.swiftdrop.courier.event.LocationEventPublisher;
import lk.swiftdrop.courier.repository.CourierProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourierLocationService {
    private final CourierProfileRepository profiles;
    private final LocationEventPublisher publisher;
    private final Clock clock;
    public CourierLocationService(CourierProfileRepository profiles, LocationEventPublisher publisher, Clock clock) {
        this.profiles = profiles; this.publisher = publisher; this.clock = clock;
    }

    @Transactional
    public void report(UUID courierId, LocationReportRequest request) {
        var profile = profiles.findForLocationUpdate(courierId)
                .orElseThrow(() -> new NotFoundException("Courier profile not found"));
        Instant now = clock.instant();
        if (profile.getLastLocationReportAt() != null && now.isBefore(profile.getLastLocationReportAt().plusSeconds(10))) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "Wait at least 10 seconds between location reports");
        }
        publisher.publish(courierId, request, now);
        profile.recordLocationReport(now);
    }
}
