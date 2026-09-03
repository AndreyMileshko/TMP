package com.tmp.ui.shell.order.worklist;

import java.util.List;
import java.util.Objects;

/**
 * Paginated operational Orders list result. {@code totalElements} is the count after all filters,
 * including operational status.
 */
public final class OrderOperationalListResult {

    private final List<OrderOperationalSummary> content;
    private final int pageIndex;
    private final int pageSize;
    private final long totalElements;

    public OrderOperationalListResult(
            List<OrderOperationalSummary> content, int pageIndex, int pageSize, long totalElements) {
        this.content = List.copyOf(Objects.requireNonNull(content, "content"));
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
    }

    public List<OrderOperationalSummary> content() {
        return content;
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
