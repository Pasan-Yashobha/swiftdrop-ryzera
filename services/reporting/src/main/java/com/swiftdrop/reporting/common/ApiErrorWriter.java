package com.swiftdrop.reporting.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/**
 * Helper that serialises an {@link ApiError} to the HTTP response.
 * Used by security entry-point and access-denied handlers that run
 * outside the Spring MVC dispatcher (before {@link GlobalExceptionHandler}).
 */
@Component
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      int status, ErrorCode errorCode, String message) throws IOException {
        ApiError body = ApiError.of(status, errorCode, message,
                MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY),
                request.getRequestURI());
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
