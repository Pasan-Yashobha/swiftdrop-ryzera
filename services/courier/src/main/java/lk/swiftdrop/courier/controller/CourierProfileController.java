package lk.swiftdrop.courier.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lk.swiftdrop.courier.dto.CourierProfileResponse;
import lk.swiftdrop.courier.dto.UpdateAvailabilityRequest;
import lk.swiftdrop.courier.dto.UpdateProfileRequest;
import lk.swiftdrop.courier.security.CurrentUser;
import lk.swiftdrop.courier.service.CourierProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courier/profile")
@PreAuthorize("hasAnyRole('COURIER', 'ADMIN')")
public class CourierProfileController {

    private final CourierProfileService profileService;

    public CourierProfileController(CourierProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<CourierProfileResponse> getProfile() {
        UUID courierId = CurrentUser.current().userId();
        CourierProfileResponse response = profileService.getProfile(courierId);
        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<CourierProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        UUID courierId = CurrentUser.current().userId();
        CourierProfileResponse response = profileService.updateProfile(courierId, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/availability")
    public ResponseEntity<CourierProfileResponse> updateAvailability(
            @Valid @RequestBody UpdateAvailabilityRequest request) {
        UUID courierId = CurrentUser.current().userId();
        CourierProfileResponse response = profileService.updateAvailability(courierId, request.available());
        return ResponseEntity.ok(response);
    }
}
