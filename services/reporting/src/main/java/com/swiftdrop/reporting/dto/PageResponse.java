package com.swiftdrop.reporting.dto;

import java.util.List;

/**
 * Generic pagination wrapper matching identity service convention.
 */
public record PageResponse<T>(
        List<T> content,
        int     page,
        int     size,
        long    totalElements,
        int     totalPages
) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 1 : (int) Math.ceil((double) totalElements / (double) size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }
}
