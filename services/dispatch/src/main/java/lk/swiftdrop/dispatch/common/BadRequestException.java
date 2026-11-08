package lk.swiftdrop.dispatch.common;

import java.util.List;

public class BadRequestException extends ApiException {
    public BadRequestException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }

    public BadRequestException(String message, List<ApiError.Detail> details) {
        super(ErrorCode.VALIDATION_FAILED, message, details);
    }
}
