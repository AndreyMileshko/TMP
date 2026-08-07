package com.tmp.warehouse.domain;

/**
 * Warehouse operation types supported in v1.0 (description level).
 *
 * <p>Execution of these operations is out of scope for the domain model foundation task.
 */
public enum WarehouseOperationType {
    RECEIPT,
    MOVE,
    TRANSFER,
    RESERVATION,
    CONSUMPTION,
    ADJUSTMENT,
    INVENTORY
}
