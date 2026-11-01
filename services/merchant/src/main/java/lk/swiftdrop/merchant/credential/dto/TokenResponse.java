package lk.swiftdrop.merchant.credential.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lk.swiftdrop.merchant.credential.IssuedAccessToken;
import lk.swiftdrop.merchant.credential.Scopes;

/**
 * Successful answer of the token endpoint (RFC 6749 section 5.1). {@link #toString()} leaves the token out.
 */
public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("scope") String scope) {

    public static TokenResponse from(IssuedAccessToken issued) {
        return new TokenResponse(issued.token(), "Bearer", issued.expiresInSeconds(), Scopes.join(issued.scopes()));
    }

    @Override
    public String toString() {
        return "TokenResponse[expiresIn=" + expiresIn + ", scope=" + scope + "]";
    }
}
