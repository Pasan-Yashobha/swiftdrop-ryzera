package lk.swiftdrop.parcel.common;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {
    public ConflictException(ErrorCode code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }
}
