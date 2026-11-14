package com.swiftdrop.reporting.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Convenience factory used by tests and security handlers to build
 * a fully-populated {@link ApiError} without duplicating field logic.
 */
@Component
public class ApiErrorFactory {

    public ApiError unauthorised(HttpServletRequest request) {
        return ApiError.of(HttpStatus.UNAUTHORIZED.value(), ErrorCode.UNAUTHORIZED,
                "Authentication required", null, request.getRequestURI());
    }

    public ApiError forbidden(HttpServletRequest request) {
        return ApiError.of(HttpStatus.FORBIDDEN.value(), ErrorCode.FORBIDDEN,
                "Access denied", null, request.getRequestURI());
    }
}
