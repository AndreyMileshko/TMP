package com.tmp.production.application;

import java.util.Objects;
import java.util.UUID;

/**
 * Production-owned configuration for the destination warehouse of Material Requirements.
 *
 * <p>This is not Warehouse domain ownership — it identifies the production warehouse that receives
 * materials. Source/supply warehouse selection is Warehouse-owned (Stage 3.5.10).
 */
public record ProductionDestinationWarehouse(UUID productionWarehouseId) {

    public ProductionDestinationWarehouse {
        Objects.requireNonNull(productionWarehouseId, "productionWarehouseId");
    }
}
