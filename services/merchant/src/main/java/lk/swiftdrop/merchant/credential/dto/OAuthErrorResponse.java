package lk.swiftdrop.merchant.credential.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lk.swiftdrop.merchant.credential.OAuthException;

/** Error answer of the token endpoint (RFC 6749 section 5.2); not the service's usual error format. */
public record OAuthErrorResponse(
        @JsonProperty("error") String error,
        @JsonProperty("error_description") String errorDescription) {

    public static OAuthErrorResponse from(OAuthException exception) {
        return new OAuthErrorResponse(exception.getError().code(), exception.getDescription());
    }
}
