package lk.swiftdrop.merchant.credential;

import java.util.Locale;
import org.springframework.http.HttpStatus;

/** A token request that failed with one of the error codes of RFC 6749 section 5.2. */
public class OAuthException extends RuntimeException {

    /** Error codes this endpoint can answer with. */
    public enum Error {
        INVALID_REQUEST(HttpStatus.BAD_REQUEST),
        INVALID_CLIENT(HttpStatus.UNAUTHORIZED),
        UNSUPPORTED_GRANT_TYPE(HttpStatus.BAD_REQUEST),
        INVALID_SCOPE(HttpStatus.BAD_REQUEST);

        private final HttpStatus status;

        Error(HttpStatus status) {
            this.status = status;
        }

        public HttpStatus status() {
            return status;
        }

        /** The value of the {@code error} member, e.g. {@code invalid_client}. */
        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Error error;
    private final boolean basicChallenge;

    private OAuthException(Error error, String description, boolean basicChallenge) {
        super(description);
        this.error = error;
        this.basicChallenge = basicChallenge;
    }

    public static OAuthException invalidRequest(String description) {
        return new OAuthException(Error.INVALID_REQUEST, description, false);
    }

    /**
     * The same answer for every kind of client authentication failure, so that it never reveals whether a
     * client id exists.
     *
     * @param basicChallenge whether the response should ask for HTTP Basic credentials
     */
    public static OAuthException invalidClient(boolean basicChallenge) {
        return new OAuthException(Error.INVALID_CLIENT, "Client authentication failed", basicChallenge);
    }

    public static OAuthException unsupportedGrantType() {
        return new OAuthException(Error.UNSUPPORTED_GRANT_TYPE, "Only the client_credentials grant is supported",
                false);
    }

    public static OAuthException invalidScope() {
        return new OAuthException(Error.INVALID_SCOPE, "The requested scope exceeds the scope of the client", false);
    }

    public Error getError() {
        return error;
    }

    /** The description for the {@code error_description} member. */
    public String getDescription() {
        return getMessage();
    }

    public boolean isBasicChallenge() {
        return basicChallenge;
    }
}
