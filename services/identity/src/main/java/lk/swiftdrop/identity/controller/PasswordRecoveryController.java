package lk.swiftdrop.identity.controller;

import jakarta.validation.Valid;
import lk.swiftdrop.identity.dto.ForgotPasswordRequest;
import lk.swiftdrop.identity.dto.MessageResponse;
import lk.swiftdrop.identity.dto.ResetPasswordRequest;
import lk.swiftdrop.identity.service.PasswordRecoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class PasswordRecoveryController {

    private final PasswordRecoveryService recoveryService;

    public PasswordRecoveryController(PasswordRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        MessageResponse response = recoveryService.requestPasswordReset(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        MessageResponse response = recoveryService.resetPassword(request);
        return ResponseEntity.ok(response);
    }
}

