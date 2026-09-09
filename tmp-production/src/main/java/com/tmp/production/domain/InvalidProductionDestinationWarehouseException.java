package com.tmp.production.domain;

import java.util.UUID;

/**
 * Raised when the configured Production destination warehouse is missing or inactive.
 */
public final class InvalidProductionDestinationWarehouseException extends RuntimeException {

    public InvalidProductionDestinationWarehouseException(String message) {
        super(message);
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
