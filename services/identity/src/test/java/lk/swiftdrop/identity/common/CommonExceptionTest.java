package lk.swiftdrop.identity.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommonExceptionTest {

    @Test
    void testCustomApiExceptions() {
        BadRequestException badReq = new BadRequestException(ErrorCode.VALIDATION_FAILED, "bad request message");
        assertThat(badReq.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badReq.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(badReq.getMessage()).isEqualTo("bad request message");

        BadRequestException badReqWithDetails = new BadRequestException("Validation failed",
                List.of(new ApiError.Detail("email", "must not be blank")));
        assertThat(badReqWithDetails.getDetails()).hasSize(1);
        assertThat(badReqWithDetails.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);

        NotFoundException notFound = new NotFoundException("not found message");
        assertThat(notFound.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFound.getCode()).isEqualTo(ErrorCode.NOT_FOUND);

        NotFoundException notFoundWithCode = new NotFoundException(ErrorCode.NOT_FOUND, "explicit not found");
        assertThat(notFoundWithCode.getCode()).isEqualTo(ErrorCode.NOT_FOUND);

        ForbiddenException forbidden = new ForbiddenException(ErrorCode.FORBIDDEN, "forbidden message");
        assertThat(forbidden.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getCode()).isEqualTo(ErrorCode.FORBIDDEN);

        ForbiddenException forbidden2 = new ForbiddenException("forbidden message default");
        assertThat(forbidden2.getCode()).isEqualTo(ErrorCode.FORBIDDEN);

        ConflictException conflict = new ConflictException(ErrorCode.INVALID_STATE, "conflict message");
        assertThat(conflict.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getCode()).isEqualTo(ErrorCode.INVALID_STATE);

        UnauthorizedException unauth = new UnauthorizedException("unauth message");
        assertThat(unauth.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauth.getCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);

        UnauthorizedException unauthWithCode = new UnauthorizedException(ErrorCode.INVALID_TOKEN, "invalid token");
        assertThat(unauthWithCode.getCode()).isEqualTo(ErrorCode.INVALID_TOKEN);

        TooManyRequestsException tmr = new TooManyRequestsException("locked", 60L);
        assertThat(tmr.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(tmr.getRetryAfterSeconds()).isEqualTo(60L);

        ApiError apiError = new ApiError(Instant.now(), 400, "Bad Request", ErrorCode.VALIDATION_FAILED,
                "msg", "/path", "corr-123", List.of(new ApiError.Detail("field", "error")));
        assertThat(apiError.status()).isEqualTo(400);
        assertThat(apiError.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(apiError.correlationId()).isEqualTo("corr-123");
        assertThat(apiError.details()).hasSize(1);
    }
}
