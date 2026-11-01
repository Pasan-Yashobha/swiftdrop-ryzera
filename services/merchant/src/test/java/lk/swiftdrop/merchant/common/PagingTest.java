package lk.swiftdrop.merchant.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PagingTest {

    @Test
    void validParametersBuildAPageRequest() {
        Pageable pageable = PageRequests.of(2, 50, Sort.by("createdAt").descending());

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(50);
        assertThat(pageable.getSort()).isEqualTo(Sort.by("createdAt").descending());
    }

    @Test
    void anOffsetBeyondWhatTheDatabaseLayerCanAddressIsABadRequest() {
        assertThatThrownBy(() -> PageRequests.of(Integer.MAX_VALUE, 100, Sort.unsorted()))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getDetails())
                        .extracting(ApiError.Detail::field).containsExactly("page"));
        assertThat(PageRequests.of(Integer.MAX_VALUE / 100, 100, Sort.unsorted()).getPageNumber())
                .isEqualTo(Integer.MAX_VALUE / 100);
    }

    @Test
    void boundariesAreAccepted() {
        assertThat(PageRequests.of(0, 1, Sort.unsorted()).getPageSize()).isEqualTo(1);
        assertThat(PageRequests.of(0, PageRequests.MAX_SIZE, Sort.unsorted()).getPageSize()).isEqualTo(100);
    }

    @Test
    void invalidParametersAreReportedTogether() {
        assertThatThrownBy(() -> PageRequests.of(-1, 101, Sort.unsorted()))
                .isInstanceOfSatisfying(BadRequestException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.getDetails()).extracting(ApiError.Detail::field).containsExactly("page", "size");
                });
        assertThatThrownBy(() -> PageRequests.of(0, 0, Sort.unsorted())).isInstanceOf(BadRequestException.class);
    }

    @Test
    void pageResponseCarriesThePagingMetadata() {
        Page<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void pageResponseCanMapItsContent() {
        Page<Integer> page = new PageImpl<>(List.of(1, 2), PageRequest.of(0, 10), 2);

        assertThat(PageResponse.from(page, number -> "n" + number).content()).containsExactly("n1", "n2");
    }
}
