package com.tmp.warehouse.domain;

/**
 * Stock state for Warehouse v1.0.
 *
 * <p>{@code RESERVED} is intentionally absent: reservation is an informational link and does not
 * change stock state.
 */
public enum StockState {
    AVAILABLE,
    IN_TRANSIT,
    BLOCKED
}
