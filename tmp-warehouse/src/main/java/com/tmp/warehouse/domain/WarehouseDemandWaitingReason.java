package com.tmp.warehouse.domain;

/**
 * Operational waiting attribute on a Demand line (not a business status).
 *
 * <p>Routing logic that sets these values belongs to later phases; B3B-1 only persists the
 * attribute.
 */
public enum WarehouseDemandWaitingReason {
    MATERIAL_UNMATCHED,
    MATERIAL_AMBIGUOUS,
    NO_AVAILABLE_STOCK,
    ROUTING_DEFERRED
}
