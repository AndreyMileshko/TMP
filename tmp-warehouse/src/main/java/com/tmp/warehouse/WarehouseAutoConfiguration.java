package com.tmp.warehouse;

import com.tmp.warehouse.security.WarehouseCapability;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Registers Warehouse Capability for Security permission synchronization.
 *
 * <p>Does not create users, roles, or Warehouse-owned authorization tables.
 */
@AutoConfiguration
public class WarehouseAutoConfiguration {

    @Bean
    WarehouseCapability warehouseCapability() {
        return new WarehouseCapability();
    }
}
