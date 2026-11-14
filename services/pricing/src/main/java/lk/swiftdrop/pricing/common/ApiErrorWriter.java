package lk.swiftdrop.pricing.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * Writes the standard error JSON for failures in the security filters (401 and 403), which happen before any
 * controller runs and so never reach {@link ApiExceptionHandler}.
 */
@Component
public class ApiErrorWriter {
    private final ObjectMapper objectMapper;

    public ApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Use as an AuthenticationEntryPoint: no token, or a token that is invalid or expired. */
    public void unauthenticated(HttpServletRequest request, HttpServletResponse response,
                                AuthenticationException exception) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, "Sign in to continue.");
    }

    /** Use as an AccessDeniedHandler: a valid token whose role may not call this endpoint. */
    public void forbidden(HttpServletRequest request, HttpServletResponse response,
                          AccessDeniedException exception) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "You do not have permission to do that.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        ApiError body = ApiErrors.of(status, ApiErrors.codeFor(status), message, request, null);
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
