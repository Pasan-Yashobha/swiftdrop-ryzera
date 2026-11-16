package lk.swiftdrop.tracking.common;

/** An error reported to the client with a contract error code. The message must be safe to show to anyone. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
