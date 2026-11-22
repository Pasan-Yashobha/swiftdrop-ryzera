package lk.swiftdrop.webhook.common;

import org.springframework.http.HttpStatus;

/** The request conflicts with the current state of a resource (409). */
public class ConflictException extends ApiException {

    public ConflictException(ErrorCode code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
