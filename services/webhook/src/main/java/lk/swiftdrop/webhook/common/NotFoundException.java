package lk.swiftdrop.webhook.common;

import org.springframework.http.HttpStatus;

/** A resource does not exist or is not visible to the caller (404). */
public class NotFoundException extends ApiException {

    public NotFoundException(ErrorCode code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }

    public NotFoundException(String message) {
        this(ErrorCode.NOT_FOUND, message);
    }
}
