package lk.swiftdrop.parcel.common;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends ApiException {
    public ServiceUnavailableException(ErrorCode code, String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.PARCEL_SERVICE_UNAVAILABLE, message);
    }
}
