package lk.swiftdrop.merchant.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lk.swiftdrop.merchant.common.ApiErrorWriter;
import lk.swiftdrop.merchant.common.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Answers unauthenticated requests with the standard JSON error (401) instead of an empty body. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorWriter errorWriter;

    public RestAuthenticationEntryPoint(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        if (authException instanceof OAuth2AuthenticationException) {
            errorWriter.write(request, response, ErrorCode.INVALID_TOKEN, "The access token is invalid or has expired");
        } else {
            errorWriter.write(request, response, ErrorCode.UNAUTHENTICATED, "Authentication is required");
        }
    }
}
