package lk.swiftdrop.identity.security;

import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.identity.config.AppProperties;
import lk.swiftdrop.identity.domain.Role;
import lk.swiftdrop.identity.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private RsaKeyProvider keyProvider;

    @BeforeEach
    void setUp() {
        AppProperties.Security sec = new AppProperties.Security(
                "swiftdrop-identity", "", "", 900, 7, 30, "test-token", List.of(), 5, 15, 4
        );
        AppProperties.GoogleOAuth google = new AppProperties.GoogleOAuth("", "", "", "");
        AppProperties props = new AppProperties(sec, google);
        this.keyProvider = new RsaKeyProvider(props);
        this.jwtService = new JwtService(keyProvider, props, Clock.systemUTC());
    }

    @Test
    void generatesAndVerifiesValidToken() throws Exception {
        User user = User.register("Kasun Silva", "kasun@example.com", "+94771234567", "hash", Role.MERCHANT, Instant.now());
        UUID familyId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(user, familyId);
        assertThat(token).isNotBlank();

        SignedJWT verified = jwtService.parseAndVerify(token);
        assertThat(verified).isNotNull();
        assertThat(verified.getJWTClaimsSet().getSubject()).isEqualTo(user.getId().toString());
        assertThat(verified.getJWTClaimsSet().getStringClaim(JwtService.CLAIM_EMAIL)).isEqualTo("kasun@example.com");
        assertThat(verified.getJWTClaimsSet().getStringClaim(JwtService.CLAIM_ROLE)).isEqualTo("MERCHANT");
        assertThat(verified.getJWTClaimsSet().getStringClaim(JwtService.CLAIM_FAMILY_ID)).isEqualTo(familyId.toString());
    }

    @Test
    void rejectsTamperedToken() {
        User user = User.register("Nimal Perera", "nimal@example.com", null, "hash", Role.CUSTOMER, Instant.now());
        String token = jwtService.generateAccessToken(user, UUID.randomUUID());

        // Alter token signature
        String tampered = token.substring(0, token.length() - 5) + "abcde";
        assertThat(jwtService.parseAndVerify(tampered)).isNull();
    }
}
