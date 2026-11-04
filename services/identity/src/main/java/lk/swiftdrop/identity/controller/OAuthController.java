package lk.swiftdrop.identity.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import lk.swiftdrop.identity.config.AppProperties;
import lk.swiftdrop.identity.dto.AuthSessionResponse;
import lk.swiftdrop.identity.dto.GoogleUserInfo;
import lk.swiftdrop.identity.security.GoogleOAuthClient;
import lk.swiftdrop.identity.security.TokenUtils;
import lk.swiftdrop.identity.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/auth/google")
public class OAuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuthController.class);
    public static final String OAUTH_STATE_COOKIE = "oauth_state";
    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";

    private final AppProperties properties;
    private final GoogleOAuthClient googleOAuthClient;
    private final AuthService authService;

    public OAuthController(AppProperties properties,
                           GoogleOAuthClient googleOAuthClient,
                           AuthService authService) {
        this.properties = properties;
        this.googleOAuthClient = googleOAuthClient;
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<Void> initiateGoogleLogin(HttpServletResponse response) {
        String state = TokenUtils.generateRawRefreshToken();

        ResponseCookie stateCookie = ResponseCookie.from(OAUTH_STATE_COOKIE, state)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(Duration.ofMinutes(5))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie.toString());

        String googleConsentUrl = UriComponentsBuilder.fromUriString(GOOGLE_AUTH_URL)
                .queryParam("client_id", properties.google().clientId())
                .queryParam("redirect_uri", properties.google().redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .build()
                .toUriString();

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(googleConsentUrl))
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> handleGoogleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response) {

        String frontendUrl = properties.google().frontendUrl();
        clearStateCookie(response);

        // 1. User denied consent
        if (error != null && !error.isBlank()) {
            log.info("Google OAuth login was denied or cancelled: {}", error);
            return redirectTo(frontendUrl + "/login?error=oauth_denied");
        }

        // 2. Validate CSRF state nonce
        String cookieState = getCookieValue(request, OAUTH_STATE_COOKIE);
        if (cookieState == null || state == null || !constantTimeEquals(cookieState, state)) {
            log.warn("Google OAuth CSRF state nonce mismatch or missing!");
            return redirectTo(frontendUrl + "/login?error=csrf_detected");
        }

        if (code == null || code.isBlank()) {
            return redirectTo(frontendUrl + "/login?error=oauth_denied");
        }

        // 3. Exchange code with Google
        GoogleUserInfo googleUser;
        try {
            googleUser = googleOAuthClient.exchangeCode(code);
        } catch (Exception e) {
            log.error("Failed to exchange authorization code with Google", e);
            return redirectTo(frontendUrl + "/login?error=oauth_failed");
        }

        // 4. Verify email verified claim
        if (!googleUser.emailVerified()) {
            log.warn("Google account email is not verified: {}", googleUser.email());
            return redirectTo(frontendUrl + "/login?error=unverified_google_email");
        }

        // 5. User provisioning with Anti-Pre-Hijacking defense
        AuthSessionResponse session = authService.handleGoogleLogin(googleUser);

        // 6. Set refreshToken cookie
        ResponseCookie refreshCookie = ResponseCookie.from(AuthController.REFRESH_COOKIE_NAME, session.refreshToken())
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(Duration.ofDays(properties.security().refreshTokenTtlDays()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        // 7. Clean browser redirect to frontend with zero tokens in URL
        return redirectTo(frontendUrl + "/dashboard");
    }

    private ResponseEntity<Void> redirectTo(String url) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
    }

    private void clearStateCookie(HttpServletResponse response) {
        ResponseCookie clearCookie = ResponseCookie.from(OAUTH_STATE_COOKIE, "")
                .httpOnly(true)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}

