package lk.swiftdrop.identity.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lk.swiftdrop.identity.config.AppProperties;
import lk.swiftdrop.identity.domain.User;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_FULL_NAME = "fullName";
    public static final String CLAIM_FAMILY_ID = "familyId";

    private final RsaKeyProvider keyProvider;
    private final JWSSigner signer;
    private final JWSVerifier verifier;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtService(RsaKeyProvider keyProvider, AppProperties properties, Clock clock) {
        this.keyProvider = keyProvider;
        this.signer = new RSASSASigner(keyProvider.getPrivateKey());
        this.verifier = new RSASSAVerifier(keyProvider.getPublicKey());
        this.issuer = properties.security().jwtIssuer();
        this.accessTokenTtl = Duration.ofSeconds(properties.security().accessTokenTtlSeconds());
        this.clock = clock;
    }

    public String generateAccessToken(User user, UUID familyId) {
        Instant now = clock.instant();
        Instant expiry = now.plus(accessTokenTtl);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_FULL_NAME, user.getFullName())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_ROLES, List.of(user.getRole().name()))
                .claim(CLAIM_FAMILY_ID, familyId.toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiry))
                .jwtID(UUID.randomUUID().toString())
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(keyProvider.getKeyId())
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        try {
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign access token", e);
        }
    }

    public SignedJWT parseAndVerify(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!JWSAlgorithm.RS256.equals(signedJWT.getHeader().getAlgorithm())) {
                return null;
            }
            if (!signedJWT.verify(verifier)) {
                return null;
            }
            Date expiry = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (expiry == null || !expiry.toInstant().isAfter(clock.instant())) {
                return null;
            }
            return signedJWT;
        } catch (ParseException | JOSEException e) {
            return null;
        }
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public String getIssuer() {
        return issuer;
    }
}
