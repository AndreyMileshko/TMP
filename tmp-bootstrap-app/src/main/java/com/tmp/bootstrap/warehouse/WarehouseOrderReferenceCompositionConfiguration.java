package com.tmp.bootstrap.warehouse;

import com.tmp.warehouse.api.TransferDocumentOrderReferenceQuery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Registers composition-boundary Warehouse order-number enrichment (cross-schema JDBC).
 */
@Configuration
public class WarehouseOrderReferenceCompositionConfiguration {

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    TransferDocumentOrderReferenceQuery transferDocumentOrderReferenceQuery(
            JdbcTemplate jdbcTemplate) {
        return new CompositionTransferDocumentOrderReferenceQuery(jdbcTemplate);
    }
}
