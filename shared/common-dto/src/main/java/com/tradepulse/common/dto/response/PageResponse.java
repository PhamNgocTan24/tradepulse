package com.tradepulse.common.dto.response;

import java.util.List;

/**
 * Standard paginated response envelope.
 *
 * @param <T> the content item type
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size,
                                          long totalElements) {
        int totalPages = size == 0 ? 1 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages,
                page >= totalPages - 1);
    }
}
