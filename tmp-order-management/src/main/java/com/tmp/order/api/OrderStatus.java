package com.tmp.order.api;

/**
 * Commercial lifecycle status of a customer order in Stage 5 (Specification §8.1).
 *
 * <p>Only these three commercial statuses exist in Stage 5. Production-derived statuses
 * ({@code IN_PROGRESS}, {@code COMPLETED}) are intentionally excluded and are not owned by Order
 * Management. Transition rules between statuses are enforced by the Customer Order aggregate.
 */
public enum OrderStatus {

    /** Draft order; commercial fields are editable. */
    DRAFT,

    /** Approved order. */
    APPROVED,

    /** Cancelled order (Stage 5: only from {@link #DRAFT}). */
    CANCELLED
}
