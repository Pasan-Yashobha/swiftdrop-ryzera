package lk.swiftdrop.courier.common;

public class UnprocessableEntityException extends ApiException {

    public UnprocessableEntityException(String message) {
        super(ErrorCode.INVALID_STATUS_TRANSITION, message);
    }

    public UnprocessableEntityException(ErrorCode code, String message) {
        super(code, message);
    }
}
