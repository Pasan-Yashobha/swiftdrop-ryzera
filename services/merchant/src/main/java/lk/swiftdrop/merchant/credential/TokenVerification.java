package lk.swiftdrop.merchant.credential;

/** Outcome of {@link AccessTokenService#verify}: the claims of a valid token or the reason it was refused. */
public sealed interface TokenVerification {

    record Valid(AccessTokenClaims claims) implements TokenVerification {
    }

    record Invalid(Failure reason) implements TokenVerification {
    }

    /** Why a token was refused; for logs and tests, never shown to the client. */
    enum Failure {
        MALFORMED, UNSUPPORTED_ALGORITHM, BAD_SIGNATURE, WRONG_ISSUER, EXPIRED, INVALID_CLAIMS
    }
}
