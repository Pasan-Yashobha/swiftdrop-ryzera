package lk.swiftdrop.courier.common;

import java.util.List;

public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }

    public BadRequestException(ErrorCode code, String message) {
        super(code, message);
    }

    public BadRequestException(String message, List<ApiError.Detail> details) {
        super(ErrorCode.VALIDATION_FAILED, message, details);
    }
}
