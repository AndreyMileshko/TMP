package com.tmp.production;

import com.tmp.production.security.ProductionCapability;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;

/**
 * Registers Production Capability permission contribution.
 *
 * <p>Does not create users, roles, or Production-owned authorization tables.
 */
@AutoConfiguration
@AutoConfigureAfter(name = {"com.tmp.security.SecurityAutoConfiguration"})
public class ProductionAutoConfiguration {

    @Bean
    ProductionCapability productionCapability() {
        return new ProductionCapability();
    }
}
