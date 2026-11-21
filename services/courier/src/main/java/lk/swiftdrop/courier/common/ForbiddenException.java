package lk.swiftdrop.courier.common;

public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }

    public ForbiddenException(ErrorCode code, String message) {
        super(code, message);
    }
}
