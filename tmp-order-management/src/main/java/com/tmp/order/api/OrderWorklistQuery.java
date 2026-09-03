package com.tmp.order.api;

import java.time.Instant;
import java.util.List;

/**
 * Read-only Order Management query for the operational Orders worklist.
 *
 * <p>Returns commercial rows only. Production-derived status is composed by a separate
 * integration/read layer. Period is mandatory. Row count is bounded by
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
     * Distinct customers observed on orders in {@code [createdFrom, createdToExclusive)}.
     * Includes an unassigned option when at least one matching order has a null {@code customerRef}.
     */
    List<OrderCustomerOptionDto> listWorklistCustomers(Instant createdFrom, Instant createdToExclusive);
}
