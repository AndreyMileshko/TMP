package com.tmp.order.api;

import java.util.List;

/**
 * Read-only Order Management query for the operational Orders worklist.
 *
 * <p>Returns commercial rows only. Production-derived status is composed by a separate
 * integration/read layer. Period is mandatory for worklist rows. Row count is bounded by
 * {@link OrderWorklistCriteria#MAX_ROWS}.
 */
public interface OrderWorklistQuery {

    /**
     * Lists commercial worklist rows matching the criteria, ordered by {@code createdAt DESC},
     * {@code orderId DESC}. Does not paginate: the caller composes operational status and then
     * paginates.
     */
    List<OrderWorklistRowDto> listWorklistRows(OrderWorklistCriteria criteria);

    /**
     * Distinct known customers observed across all Orders (period-independent filter catalogue).
     * Includes an unassigned option when at least one order has a null {@code customerRef}.
     * Ordered by {@code customerName}, then {@code customerRef}. Duplicate display names with
     * distinct refs are preserved.
     */
    List<OrderCustomerOptionDto> listKnownCustomers();
}
