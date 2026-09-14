package com.tmp.production.domain;

import java.util.UUID;

/**
 * Raised when the Production destination warehouse is missing, inactive, or not assigned.
 */
public final class InvalidProductionDestinationWarehouseException extends RuntimeException {

    public static final String NOT_ASSIGNED_MESSAGE =
            "Не назначен склад производства. Укажите его в Настройки склада → Склады.";

    public InvalidProductionDestinationWarehouseException(String message) {
        super(message);
    }

    public static InvalidProductionDestinationWarehouseException notAssigned() {
        return new InvalidProductionDestinationWarehouseException(NOT_ASSIGNED_MESSAGE);
    }

    public static InvalidProductionDestinationWarehouseException warehouseNotFound(
            UUID warehouseId) {
        return new InvalidProductionDestinationWarehouseException(
                "Configured destination warehouse not found: " + warehouseId);
    }

    public static InvalidProductionDestinationWarehouseException warehouseInactive(
            UUID warehouseId) {
        return new InvalidProductionDestinationWarehouseException(
                "Configured destination warehouse is not active: " + warehouseId);
    }
}
