package com.tmp.order.api;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable page of query results (Specification §15.1.2).
 *
 * <p>Validation of page index / page size is performed by {@link PageRequest} factories
 * (STAGE5-010). This type only carries the result envelope.
 */
public final class PageResult<T> {

    private final List<T> content;
    private final int pageIndex;
    private final int pageSize;
    private final long totalElements;

    private PageResult(List<T> content, int pageIndex, int pageSize, long totalElements) {
        this.content = List.copyOf(content);
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
    }

    public static <T> PageResult<T> of(
            List<T> content, int pageIndex, int pageSize, long totalElements) {
        Objects.requireNonNull(content, "content");
        if (pageIndex < 0) {
            throw new IllegalArgumentException("pageIndex must be >= 0: " + pageIndex);
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1: " + pageSize);
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must be >= 0: " + totalElements);
        }
        return new PageResult<>(content, pageIndex, pageSize, totalElements);
    }

    /** Unmodifiable page content. */
    public List<T> content() {
        return Collections.unmodifiableList(content);
    }

    public int pageIndex() {
        return pageIndex;
    }

    public int pageSize() {
        return pageSize;
    }

    public long totalElements() {
        return totalElements;
    }
}
