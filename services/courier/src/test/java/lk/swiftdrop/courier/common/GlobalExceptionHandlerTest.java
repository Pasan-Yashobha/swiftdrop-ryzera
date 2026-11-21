package lk.swiftdrop.courier.common;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        ApiErrorFactory factory = new ApiErrorFactory(clock);
        handler = new GlobalExceptionHandler(factory);
    }

    @Test
    void shouldHandleNotFoundException() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/courier/profile");
        ServletWebRequest webRequest = new ServletWebRequest(request);

        NotFoundException ex = new NotFoundException("Courier profile not found");
        ResponseEntity<Object> response = handler.handleApiException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isInstanceOf(ApiError.class);
        ApiError error = (ApiError) response.getBody();
        assertThat(error.status()).isEqualTo(404);
        assertThat(error.code()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(error.message()).isEqualTo("Courier profile not found");
        assertThat(error.path()).isEqualTo("/api/v1/courier/profile");
    }

    @Test
    void shouldHandleForbiddenException() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/courier/deliveries/123/status");
        ServletWebRequest webRequest = new ServletWebRequest(request);

        ForbiddenException ex = new ForbiddenException("Courier is not assigned to this parcel");
        ResponseEntity<Object> response = handler.handleApiException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ApiError error = (ApiError) response.getBody();
        assertThat(error.status()).isEqualTo(403);
        assertThat(error.code()).isEqualTo(ErrorCode.FORBIDDEN);
    }
}
