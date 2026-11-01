package lk.swiftdrop.merchant.credential;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
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
import java.util.stream.Stream;
import lk.swiftdrop.merchant.config.AppProperties;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the access tokens of the client-credentials grant: HS256 JWTs signed with
 * {@code swiftdrop.security.merchant-token-secret}, understood by this service only (CONTRACT section 3).
 *
 * <p>Verification is strict: the header must say HS256 (so {@code alg: none} and other algorithms are
 * refused), the signature must verify, the issuer must be ours, the token must not be expired and all
 * claims this service relies on must be present and well formed.
 */
@Service
public class AccessTokenService {

    private static final String ISSUER = "swiftdrop-merchant-service";
    private static final String CLAIM_MERCHANT_ID = "merchantId";
    private static final String CLAIM_CREDENTIAL_ID = "credentialId";
    private static final String CLAIM_ENVIRONMENT = "env";
    private static final String CLAIM_SCOPE = "scope";

    /** Real tokens are about 400 characters; anything this long is not worth parsing. */
    private static final int MAX_TOKEN_LENGTH = 4096;

    private static final Logger log = LoggerFactory.getLogger(AccessTokenService.class);

    private final JWSSigner signer;
    private final JWSVerifier verifier;
    private final Duration lifetime;
    private final Clock clock;

    public AccessTokenService(AppProperties properties, Clock clock) {
        byte[] secret = properties.security().merchantTokenSecretBytes();
        try {
            this.signer = new MACSigner(secret);
            this.verifier = new MACVerifier(secret);
        } catch (JOSEException e) {
            throw new IllegalStateException("MERCHANT_TOKEN_SECRET cannot be used as an HS256 key", e);
        }
        this.lifetime = Duration.ofSeconds(properties.security().accessTokenTtlSeconds());
        this.clock = clock;
    }

    public IssuedAccessToken issue(OAuthClient client, List<String> scopes) {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(client.getClientId())
                .claim(CLAIM_MERCHANT_ID, client.getMerchantId().toString())
                .claim(CLAIM_CREDENTIAL_ID, client.getId().toString())
                .claim(CLAIM_ENVIRONMENT, client.getEnvironment().name())
                .claim(CLAIM_SCOPE, Scopes.join(scopes))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(lifetime)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Access token cannot be signed", e);
        }
        return new IssuedAccessToken(jwt.serialize(), lifetime.toSeconds(), scopes);
    }

    public TokenVerification verify(String token) {
        if (token == null || token.length() > MAX_TOKEN_LENGTH) {
            return invalid(TokenVerification.Failure.MALFORMED);
        }
        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(token);
        } catch (ParseException e) {
            return invalid(TokenVerification.Failure.MALFORMED);
        }
        if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
            return invalid(TokenVerification.Failure.UNSUPPORTED_ALGORITHM);
        }
        if (!signatureIsValid(jwt)) {
            return invalid(TokenVerification.Failure.BAD_SIGNATURE);
        }
        JWTClaimsSet claims;
        try {
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            return invalid(TokenVerification.Failure.INVALID_CLAIMS);
        }
        if (!ISSUER.equals(claims.getIssuer())) {
            return invalid(TokenVerification.Failure.WRONG_ISSUER);
        }
        Date expiry = claims.getExpirationTime();
        if (expiry == null) {
            return invalid(TokenVerification.Failure.INVALID_CLAIMS);
        }
        if (!expiry.toInstant().isAfter(clock.instant())) {
            return invalid(TokenVerification.Failure.EXPIRED);
        }
        return readClaims(claims);
    }

    private boolean signatureIsValid(SignedJWT jwt) {
        try {
            return jwt.verify(verifier);
        } catch (JOSEException e) {
            return false;
        }
    }

    private static TokenVerification readClaims(JWTClaimsSet claims) {
        try {
            String clientId = claims.getSubject();
            String merchantId = claims.getStringClaim(CLAIM_MERCHANT_ID);
            String credentialId = claims.getStringClaim(CLAIM_CREDENTIAL_ID);
            String environment = claims.getStringClaim(CLAIM_ENVIRONMENT);
            String scope = claims.getStringClaim(CLAIM_SCOPE);
            if (Stream.of(clientId, merchantId, credentialId, environment, scope).anyMatch(Objects::isNull)) {
                return invalid(TokenVerification.Failure.INVALID_CLAIMS);
            }
            return new TokenVerification.Valid(new AccessTokenClaims(clientId, UUID.fromString(merchantId),
                    UUID.fromString(credentialId), ApiEnvironment.valueOf(environment), Scopes.split(scope)));
        } catch (ParseException | IllegalArgumentException e) {
            return invalid(TokenVerification.Failure.INVALID_CLAIMS);
        }
    }

    private static TokenVerification.Invalid invalid(TokenVerification.Failure reason) {
        log.debug("Access token refused: {}", reason);
        return new TokenVerification.Invalid(reason);
    }
}
