package lk.swiftdrop.merchant.order;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lk.swiftdrop.merchant.common.ApiError;
import lk.swiftdrop.merchant.common.ApiErrorFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Adds {@code Retry-After: 1} to the {@code IDEMPOTENCY_KEY_IN_USE} answer of the order endpoints. The body is the
 * standard error body; only these controllers need the extra header, so the global handler stays untouched.
 */
@RestControllerAdvice(assignableTypes = {MerchantOrderController.class, MerchantApiOrderController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
class IdempotencyConflictAdvice {

    private static final String RETRY_AFTER_SECONDS = "1";

    private final ApiErrorFactory errors;

    IdempotencyConflictAdvice(ApiErrorFactory errors) {
        this.errors = errors;
    }

    @ExceptionHandler(IdempotencyKeyInUseException.class)
    ResponseEntity<ApiError> keyInUse(IdempotencyKeyInUseException ex, HttpServletRequest request) {
        ApiError body = errors.create(ex.getStatus(), ex.getCode(), ex.getMessage(), request.getRequestURI(), List.of());
        return ResponseEntity.status(ex.getStatus())
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
