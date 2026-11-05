package lk.swiftdrop.identity.security;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Duration;
import java.util.Map;
import lk.swiftdrop.identity.common.ErrorCode;
import lk.swiftdrop.identity.common.UnauthorizedException;
import lk.swiftdrop.identity.config.AppProperties;
import lk.swiftdrop.identity.dto.GoogleUserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class DefaultGoogleOAuthClient implements GoogleOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultGoogleOAuthClient.class);
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";

    private final RestClient restClient;
    private final AppProperties properties;

    public DefaultGoogleOAuthClient(AppProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public GoogleUserInfo exchangeCode(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", properties.google().clientId());
        body.add("client_secret", properties.google().clientSecret());
        body.add("redirect_uri", properties.google().redirectUri());
        body.add("grant_type", "authorization_code");

        try {
            Map<String, Object> response = restClient.post()
                    .uri(GOOGLE_TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || !response.containsKey("id_token")) {
                throw new UnauthorizedException(ErrorCode.INVALID_TOKEN, "Google token response did not contain id_token");
            }

            String idTokenString = (String) response.get("id_token");
            SignedJWT signedJwt = SignedJWT.parse(idTokenString);
            JWTClaimsSet claims = signedJwt.getJWTClaimsSet();

            String sub = claims.getSubject();
            String email = claims.getStringClaim("email");
            Boolean emailVerifiedClaim = claims.getBooleanClaim("email_verified");
            boolean emailVerified = Boolean.TRUE.equals(emailVerifiedClaim);
            String name = claims.getStringClaim("name");
            String picture = claims.getStringClaim("picture");

            return new GoogleUserInfo(sub, email, name, picture, emailVerified);
        } catch (ParseException e) {
            log.error("Failed to parse Google id_token", e);
            throw new UnauthorizedException(ErrorCode.INVALID_TOKEN, "Failed to parse Google ID token claims");
        } catch (Exception e) {
            log.error("Failed to communicate with Google token endpoint", e);
            throw new UnauthorizedException(ErrorCode.INVALID_TOKEN, "Google authorization code exchange failed");
        }
    }
}
