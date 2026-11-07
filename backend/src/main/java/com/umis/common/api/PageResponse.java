package com.umis.common.api;

import org.springframework.data.domain.Page;
import java.util.List;

/**
 * Standard paginated response envelope.
 *
 * @param <T> element type
 */
public record PageResponse<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean isFirst,
        boolean isLast
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
