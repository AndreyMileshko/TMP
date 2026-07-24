package com.tmp.order.api;

/**
 * Commercial lifecycle status of an order item in Stage 5 (Specification §9.1).
 *
 * <p>Production statuses ({@code NOT_STARTED}, {@code READY_FOR_PRODUCTION}, {@code IN_PRODUCTION},
 * {@code PARTIALLY_RELEASED}, {@code RELEASED}) are not order item statuses — they belong to
 * Production. Transition rules are enforced by the Order Item aggregate.
 */
public enum OrderItemStatus {

    /** Item is being formed; work happens on its draft revision. */
    DRAFT,

    /** Item has at least one approved revision and is available to other Capabilities. */
    ACTIVE,

    /** Item cancelled (Stage 5: only from {@link #DRAFT}). */
    CANCELLED
}
