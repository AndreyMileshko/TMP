package com.tmp.ui.shell.order.worklist;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Paginated operational Orders list result. {@code totalElements} is the count after all filters,
 * including operational status.
 */
public final class OrderOperationalListResult {

    public enum ProductionFactsState {
        AVAILABLE,
        ACCESS_DENIED,
        TECHNICAL_FAILURE
    }

    private final List<OrderOperationalSummary> content;
    private final int pageIndex;
    private final int pageSize;
    private final long totalElements;
    private final ProductionFactsState productionFactsState;
    private final RuntimeException technicalFailure;

    public OrderOperationalListResult(
            List<OrderOperationalSummary> content,
            int pageIndex,
            int pageSize,
            long totalElements,
            ProductionFactsState productionFactsState) {
        this(content, pageIndex, pageSize, totalElements, productionFactsState, null);
    }

    public OrderOperationalListResult(
            List<OrderOperationalSummary> content,
            int pageIndex,
            int pageSize,
            long totalElements,
            ProductionFactsState productionFactsState,
            RuntimeException technicalFailure) {
        this.content = List.copyOf(Objects.requireNonNull(content, "content"));
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.productionFactsState =
                Objects.requireNonNull(productionFactsState, "productionFactsState");
        this.technicalFailure = technicalFailure;
        if (productionFactsState == ProductionFactsState.TECHNICAL_FAILURE
                && technicalFailure == null) {
            throw new IllegalArgumentException("technicalFailure required for TECHNICAL_FAILURE");
        }
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

    public ProductionFactsState productionFactsState() {
        return productionFactsState;
    }

    public Optional<RuntimeException> technicalFailure() {
        return Optional.ofNullable(technicalFailure);
    }
}
