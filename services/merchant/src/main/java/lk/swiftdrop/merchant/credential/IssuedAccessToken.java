package lk.swiftdrop.merchant.credential;

import java.util.List;

/**
 * A signed access token and what to tell the client about it. {@link #toString()} leaves the token out so
 * that it cannot end up in a log.
 *
 * @param token            the compact JWT
 * @param expiresInSeconds lifetime from now
 * @param scopes           granted scopes in canonical order
 */
public record IssuedAccessToken(String token, long expiresInSeconds, List<String> scopes) {

    @Override
    public String toString() {
        return "IssuedAccessToken[expiresInSeconds=" + expiresInSeconds + ", scopes=" + scopes + "]";
    }
}
