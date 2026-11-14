package lk.swiftdrop.webhook.common;

import java.util.List;
import org.springframework.http.HttpStatus;

/** The request is invalid (400), optionally with one detail per offending field. */
public class BadRequestException extends ApiException {

    public BadRequestException(ErrorCode code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }

    public BadRequestException(String message, List<ApiError.Detail> details) {
        super(ErrorCode.VALIDATION_FAILED, message, details);
    }
}
