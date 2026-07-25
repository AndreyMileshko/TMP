package com.tmp.order.api;

import java.util.Optional;

/**
 * Read-only Public Query API of Order Management (Specification §15.1).
 *
 * <p>All operations are non-mutating. Absence is expressed as {@link Optional#empty()} or an empty
 * page — never {@code null}. Draft revisions and draft specifications are never returned: list and
 * get-by-number operations expose only {@link RevisionStatus#APPROVED} revisions; the current
 * production specification is {@link #getActiveOrderItemRevision(OrderItemId)}.
 *
 * <p>DTOs contain only Order Management data and never domain aggregates, persistence entities,
 * Production Status, stock positions, lots or Cutting Plan data.
 */
public interface OrderQueryService {

    /**
     * Searches customer orders by criteria with pagination and sort (Specification §15.1.1/§15.1.2).
     */
    PageResult<OrderSummaryDto> searchOrders(OrderSearchCriteria criteria, PageRequest pageRequest);

    Optional<OrderDto> getOrder(OrderId orderId);

    PageResult<OrderItemDto> getOrderItems(OrderId orderId, PageRequest pageRequest);

    Optional<OrderItemDto> getOrderItem(OrderItemId orderItemId);

    /**
     * Returns approved revisions only (draft revisions are omitted).
     */
    PageResult<OrderItemRevisionDto> getOrderItemRevisions(
            OrderItemId orderItemId, PageRequest pageRequest);

    /**
     * Returns the revision only when it exists and is {@link RevisionStatus#APPROVED}. Draft
     * revisions yield {@link Optional#empty()}.
     */
    Optional<OrderItemRevisionDto> getOrderItemRevision(
            OrderItemId orderItemId, RevisionNumber revisionNumber);

    /**
     * Returns the current active (approved) revision used as the production specification, if any.
     */
    Optional<OrderItemRevisionDto> getActiveOrderItemRevision(OrderItemId orderItemId);

    /**
     * Returns the specification of an approved revision. Draft specifications are not exposed.
     */
    Optional<ItemSpecificationDto> getItemSpecification(
            OrderItemId orderItemId, RevisionNumber revisionNumber);
}
