package lk.swiftdrop.identity.common;

import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ApiErrorFactory errors;

    public GlobalExceptionHandler(ApiErrorFactory errors) {
        this.errors = errors;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof TooManyRequestsException tooMany) {
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(tooMany.getRetryAfterSeconds()));
        }
        return respond(ex.getStatus(), ex.getCode(), ex.getMessage(), request, ex.getDetails(), headers);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Object> handleOptimisticLock(OptimisticLockingFailureException ex, WebRequest request) {
        return respond(HttpStatus.CONFLICT, ErrorCode.OPTIMISTIC_LOCK,
                "The resource was changed by someone else. Reload it and try again", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Data integrity violation on {}: {}", path(request), ex.getClass().getSimpleName());
        return respond(HttpStatus.CONFLICT, ErrorCode.INVALID_STATE,
                "The request conflicts with the current state of the resource", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<ApiError.Detail> details = ex.getConstraintViolations().stream()
                .map(violation -> new ApiError.Detail(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Validation failed", request, details,
                new HttpHeaders());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return respond(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "You do not have permission to perform this action",
                request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Authentication is required", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception on {}", path(request), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Please quote the correlation id when contacting support", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiError.Detail> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.add(new ApiError.Detail(fieldError.getField(), fieldError.getDefaultMessage()));
        }
        for (ObjectError objectError : ex.getBindingResult().getGlobalErrors()) {
            details.add(new ApiError.Detail(objectError.getObjectName(), objectError.getDefaultMessage()));
        }
        return respond(status, ErrorCode.VALIDATION_FAILED, "Validation failed", request, details, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiError.Detail> details = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ApiError.Detail(result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return respond(status, ErrorCode.VALIDATION_FAILED, "Validation failed", request, details, headers);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return respond(status, ErrorCode.VALIDATION_FAILED, "Validation failed", request,
                List.of(new ApiError.Detail(ex.getParameterName(), "is required")), headers);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return respond(status, ErrorCode.VALIDATION_FAILED, "Validation failed", request,
                List.of(new ApiError.Detail(ex.getPropertyName(), "has an invalid value")), headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiError.Detail> details = List.of();
        if (ex.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            String field = mapping.getPath().stream()
                    .map(reference -> reference.getFieldName() != null
                            ? reference.getFieldName() : "[" + reference.getIndex() + "]")
                    .collect(Collectors.joining("."));
            details = List.of(new ApiError.Detail(field, "has an invalid value or type"));
        }
        return respond(status, ErrorCode.MALFORMED_REQUEST, "The request body is missing or malformed", request,
                details, headers);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = codeFor(statusCode);
        String message = statusCode.is5xxServerError()
                ? "An unexpected error occurred. Please quote the correlation id when contacting support"
                : messageFor(code);
        if (statusCode.is5xxServerError()) {
            log.error("Unhandled framework exception on {}", path(request), ex);
        }
        return respond(statusCode, code, message, request, List.of(), headers);
    }

    private static ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.MALFORMED_REQUEST;
        };
    }

    private static String messageFor(ErrorCode code) {
        return switch (code) {
            case NOT_FOUND -> "The requested resource was not found";
            case METHOD_NOT_ALLOWED -> "This HTTP method is not supported for this endpoint";
            case PAYLOAD_TOO_LARGE -> "The request is too large";
            case UNSUPPORTED_MEDIA_TYPE -> "This content type is not supported";
            default -> "The request could not be processed";
        };
    }

    private ResponseEntity<Object> respond(HttpStatusCode status, ErrorCode code, String message, WebRequest request) {
        return respond(status, code, message, request, List.of(), new HttpHeaders());
    }

    private ResponseEntity<Object> respond(HttpStatusCode status, ErrorCode code, String message, WebRequest request,
            List<ApiError.Detail> details, HttpHeaders headers) {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        ApiError body = errors.create(status, code, message, path(request), details);
        return new ResponseEntity<>(body, responseHeaders, status);
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest ? servletRequest.getRequest().getRequestURI() : "";
    }
}
