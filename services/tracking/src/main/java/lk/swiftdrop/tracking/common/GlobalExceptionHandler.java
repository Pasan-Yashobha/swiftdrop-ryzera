package lk.swiftdrop.tracking.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every exception that leaves a controller to {@link ApiError}. Messages are fixed or come from our own
 * exceptions; nothing from Redis, stack traces or request values is echoed. The content type is set explicitly so
 * an error on the SSE endpoint (which the browser requested as text/event-stream) is still written as JSON.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ApiErrorWriter errors;

    public GlobalExceptionHandler(ApiErrorWriter errors) {
        this.errors = errors;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException e, HttpServletRequest request) {
        return respond(e.getCode(), e.getMessage(), request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ApiError> handleNotFound(Exception e, HttpServletRequest request) {
        return respond(ErrorCode.NOT_FOUND, "Not found", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, "Method not allowed", request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ApiError> handleNotAcceptable(HttpMediaTypeNotAcceptableException e, HttpServletRequest request) {
        return respond(ErrorCode.NOT_ACCEPTABLE, "Response type not supported", request);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ApiError> handleBadRequest(Exception e, HttpServletRequest request) {
        return respond(ErrorCode.VALIDATION_FAILED, "The request is not valid", request);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> handleStoreDown(DataAccessException e, HttpServletRequest request) {
        log.warn("Read model unavailable on {}: {}", request.getRequestURI(), e.getClass().getSimpleName());
        return respond(ErrorCode.SERVICE_UNAVAILABLE, "Tracking is temporarily unavailable. Please try again shortly.",
                request);
    }

    /** A live stream ended (timeout or the browser left); the response is already committed, nothing to write. */
    @ExceptionHandler({AsyncRequestTimeoutException.class, AsyncRequestNotUsableException.class})
    void handleStreamClosed(Exception e, HttpServletResponse response) {
        log.debug("Stream closed: {}", e.getClass().getSimpleName());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception e, HttpServletRequest request, HttpServletResponse response) {
        log.error("Unexpected error on {}", request.getRequestURI(), e);
        if (response.isCommitted()) {
            return null;
        }
        return respond(ErrorCode.INTERNAL_ERROR, "Something went wrong. Please try again.", request);
    }

    private ResponseEntity<ApiError> respond(ErrorCode code, String message, HttpServletRequest request) {
        return ResponseEntity.status(code.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(errors.build(code, message, request.getRequestURI()));
    }
}
