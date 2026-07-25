package com.tmp.order.api;

/**
 * Zero-based page request for Public Query API list operations (Specification §15.1.2).
 *
 * <p>Validated factories ({@link #of(int, int)}, {@link #firstPage()}) are defined in STAGE5-010
 * together with default/max page size and sort whitelist. This type holds the requested window.
 */
public final class PageRequest {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 100;

    private final int pageIndex;
    private final int pageSize;
    private final OrderSort orderSort;

    PageRequest(int pageIndex, int pageSize, OrderSort orderSort) {
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
        this.orderSort = orderSort;
    }

    /**
     * Creates a validated page request with default sorting
     * ({@code createdAt DESC}, {@code orderId DESC}).
     *
     * @throws IllegalArgumentException if {@code pageIndex < 0}, {@code pageSize < 1}, or
     *     {@code pageSize >} {@link #MAX_PAGE_SIZE}
     */
    public static PageRequest of(int pageIndex, int pageSize) {
        return of(pageIndex, pageSize, OrderSort.defaultSort());
    }

    /**
     * Creates a validated page request with an explicit sort.
     *
     * @throws IllegalArgumentException if page bounds are invalid
     */
    public static PageRequest of(int pageIndex, int pageSize, OrderSort orderSort) {
        if (pageIndex < 0) {
            throw new IllegalArgumentException("pageIndex must be >= 0 (zero-based): " + pageIndex);
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1: " + pageSize);
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "pageSize must be <= " + MAX_PAGE_SIZE + ": " + pageSize);
        }
        java.util.Objects.requireNonNull(orderSort, "orderSort");
        return new PageRequest(pageIndex, pageSize, orderSort);
    }

    /** First page ({@code pageIndex = 0}) with {@link #DEFAULT_PAGE_SIZE}. */
    public static PageRequest firstPage() {
        return of(0, DEFAULT_PAGE_SIZE);
    }

    public int pageIndex() {
        return pageIndex;
    }

    public int pageSize() {
        return pageSize;
    }

    public OrderSort orderSort() {
        return orderSort;
    }
}
