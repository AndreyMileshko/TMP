package com.tmp.production.application;

import java.util.Objects;
import java.util.UUID;

/**
 * Production-owned configuration for which warehouses participate in material availability checks.
 *
 * <p>This is not Warehouse domain ownership — it identifies the two warehouse ids used by
 * Production workflow (main + production).
 */
public record ProductionWarehouseScope(UUID mainWarehouseId, UUID productionWarehouseId) {

    public ProductionWarehouseScope {
        Objects.requireNonNull(mainWarehouseId, "mainWarehouseId");
        Objects.requireNonNull(productionWarehouseId, "productionWarehouseId");
        if (mainWarehouseId.equals(productionWarehouseId)) {
            throw new com.tmp.production.domain.InvalidProductionWarehouseScopeException(
                    "Main and production warehouse ids must be distinct");
        }
    }
}
