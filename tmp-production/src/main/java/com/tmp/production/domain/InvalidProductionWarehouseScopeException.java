package com.tmp.production.domain;

import java.util.UUID;

/**
 * Raised when Production warehouse scope configuration is invalid or references inactive warehouses.
 */
public final class InvalidProductionWarehouseScopeException extends RuntimeException {

    public InvalidProductionWarehouseScopeException(String message) {
        super(message);
    }

    public static InvalidProductionWarehouseScopeException duplicateWarehouseIds() {
        return new InvalidProductionWarehouseScopeException(
                "Main and production warehouse ids must be distinct");
    }

    public static InvalidProductionWarehouseScopeException warehouseNotFound(UUID warehouseId) {
        return new InvalidProductionWarehouseScopeException(
                "Configured warehouse not found: " + warehouseId);
    }

    public static InvalidProductionWarehouseScopeException warehouseInactive(UUID warehouseId) {
        return new InvalidProductionWarehouseScopeException(
                "Configured warehouse is not active: " + warehouseId);
    }
}
