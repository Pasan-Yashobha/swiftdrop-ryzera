package lk.swiftdrop.identity.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("swiftdrop")
public record AppProperties(
        @Valid @NotNull @DefaultValue Security security,
        @Valid @NotNull @DefaultValue GoogleOAuth google) {

    public record Security(
            @DefaultValue("swiftdrop-identity") String jwtIssuer,
            @DefaultValue("") String jwtPrivateKeyPem,
            @DefaultValue("") String jwtPublicKeyPem,
            @DefaultValue("900") @Min(60) @Max(86400) int accessTokenTtlSeconds,
            @DefaultValue("7") @Min(1) @Max(90) int refreshTokenTtlDays,
            @DefaultValue("30") @Min(1) @Max(365) int refreshTokenFamilyMaxDays,
            @NotBlank(message = "INTERNAL_API_TOKEN (swiftdrop.security.internal-token) must be set") String internalToken,
            @DefaultValue("http://localhost:4200,http://localhost:4201") List<String> corsAllowedOrigins,
            @DefaultValue("5") @Min(1) int maxFailedAttempts,
            @DefaultValue("15") @Min(1) int lockoutDurationMinutes,
            @DefaultValue("12") @Min(4) @Max(31) int bcryptCost) {
    }

    public record GoogleOAuth(
            @DefaultValue("") String clientId,
            @DefaultValue("") String clientSecret,
            @DefaultValue("http://localhost:8080/api/v1/auth/google/callback") String redirectUri,
            @DefaultValue("http://localhost:4200") String frontendUrl) {
    }
}
