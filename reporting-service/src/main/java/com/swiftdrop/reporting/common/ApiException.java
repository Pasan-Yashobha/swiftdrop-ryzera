package com.swiftdrop.reporting.common;

import org.springframework.http.HttpStatus;

/**
 * Base exception for all domain-level errors that map to HTTP responses.
 * Carry an {@link ErrorCode} so the global handler can build a consistent body.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode  errorCode;

    public ApiException(HttpStatus status, ErrorCode errorCode, String message) {
        super(message);
        this.status    = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus()    { return status;    }
    public ErrorCode  getErrorCode() { return errorCode; }
}
