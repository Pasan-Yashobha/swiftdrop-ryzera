package lk.swiftdrop.merchant.common;

import org.springframework.http.HttpStatus;

/** The caller is authenticated but not allowed to do this (403). */
public class ForbiddenException extends ApiException {

    public ForbiddenException(ErrorCode code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }

    public ForbiddenException(String message) {
        this(ErrorCode.FORBIDDEN, message);
    }
}
