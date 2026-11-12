package lk.swiftdrop.identity.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.UUID;
import lk.swiftdrop.identity.dto.AuthSessionResponse;
import lk.swiftdrop.identity.dto.LoginRequest;
import lk.swiftdrop.identity.dto.RefreshRequest;
import lk.swiftdrop.identity.dto.SignupRequest;
import lk.swiftdrop.identity.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public static final String REFRESH_COOKIE_NAME = "refreshToken";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<AuthSessionResponse> signup(@Valid @RequestBody SignupRequest request,
                                                      HttpServletResponse response) {
        AuthSessionResponse session = authService.signup(request);
        attachRefreshCookie(response, session.refreshToken(), session.expiresIn());
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletRequest httpRequest,
                                                     HttpServletResponse response) {
        String clientIp = resolveClientIp(httpRequest);
        AuthSessionResponse session = authService.login(request, clientIp);
        attachRefreshCookie(response, session.refreshToken(), session.expiresIn());
        return ResponseEntity.ok(session);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthSessionResponse> refresh(@RequestBody(required = false) RefreshRequest request,
                                                       HttpServletRequest httpRequest,
                                                       HttpServletResponse response) {
        String rawToken = null;
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            rawToken = request.refreshToken();
        } else if (httpRequest.getCookies() != null) {
            rawToken = Arrays.stream(httpRequest.getCookies())
                    .filter(c -> REFRESH_COOKIE_NAME.equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        AuthSessionResponse session = authService.refresh(rawToken);
        attachRefreshCookie(response, session.refreshToken(), session.expiresIn());
        return ResponseEntity.ok(session);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse response,
                                       Authentication authentication) {
        String rawToken = null;
        if (request != null && request.refreshToken() != null) {
            rawToken = request.refreshToken();
        } else if (httpRequest.getCookies() != null) {
            rawToken = Arrays.stream(httpRequest.getCookies())
                    .filter(c -> REFRESH_COOKIE_NAME.equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        UUID userId = null;
        if (authentication != null && authentication.isAuthenticated()) {
            try {
                userId = UUID.fromString(authentication.getName());
            } catch (Exception ignored) {
            }
        }

        authService.logout(rawToken, userId);
        clearRefreshCookie(response);
        return ResponseEntity.ok().build();
    }

    private void attachRefreshCookie(HttpServletResponse response, String refreshToken, long expiresIn) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(false) // allows local development over HTTP; production reverse proxy sets secure
                .path("/api/v1/auth")
                .maxAge(7 * 24 * 3600)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .path("/api/v1/auth")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
