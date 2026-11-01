package lk.swiftdrop.merchant.credential;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import lk.swiftdrop.merchant.credential.dto.OAuthErrorResponse;
import lk.swiftdrop.merchant.credential.dto.TokenResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth 2.0 token endpoint with the client-credentials grant (RFC 6749 section 4.4, CONTRACT section 3).
 * The client authenticates with HTTP Basic or with {@code client_id} / {@code client_secret} form fields.
 * Errors use the RFC's JSON format, not the service's usual error body, and no response is cacheable.
 */
@RestController
public class OAuthTokenController {

    private static final String BASIC_PREFIX = "Basic ";
    private static final String BASIC_CHALLENGE = "Basic realm=\"swiftdrop-merchant-api\", charset=\"UTF-8\"";

    private final OAuthTokenService tokenService;

    public OAuthTokenController(OAuthTokenService tokenService) {
        this.tokenService = tokenService;
    }

    // No "consumes": a wrong content type must get the RFC 6749 error body, not the service's usual 415 body.
    @PostMapping(path = MerchantApiPaths.TOKEN)
    public ResponseEntity<TokenResponse> token(
            HttpServletRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(name = "grant_type", required = false) String grantType,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "client_secret", required = false) String clientSecret,
            @RequestParam(name = "scope", required = false) String scope) {
        if (!isFormEncoded(request.getContentType())) {
            throw OAuthException.invalidRequest("The request body must be application/x-www-form-urlencoded");
        }
        if (request.getQueryString() != null) {
            // A secret in the URL would end up in access logs; RFC 6749 section 3.2 wants the parameters in the body.
            throw OAuthException.invalidRequest("Send the parameters in the request body, not in the URL");
        }
        ClientCredentials credentials = clientCredentials(authorization, clientId, clientSecret);
        IssuedAccessToken issued = tokenService.issueToken(credentials, grantType, scope);
        return ResponseEntity.ok().headers(noStoreHeaders()).body(TokenResponse.from(issued));
    }

    @ExceptionHandler(OAuthException.class)
    ResponseEntity<OAuthErrorResponse> handleOAuthError(OAuthException exception) {
        HttpHeaders headers = noStoreHeaders();
        if (exception.isBasicChallenge()) {
            headers.set(HttpHeaders.WWW_AUTHENTICATE, BASIC_CHALLENGE);
        }
        return ResponseEntity.status(exception.getError().status()).headers(headers)
                .body(OAuthErrorResponse.from(exception));
    }

    /** One client authentication method only (RFC 6749 section 2.3); none at all asks for Basic credentials. */
    private static ClientCredentials clientCredentials(String authorization, String clientId, String clientSecret) {
        boolean basic = authorization != null && authorization.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length());
        boolean form = clientId != null || clientSecret != null;
        if (basic && form) {
            throw OAuthException.invalidRequest("Use only one client authentication method");
        }
        if (basic) {
            return basicCredentials(authorization.substring(BASIC_PREFIX.length()).trim());
        }
        if (form) {
            return new ClientCredentials(clientId, Objects.requireNonNullElse(clientSecret, ""), false);
        }
        throw OAuthException.invalidClient(true);
    }

    /** {@code base64(urlencode(client_id) ":" urlencode(client_secret))}, see RFC 6749 section 2.3.1. */
    private static ClientCredentials basicCredentials(String encoded) {
        try {
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator < 0) {
                throw OAuthException.invalidClient(true);
            }
            return new ClientCredentials(
                    URLDecoder.decode(decoded.substring(0, separator), StandardCharsets.UTF_8),
                    URLDecoder.decode(decoded.substring(separator + 1), StandardCharsets.UTF_8), true);
        } catch (IllegalArgumentException notBase64OrBadEscape) {
            throw OAuthException.invalidClient(true);
        }
    }

    private static boolean isFormEncoded(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            return MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(MediaType.parseMediaType(contentType));
        } catch (InvalidMediaTypeException malformed) {
            return false;
        }
    }

    private static HttpHeaders noStoreHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        headers.set(HttpHeaders.PRAGMA, "no-cache");
        return headers;
    }
}
