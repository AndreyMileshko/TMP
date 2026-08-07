package com.tmp.warehouse.domain;

/**
 * Warehouse operation types supported in v1.0.
 *
 * <p>Inter-warehouse Transfer is modeled as two stages: {@link #TRANSFER_SEND} ({@code AVAILABLE →
 * IN_TRANSIT}) and {@link #TRANSFER_RECEIVE} ({@code IN_TRANSIT → AVAILABLE}).
 */
public enum WarehouseOperationType {
    RECEIPT,
    MOVE,
    TRANSFER_SEND,
    TRANSFER_RECEIVE,
    RESERVATION,
    CONSUMPTION,
    ADJUSTMENT,
    INVENTORY
}
