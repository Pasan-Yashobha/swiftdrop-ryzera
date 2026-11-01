package lk.swiftdrop.merchant.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

class ApiExceptionTest {

    @Test
    void typedExceptionsCarryTheirStatus() {
        assertThat(new NotFoundException("gone").getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(new NotFoundException("gone").getCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(new NotFoundException(ErrorCode.MERCHANT_NOT_REGISTERED, "none").getCode())
                .isEqualTo(ErrorCode.MERCHANT_NOT_REGISTERED);
        assertThat(new ConflictException(ErrorCode.DUPLICATE_EXTERNAL_REF, "dup").getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(new ForbiddenException("no").getCode()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(new ForbiddenException(ErrorCode.SANDBOX_ONLY, "no").getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(new UnauthorizedException("who").getCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
        assertThat(new UnauthorizedException(ErrorCode.INVALID_API_KEY, "bad").getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(new UnprocessableException(ErrorCode.PARCEL_REJECTED, "no").getStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(new ServiceUnavailableException(ErrorCode.PARCEL_SERVICE_UNAVAILABLE, "down").getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(new BadRequestException(ErrorCode.MALFORMED_REQUEST, "bad").getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void tooManyRequestsCarriesRetryAfter() {
        TooManyRequestsException exception = new TooManyRequestsException("slow down", 17);

        assertThat(exception.getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exception.getRetryAfterSeconds()).isEqualTo(17);
    }

    @Test
    void validationExceptionKeepsItsDetails() {
        List<ApiError.Detail> details = new ArrayList<>(List.of(new ApiError.Detail("city", "must not be blank")));

        BadRequestException exception = new BadRequestException("Invalid file", details);
        details.clear();

        assertThat(exception.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(exception.getDetails()).containsExactly(new ApiError.Detail("city", "must not be blank"));
    }

    @Test
    void baseExceptionCanCarryAnyCode() {
        ApiException exception = new ApiException(ErrorCode.PAYLOAD_TOO_LARGE, "too big");

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(exception.getDetails()).isEmpty();
        assertThat(exception).hasMessage("too big");
    }

    @Test
    void codeMustMatchTheExceptionType() {
        assertThatThrownBy(() -> new ConflictException(ErrorCode.NOT_FOUND, "wrong"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NOT_FOUND");
        assertThatThrownBy(() -> new NotFoundException(ErrorCode.INVALID_STATE, "wrong"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void everyCodeHasAnHttpErrorStatus(ErrorCode code) {
        assertThat(code.status().isError()).isTrue();
    }
}
