package lk.swiftdrop.parcel.common;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class CommonComponentsTest {

    @Test
    @DisplayName("Should create PageResponse directly and from Spring Page")
    void testPageResponse() {
        var page = new PageImpl<>(List.of("alpha", "beta"), PageRequest.of(0, 10), 2);
        PageResponse<String> response = PageResponse.from(page);
        assertThat(response.content()).containsExactly("alpha", "beta");
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(2L);
        assertThat(response.totalPages()).isEqualTo(1);

        PageResponse<Integer> mapped = PageResponse.from(page, String::length);
        assertThat(mapped.content()).containsExactly(5, 4);
    }

    @Test
    @DisplayName("Should instantiate BadRequestException and UnauthorizedException")
    void testExceptions() {
        BadRequestException bre = new BadRequestException(ErrorCode.MALFORMED_REQUEST, "invalid input");
        assertThat(bre.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bre.getCode()).isEqualTo(ErrorCode.MALFORMED_REQUEST);
        assertThat(bre.getMessage()).isEqualTo("invalid input");

        UnauthorizedException ue = new UnauthorizedException(ErrorCode.UNAUTHENTICATED, "missing auth");
        assertThat(ue.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ue.getCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
        assertThat(ue.getMessage()).isEqualTo("missing auth");
    }
}
