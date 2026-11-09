package lk.swiftdrop.dispatch.common;

public class UnprocessableEntityException extends ApiException {
    public UnprocessableEntityException(ErrorCode code, String message) {
        super(code, message);
    }

    public UnprocessableEntityException(String message) {
        super(ErrorCode.COURIER_CAPACITY_EXCEEDED, message);
    }
}

