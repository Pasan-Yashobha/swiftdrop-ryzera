package lk.swiftdrop.merchant.common;

import org.springframework.http.HttpStatus;

/** A downstream dependency is unavailable (503). */
public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(ErrorCode code, String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
