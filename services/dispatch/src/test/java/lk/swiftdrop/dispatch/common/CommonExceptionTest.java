package lk.swiftdrop.dispatch.common;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class CommonExceptionTest {

    @Test
    @DisplayName("Exception classes return correct HttpStatus and ErrorCode")
    void exceptionStatusAndCode() {
        BadRequestException badReq = new BadRequestException("bad request", List.of(new ApiError.Detail("field", "err")));
        assertThat(badReq.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badReq.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(badReq.details()).hasSize(1);

        ConflictException conflict = new ConflictException("conflict");
        assertThat(conflict.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.code()).isEqualTo(ErrorCode.PARCEL_ALREADY_ASSIGNED);

        NotFoundException notFound = new NotFoundException("not found");
        assertThat(notFound.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFound.code()).isEqualTo(ErrorCode.NOT_FOUND);

        ForbiddenException forbidden = new ForbiddenException("forbidden");
        assertThat(forbidden.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.code()).isEqualTo(ErrorCode.FORBIDDEN);

        UnauthorizedException unauthorized = new UnauthorizedException("unauthorized");
        assertThat(unauthorized.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauthorized.code()).isEqualTo(ErrorCode.UNAUTHENTICATED);

        UnprocessableEntityException unprocessable = new UnprocessableEntityException("unprocessable");
        assertThat(unprocessable.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(unprocessable.code()).isEqualTo(ErrorCode.COURIER_CAPACITY_EXCEEDED);
    }
}
