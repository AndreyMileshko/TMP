package com.tmp.warehouse.domain;

/**
 * Warehouse operation types supported in v1.0.
 *
 * <p>Inter-warehouse Transfer is modeled as stages: {@link #TRANSFER_SEND} ({@code AVAILABLE →
 * IN_TRANSIT}), {@link #TRANSFER_RECEIVE} ({@code IN_TRANSIT → AVAILABLE} at destination), and
 * {@link #TRANSFER_RETURN} ({@code IN_TRANSIT → AVAILABLE} at source; Stage 3.5.8.3).
 */
public enum WarehouseOperationType {
    RECEIPT,
    MOVE,
    TRANSFER_SEND,
    TRANSFER_RECEIVE,
    TRANSFER_RETURN,
    RESERVATION,
    CONSUMPTION,
    ADJUSTMENT,
    INVENTORY
}
