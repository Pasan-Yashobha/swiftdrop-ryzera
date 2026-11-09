package lk.swiftdrop.identity.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lk.swiftdrop.identity.dto.ChangePasswordRequest;
import lk.swiftdrop.identity.dto.UpdateProfileRequest;
import lk.swiftdrop.identity.dto.UserResponse;
import lk.swiftdrop.identity.security.CurrentUser;
import lk.swiftdrop.identity.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;
    private final CurrentUser currentUser;

    public UserController(UserService userService, CurrentUser currentUser) {
        this.userService = userService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public UserResponse getProfile() {
        UUID userId = currentUser.getUserId();
        return userService.getProfile(userId);
    }

    @PutMapping
    public UserResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = currentUser.getUserId();
        return userService.updateProfile(userId, request);
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = currentUser.getUserId();
        UUID familyId = currentUser.getFamilyId();
        userService.changePassword(userId, familyId, request);
        return ResponseEntity.ok().build();
    }
}
