package lk.swiftdrop.merchant.common;

import org.springframework.http.HttpStatus;

/** The caller could not be authenticated (401). */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(ErrorCode code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message);
    }

    public UnauthorizedException(String message) {
        this(ErrorCode.UNAUTHENTICATED, message);
    }
}
