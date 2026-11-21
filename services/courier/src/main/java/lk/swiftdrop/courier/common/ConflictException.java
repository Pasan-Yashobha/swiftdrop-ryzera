package lk.swiftdrop.courier.common;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(ErrorCode.INVALID_STATE, message);
    }

    public ConflictException(ErrorCode code, String message) {
        super(code, message);
    }
}
