package com.tmp.production.domain;

/**
 * Computed order-level Production View status (Production Spec §5.3).
 *
 * <p>Not persisted. {@link #NOT_ACCEPTED} means Production has no item-owned states for the order;
 * user-facing {@code AVAILABLE_FOR_PRODUCTION} requires a confirmed ACTIVE Order from Order
 * Management and is composed outside this calculator.
 */
public enum OrderProductionViewStatus {
    NOT_ACCEPTED,
    IN_PRODUCTION,
    MANUFACTURED,
    CANCELLED
}
