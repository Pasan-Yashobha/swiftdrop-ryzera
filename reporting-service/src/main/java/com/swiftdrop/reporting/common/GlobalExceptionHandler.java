package com.swiftdrop.reporting.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts all exceptions into the team's shared {@link ApiError} JSON format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest req) {
        ApiError body = ApiError.of(
                ex.getStatus().value(),
                ex.getErrorCode(),
                ex.getMessage(),
                MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY),
                req.getRequestURI());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "; " + b);
        ApiError body = ApiError.of(400, ErrorCode.VALIDATION_ERROR, message,
                MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY), req.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex, HttpServletRequest req) {
        // Never expose internal details – log them instead
        ApiError body = ApiError.of(500, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred",
                MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY),
                req.getRequestURI());
        return ResponseEntity.internalServerError().body(body);
    }
}
