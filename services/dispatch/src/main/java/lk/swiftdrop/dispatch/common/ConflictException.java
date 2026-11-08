package lk.swiftdrop.dispatch.common;

public class ConflictException extends ApiException {
    public ConflictException(ErrorCode code, String message) {
        super(code, message);
    }

    public ConflictException(String message) {
        super(ErrorCode.PARCEL_ALREADY_ASSIGNED, message);
    }
}
