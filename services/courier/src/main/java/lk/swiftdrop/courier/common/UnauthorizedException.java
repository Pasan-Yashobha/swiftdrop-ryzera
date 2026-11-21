package lk.swiftdrop.courier.common;

public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHENTICATED, message);
    }

    public UnauthorizedException(ErrorCode code, String message) {
        super(code, message);
    }
}
