package com.tmp.warehouse.domain;

/**
 * Warehouse Operation lifecycle status (STAGE6-006).
 *
 * <p>Simple lifecycle: {@code DRAFT → COMPLETED} on success, {@code DRAFT → FAILED} on error.
 */
public enum WarehouseOperationStatus {
    DRAFT,
    COMPLETED,
    FAILED
}
