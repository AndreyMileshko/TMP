package com.tmp.production.config;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Explicit runtime configuration for the Production destination warehouse.
 *
 * <p>Bound from {@code tmp.production.warehouse.*}. Only {@code productionWarehouseId} is required.
 * {@code mainWarehouseId} remains optional for YAML compatibility and is unused by active runtime.
 */
@ConfigurationProperties(prefix = "tmp.production.warehouse")
public class ProductionWarehouseProperties {

    private UUID mainWarehouseId;
    private UUID productionWarehouseId;

    public UUID getMainWarehouseId() {
        return mainWarehouseId;
    }

    public void setMainWarehouseId(UUID mainWarehouseId) {
        this.mainWarehouseId = mainWarehouseId;
    }

    public UUID getProductionWarehouseId() {
        return productionWarehouseId;
    }

    public void setProductionWarehouseId(UUID productionWarehouseId) {
        this.productionWarehouseId = productionWarehouseId;
    }

    /** Destination warehouse configured for Material Requirement / availability / release. */
    public boolean isComplete() {
        return productionWarehouseId != null;
    }
}
