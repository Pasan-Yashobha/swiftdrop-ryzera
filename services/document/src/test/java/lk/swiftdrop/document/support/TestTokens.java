package lk.swiftdrop.document.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** Signs real HS256 tokens with the development secret, exactly like the dev token endpoints of the other services. */
public final class TestTokens {
    public static final String SECRET = "dev-only-hmac-secret-change-me-0123456789";

    private TestTokens() { }

    public static String token(String subject, String role) {
        try {
            var claims = new JWTClaimsSet.Builder().subject(subject).claim("role", role)
                    .issueTime(Date.from(Instant.now())).expirationTime(Date.from(Instant.now().plusSeconds(3600))).build();
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String bearer(String subject, String role) {
        return "Bearer " + token(subject, role);
    }
}
