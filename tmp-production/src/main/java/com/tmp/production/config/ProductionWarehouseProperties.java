package com.tmp.production.config;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Explicit runtime configuration for Production warehouse scope.
 *
 * <p>Bound from {@code tmp.production.warehouse.*}. No fake defaults — missing ids fail fast when
 * the scope bean is created.
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

    public boolean isComplete() {
        return mainWarehouseId != null && productionWarehouseId != null;
    }
}
