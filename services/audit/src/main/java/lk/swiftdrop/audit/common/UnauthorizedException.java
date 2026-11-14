package lk.swiftdrop.audit.common;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiException {

    public UnauthorizedException(ErrorCode code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message);
    }

    public UnauthorizedException(String message) {
        this(ErrorCode.UNAUTHENTICATED, message);
    }
}
