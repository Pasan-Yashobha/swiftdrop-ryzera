package lk.swiftdrop.merchant.common;

import org.springframework.http.HttpStatus;

/** The request is well formed but cannot be processed (422). */
public class UnprocessableException extends ApiException {

    public UnprocessableException(ErrorCode code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
