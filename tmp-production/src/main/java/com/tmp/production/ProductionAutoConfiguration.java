package com.tmp.production;

import com.tmp.order.api.OrderQueryService;
import com.tmp.production.application.CurrentMaterialAvailabilityQueryService;
import com.tmp.production.application.DefaultProductionQueryApi;
import com.tmp.production.application.ProductionOrderViewService;
import com.tmp.production.application.ProductionFoundationQueryService;
import com.tmp.production.application.ProductionWarehouseScope;
import com.tmp.production.application.ProductionHistoryService;
import com.tmp.production.application.port.DefaultOrderSpecificationQueryAdapter;
import com.tmp.production.application.port.DefaultWarehouseAvailabilityQueryAdapter;
import com.tmp.production.security.ProductionCapability;
import com.tmp.production.domain.repository.ProductionCancellationQuery;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import com.tmp.production.domain.repository.ProductionHistoryRepository;
import com.tmp.production.persistence.JdbcProductionCancellationRepository;
import com.tmp.production.persistence.JdbcProductionHistoryRepository;
import com.tmp.production.persistence.JdbcProductionItemStateRepository;
import com.tmp.production.application.port.OrderSpecificationQueryPort;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import com.tmp.security.api.AuthorizationService;
import com.tmp.warehouse.api.WarehouseQueryApi;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import com.tmp.production.api.ProductionQueryApi;

/**
 * Registers Production Capability permission contribution.
 *
 * <p>Does not create users, roles, or Production-owned authorization tables.
 */
@AutoConfiguration
@AutoConfigureAfter(
        name = {
            "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
            "com.tmp.security.SecurityAutoConfiguration",
            "com.tmp.order.OrderManagementAutoConfiguration",
            "com.tmp.warehouse.WarehouseAutoConfiguration"
        })
public class ProductionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    ProductionWarehouseScope productionWarehouseScope() {
        // Stage 7 wiring uses distinct warehouse ids; in tests these are overridden by mocks/fixtures.
        UUID main = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID production = UUID.fromString("00000000-0000-0000-0000-000000000002");
        return new ProductionWarehouseScope(main, production);
    }

    @Bean
    ProductionItemStateRepository productionItemStateRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        return new JdbcProductionItemStateRepository(jdbcTemplate, clock);
    }

    @Bean
    ProductionCancellationQuery productionCancellationQuery(JdbcTemplate jdbcTemplate, Clock clock) {
        return new JdbcProductionCancellationRepository(jdbcTemplate, clock);
    }

    @Bean
    ProductionHistoryRepository productionHistoryRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcProductionHistoryRepository(jdbcTemplate);
    }

    @Bean
    ProductionOrderViewService productionOrderViewService(
            ProductionItemStateRepository itemStateRepository,
            ProductionCancellationQuery cancellationQuery) {
        return new ProductionOrderViewService(itemStateRepository, cancellationQuery);
    }

    @Bean
    ProductionHistoryService productionHistoryService(ProductionHistoryRepository repository, Clock clock) {
        return new ProductionHistoryService(repository, clock);
    }

    @Bean
    OrderSpecificationQueryPort orderSpecificationQueryPort(OrderQueryService orderQueryService) {
        return new DefaultOrderSpecificationQueryAdapter(orderQueryService);
    }

    @Bean
    ProductionFoundationQueryService productionFoundationQueryService(
            OrderSpecificationQueryPort specificationQueryPort) {
        return new ProductionFoundationQueryService(specificationQueryPort);
    }

    @Bean
    WarehouseAvailabilityQueryPort warehouseAvailabilityQueryPort(WarehouseQueryApi warehouseQueryApi) {
        return new DefaultWarehouseAvailabilityQueryAdapter(warehouseQueryApi);
    }

    @Bean
    CurrentMaterialAvailabilityQueryService currentMaterialAvailabilityQueryService(
            ProductionOrderViewService orderViewService,
            com.tmp.production.application.ProductionFoundationQueryService foundationQueryService,
            WarehouseAvailabilityQueryPort warehouseQueryPort,
            ProductionWarehouseScope warehouseScope,
            Clock clock) {
        return new CurrentMaterialAvailabilityQueryService(
                orderViewService, foundationQueryService, warehouseQueryPort, warehouseScope, clock);
    }

    @Bean
    ProductionQueryApi productionQueryApi(
            AuthorizationService authorizationService,
            ProductionOrderViewService orderViewService,
            CurrentMaterialAvailabilityQueryService materialAvailabilityQueryService,
            ProductionHistoryService historyService) {
        return new DefaultProductionQueryApi(
                authorizationService, orderViewService, materialAvailabilityQueryService, historyService);
    }

    @Bean
    ProductionCapability productionCapability(ProductionQueryApi productionQueryApi) {
        return new ProductionCapability(productionQueryApi);
    }
}
