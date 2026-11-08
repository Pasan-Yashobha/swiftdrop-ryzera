package lk.swiftdrop.dispatch.common;

public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHENTICATED, message);
    }
}
