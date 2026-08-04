package com.tmp.order.api;

/**
 * Commercial lifecycle status of a customer order in Stage 5 (Specification §8.1 / ADR-031).
 *
 * <p>Production-derived statuses ({@code IN_PROGRESS}, {@code COMPLETED}) are intentionally
 * excluded and are not owned by Order Management. Transition rules between statuses are enforced by
 * the Customer Order aggregate. {@link #ACTIVE} is uniform regardless of creation channel.
 */
public enum OrderStatus {

    /** Draft order; commercial fields are editable (manual path). */
    DRAFT,

    /** Commercially approved; not yet downstream-ready (manual path). */
    APPROVED,

    /** Downstream-ready production baseline; identical for manual and import paths. */
    ACTIVE,

    /** Cancelled order (Stage 5: only from {@link #DRAFT}). */
    CANCELLED
}
