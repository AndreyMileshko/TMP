package com.tmp.bootstrap.warehouse;

import com.tmp.warehouse.WarehouseAutoConfiguration;
import com.tmp.warehouse.api.TransferDocumentOrderReferenceQuery;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Registers composition-boundary Warehouse order-number enrichment (cross-schema JDBC).
 */
@Configuration
@AutoConfigureAfter(JdbcTemplateAutoConfiguration.class)
@AutoConfigureBefore(WarehouseAutoConfiguration.class)
public class WarehouseOrderReferenceCompositionConfiguration {

    @Bean
    TransferDocumentOrderReferenceQuery transferDocumentOrderReferenceQuery(
            JdbcTemplate jdbcTemplate) {
        return new CompositionTransferDocumentOrderReferenceQuery(jdbcTemplate);
    }
}
