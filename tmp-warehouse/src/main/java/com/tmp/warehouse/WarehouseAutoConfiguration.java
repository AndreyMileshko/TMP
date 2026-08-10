package com.tmp.warehouse;

import com.tmp.security.api.AuthorizationService;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.application.CodeOnlyMaterialReferenceDisplayPort;
import com.tmp.warehouse.application.DefaultWarehouseApi;
import com.tmp.warehouse.application.port.MaterialReferenceDisplayPort;
import com.tmp.warehouse.application.WarehouseAdjustmentService;
import com.tmp.warehouse.application.WarehouseConsumptionService;
import com.tmp.warehouse.application.WarehouseInventoryService;
import com.tmp.warehouse.application.WarehouseMoveService;
import com.tmp.warehouse.application.WarehouseOperationEngine;
import com.tmp.warehouse.application.WarehouseReceiptService;
import com.tmp.warehouse.application.WarehouseReservationLinkService;
import com.tmp.warehouse.application.WarehouseTransferService;
import com.tmp.warehouse.domain.repository.MaterialReservationLinkRepository;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import com.tmp.warehouse.domain.repository.WarehouseMovementRepository;
import com.tmp.warehouse.domain.repository.WarehouseOperationRepository;
import com.tmp.warehouse.persistence.JdbcMaterialReservationLinkRepository;
import com.tmp.warehouse.persistence.JdbcStockPositionRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseCatalogRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseMovementRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseOperationRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseStockRepository;
import com.tmp.warehouse.security.WarehouseCapability;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registers Warehouse Public API, application services, persistence adapters and Capability.
 *
 * <p>Does not create users, roles, or Warehouse-owned authorization tables.
 */
@AutoConfiguration
@AutoConfigureAfter(
        name = {
            "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
            "com.tmp.security.SecurityAutoConfiguration"
        })
public class WarehouseAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock warehouseClock() {
        return Clock.systemUTC();
    }

    @Bean
    JdbcWarehouseStockRepository jdbcWarehouseStockRepository(
            JdbcTemplate jdbcTemplate, Clock clock) {
        return new JdbcWarehouseStockRepository(jdbcTemplate, clock);
    }

    @Bean
    WarehouseCatalogRepository warehouseCatalogRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        return new JdbcWarehouseCatalogRepository(jdbcTemplate, clock);
    }

    @Bean
    StockPositionRepository stockPositionRepository(JdbcWarehouseStockRepository stockRepository) {
        return new JdbcStockPositionRepository(stockRepository);
    }

    @Bean
    WarehouseOperationRepository warehouseOperationRepository(
            JdbcWarehouseStockRepository stockRepository, Clock clock) {
        return new JdbcWarehouseOperationRepository(stockRepository, clock);
    }

    @Bean
    WarehouseMovementRepository warehouseMovementRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcWarehouseMovementRepository(jdbcTemplate);
    }

    @Bean
    MaterialReservationLinkRepository materialReservationLinkRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcMaterialReservationLinkRepository(jdbcTemplate);
    }

    @Bean
    WarehouseOperationEngine warehouseOperationEngine(
            WarehouseOperationRepository warehouseOperationRepository,
            StockPositionRepository stockPositionRepository,
            WarehouseMovementRepository warehouseMovementRepository,
            PlatformTransactionManager platformTransactionManager,
            Clock clock) {
        return new WarehouseOperationEngine(
                warehouseOperationRepository,
                stockPositionRepository,
                warehouseMovementRepository,
                new TransactionTemplate(platformTransactionManager),
                clock);
    }

    @Bean
    WarehouseReceiptService warehouseReceiptService(
            WarehouseOperationEngine warehouseOperationEngine,
            StockPositionRepository stockPositionRepository) {
        return new WarehouseReceiptService(warehouseOperationEngine, stockPositionRepository);
    }

    @Bean
    WarehouseMoveService warehouseMoveService(WarehouseOperationEngine warehouseOperationEngine) {
        return new WarehouseMoveService(warehouseOperationEngine);
    }

    @Bean
    WarehouseTransferService warehouseTransferService(
            WarehouseOperationEngine warehouseOperationEngine) {
        return new WarehouseTransferService(warehouseOperationEngine);
    }

    @Bean
    WarehouseConsumptionService warehouseConsumptionService(
            WarehouseOperationEngine warehouseOperationEngine,
            StockPositionRepository stockPositionRepository) {
        return new WarehouseConsumptionService(warehouseOperationEngine, stockPositionRepository);
    }

    @Bean
    WarehouseAdjustmentService warehouseAdjustmentService(
            WarehouseOperationEngine warehouseOperationEngine,
            StockPositionRepository stockPositionRepository) {
        return new WarehouseAdjustmentService(warehouseOperationEngine, stockPositionRepository);
    }

    @Bean
    WarehouseInventoryService warehouseInventoryService(
            AuthorizationService authorizationService,
            WarehouseAdjustmentService warehouseAdjustmentService,
            StockPositionRepository stockPositionRepository) {
        return new WarehouseInventoryService(
                authorizationService, warehouseAdjustmentService, stockPositionRepository);
    }

    @Bean
    WarehouseReservationLinkService warehouseReservationLinkService(
            MaterialReservationLinkRepository materialReservationLinkRepository, Clock clock) {
        return new WarehouseReservationLinkService(materialReservationLinkRepository, clock);
    }

    @Bean
    CodeOnlyMaterialReferenceDisplayPort codeOnlyMaterialReferenceDisplayPort() {
        return new CodeOnlyMaterialReferenceDisplayPort();
    }

    @Bean
    @ConditionalOnMissingBean(MaterialReferenceDisplayPort.class)
    MaterialReferenceDisplayPort materialReferenceDisplayPort(
            CodeOnlyMaterialReferenceDisplayPort codeOnlyMaterialReferenceDisplayPort) {
        return codeOnlyMaterialReferenceDisplayPort;
    }

    @Bean
    WarehouseApi warehouseApi(
            AuthorizationService authorizationService,
            WarehouseCatalogRepository warehouseCatalogRepository,
            StockPositionRepository stockPositionRepository,
            MaterialReferenceDisplayPort materialReferenceDisplayPort,
            WarehouseReservationLinkService warehouseReservationLinkService,
            WarehouseReceiptService warehouseReceiptService,
            WarehouseMoveService warehouseMoveService,
            WarehouseTransferService warehouseTransferService,
            WarehouseConsumptionService warehouseConsumptionService,
            WarehouseAdjustmentService warehouseAdjustmentService) {
        return new DefaultWarehouseApi(
                authorizationService,
                warehouseCatalogRepository,
                stockPositionRepository,
                materialReferenceDisplayPort,
                warehouseReservationLinkService,
                warehouseReceiptService,
                warehouseMoveService,
                warehouseTransferService,
                warehouseConsumptionService,
                warehouseAdjustmentService);
    }

    @Bean
    WarehouseCapability warehouseCapability() {
        return new WarehouseCapability();
    }
}
