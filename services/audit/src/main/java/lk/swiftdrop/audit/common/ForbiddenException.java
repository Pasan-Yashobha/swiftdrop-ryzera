package lk.swiftdrop.audit.common;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApiException {

    public ForbiddenException(ErrorCode code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }

    public ForbiddenException(String message) {
        this(ErrorCode.FORBIDDEN, message);
    }
}
