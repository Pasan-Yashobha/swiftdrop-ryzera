package lk.swiftdrop.pricing.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into the team's {@link ApiError} JSON. Spring's own errors (validation, unreadable JSON,
 * unknown path, wrong method) and our {@link ResponseStatusException}s all pass through
 * {@link #handleExceptionInternal}; anything unexpected becomes a 500 with a generic message.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Object> concurrentChange(OptimisticLockingFailureException exception, HttpServletRequest request) {
        return ApiErrors.response(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK",
                "This record changed during your request. Reload it and try again.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> conflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        return ApiErrors.response(HttpStatus.CONFLICT, "CONFLICT",
                "The zone conflicts with an existing record. Zone codes must be unique.", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception exception, HttpServletRequest request) {
        LOG.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), exception);
        return ApiErrors.response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                ApiErrors.GENERIC_SERVER_MESSAGE, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, @Nullable Object body,
                                                             HttpHeaders headers, HttpStatusCode status,
                                                             WebRequest webRequest) {
        HttpServletRequest request = ((ServletWebRequest) webRequest).getRequest();
        String code = ApiErrors.codeFor(status);
        // Spring passes no body for ResponseStatusException; its reason is then in the exception's own ProblemDetail.
        ProblemDetail problem = body instanceof ProblemDetail detail ? detail
                : exception instanceof ErrorResponse errorResponse ? errorResponse.getBody() : null;
        String message = problem != null && StringUtils.hasText(problem.getDetail())
                ? problem.getDetail()
                : ApiErrors.reason(status);
        List<ApiError.Detail> details = null;

        if (exception instanceof MethodArgumentNotValidException invalid) {
            code = "VALIDATION_FAILED";
            message = "Some fields are not valid.";
            details = invalid.getBindingResult().getFieldErrors().stream()
                    .map(error -> new ApiError.Detail(error.getField(), error.getDefaultMessage()))
                    .toList();
        } else if (exception instanceof HandlerMethodValidationException) {
            code = "VALIDATION_FAILED";
            message = "Some request values are not valid.";
        } else if (status.value() == 400 && !(exception instanceof ResponseStatusException)) {
            code = "MALFORMED_REQUEST";
        }
        if (status.is5xxServerError() && !(exception instanceof ResponseStatusException)) {
            message = ApiErrors.GENERIC_SERVER_MESSAGE;
        }

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.addAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(ApiErrors.of(status, code, message, request, details), responseHeaders, status);
    }
}
