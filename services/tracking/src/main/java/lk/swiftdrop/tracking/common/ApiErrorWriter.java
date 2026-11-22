package lk.swiftdrop.tracking.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Builds {@link ApiError} bodies, and writes them directly for errors raised outside Spring MVC (security). */
@Component
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ApiErrorWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ApiError build(ErrorCode code, String message, String path) {
        return new ApiError(clock.instant(), code.status().value(), code.status().getReasonPhrase(), code, message,
                path, CorrelationIdFilter.currentOrNew());
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), build(code, message, request.getRequestURI()));
    }
}
