package lk.swiftdrop.parcel.common;

import org.springframework.http.HttpStatus;

public class UnprocessableException extends ApiException {
    public UnprocessableException(ErrorCode code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public UnprocessableException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.PARCEL_REJECTED, message);
    }
}
