package lk.swiftdrop.webhook.common;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Validates the {@code page} and {@code size} query parameters shared by all list endpoints. */
public final class PageRequests {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    public static Pageable of(int page, int size, Sort sort) {
        List<ApiError.Detail> problems = new ArrayList<>();
        if (page < 0) {
            problems.add(new ApiError.Detail("page", "must be 0 or greater"));
        }
        if (size < 1 || size > MAX_SIZE) {
            problems.add(new ApiError.Detail("size", "must be between 1 and " + MAX_SIZE));
        }
        // The offset (page * size) is an int in Spring Data; a larger one would surface as an internal error.
        if (page >= 0 && size >= 1 && (long) page * size > Integer.MAX_VALUE) {
            problems.add(new ApiError.Detail("page", "is too large"));
        }
        if (!problems.isEmpty()) {
            throw new BadRequestException("Invalid pagination parameters", problems);
        }
        return PageRequest.of(page, size, sort);
    }
}
