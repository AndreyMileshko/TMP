package com.tmp.warehouse;

import com.tmp.document.api.DocumentEngine;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.warehouse.api.MaterialReferenceDisplayPort;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseCommandApi;
import com.tmp.warehouse.api.WarehouseDemandCommandApi;
import com.tmp.warehouse.api.WarehouseQueryApi;
import com.tmp.warehouse.api.WarehouseReferenceQueryApi;
import com.tmp.warehouse.application.CodeOnlyMaterialReferenceDisplayPort;
import com.tmp.warehouse.application.DefaultWarehouseApi;
import com.tmp.warehouse.application.DefaultWarehouseDemandCommandApi;
import com.tmp.warehouse.application.DefaultWarehouseReferenceQueryApi;
import com.tmp.warehouse.application.DefaultWarehouseResponsibilityGuard;
import com.tmp.warehouse.application.WarehouseAdjustmentService;
import com.tmp.warehouse.application.WarehouseConsumptionService;
import com.tmp.warehouse.application.WarehouseInventoryService;
import com.tmp.warehouse.application.WarehouseMoveService;
import com.tmp.warehouse.application.WarehouseOperationEngine;
import com.tmp.warehouse.application.WarehouseReceiptService;
import com.tmp.warehouse.application.WarehouseReservationLinkService;
import com.tmp.warehouse.application.WarehouseResponsibilityGuard;
import com.tmp.warehouse.application.MaterialSourceRoutingService;
import com.tmp.warehouse.application.WarehouseOperationalInboxService;
import com.tmp.warehouse.application.WarehouseTransferDocumentService;
import com.tmp.warehouse.application.WarehouseTransferReceiveService;
import com.tmp.warehouse.application.WarehouseTransferRejectService;
import com.tmp.warehouse.application.WarehouseTransferReturnService;
import com.tmp.warehouse.application.WarehouseTransferSendService;
import com.tmp.warehouse.application.WarehouseTransferService;
import com.tmp.warehouse.application.document.WarehouseTransferDocumentProcessor;
import com.tmp.warehouse.domain.repository.AvailableStockAggregationQuery;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.MaterialReservationLinkRepository;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import com.tmp.warehouse.domain.repository.TransferDocumentSendAllocationRepository;
import com.tmp.warehouse.domain.repository.TransferDocumentSettlementRepository;
import com.tmp.warehouse.domain.repository.TransferOperationContextRepository;
import com.tmp.warehouse.domain.repository.TransferReceiptSettlementItemRepository;
import com.tmp.warehouse.domain.repository.TransferReturnSettlementItemRepository;
import com.tmp.warehouse.domain.repository.TransferTaskStateRepository;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import com.tmp.warehouse.domain.repository.WarehouseMovementRepository;
import com.tmp.warehouse.domain.repository.WarehouseOperationRepository;
import com.tmp.warehouse.domain.repository.WarehouseTransferDocumentRepository;
import com.tmp.warehouse.domain.repository.WarehouseUserResponsibilityRepository;
import com.tmp.warehouse.persistence.JdbcMaterialReferenceRepository;
import com.tmp.warehouse.persistence.JdbcMaterialReservationLinkRepository;
import com.tmp.warehouse.persistence.JdbcAvailableStockAggregationQuery;
import com.tmp.warehouse.persistence.JdbcStockPositionRepository;
import com.tmp.warehouse.persistence.JdbcTransferDocumentSendAllocationRepository;
import com.tmp.warehouse.persistence.JdbcTransferDocumentSettlementRepository;
import com.tmp.warehouse.persistence.JdbcTransferOperationContextRepository;
import com.tmp.warehouse.persistence.JdbcTransferReceiptSettlementItemRepository;
import com.tmp.warehouse.persistence.JdbcTransferReturnSettlementItemRepository;
import com.tmp.warehouse.persistence.JdbcTransferTaskStateRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseCatalogRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseMovementRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseOperationRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseStockRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseTransferDocumentRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseUserResponsibilityRepository;
import com.tmp.warehouse.security.WarehouseCapability;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.util.Objects;
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
 * <p>Does not create users, roles, or Warehouse-owned authorization tables beyond responsibility
 * assignments owned by Warehouse.
 */
@AutoConfiguration
@AutoConfigureAfter(
        name = {
            "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
            "com.tmp.security.SecurityAutoConfiguration",
            "com.tmp.document.DocumentEngineAutoConfiguration"
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
    WarehouseUserResponsibilityRepository warehouseUserResponsibilityRepository(
            JdbcTemplate jdbcTemplate, Clock clock) {
        return new JdbcWarehouseUserResponsibilityRepository(jdbcTemplate, clock);
    }

    @Bean
    WarehouseResponsibilityGuard warehouseResponsibilityGuard(
            AuthenticationService authenticationService,
            WarehouseUserResponsibilityRepository warehouseUserResponsibilityRepository) {
        return new DefaultWarehouseResponsibilityGuard(
                authenticationService, warehouseUserResponsibilityRepository);
    }

    @Bean
    MaterialReferenceRepository materialReferenceRepository(
            JdbcTemplate jdbcTemplate, Clock clock) {
        return new JdbcMaterialReferenceRepository(jdbcTemplate, clock);
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
            StockPositionRepository stockPositionRepository,
            MaterialReferenceRepository materialReferenceRepository) {
        return new WarehouseReceiptService(
                warehouseOperationEngine, stockPositionRepository, materialReferenceRepository);
    }

    @Bean
    WarehouseMoveService warehouseMoveService(WarehouseOperationEngine warehouseOperationEngine) {
        return new WarehouseMoveService(warehouseOperationEngine);
    }

    @Bean
    TransferOperationContextRepository transferOperationContextRepository(
            JdbcTemplate jdbcTemplate) {
        return new JdbcTransferOperationContextRepository(jdbcTemplate);
    }

    @Bean
    WarehouseTransferDocumentRepository warehouseTransferDocumentRepository(
            JdbcTemplate jdbcTemplate, Clock clock) {
        return new JdbcWarehouseTransferDocumentRepository(jdbcTemplate, clock);
    }

    @Bean
    TransferTaskStateRepository transferTaskStateRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        return new JdbcTransferTaskStateRepository(jdbcTemplate, clock);
    }

    @Bean
    TransferDocumentSendAllocationRepository transferDocumentSendAllocationRepository(
            JdbcTemplate jdbcTemplate) {
        return new JdbcTransferDocumentSendAllocationRepository(jdbcTemplate);
    }

    @Bean
    TransferDocumentSettlementRepository transferDocumentSettlementRepository(
            JdbcTemplate jdbcTemplate) {
        return new JdbcTransferDocumentSettlementRepository(jdbcTemplate);
    }

    @Bean
    TransferReceiptSettlementItemRepository transferReceiptSettlementItemRepository(
            JdbcTemplate jdbcTemplate) {
        return new JdbcTransferReceiptSettlementItemRepository(jdbcTemplate);
    }

    @Bean
    TransferReturnSettlementItemRepository transferReturnSettlementItemRepository(
            JdbcTemplate jdbcTemplate) {
        return new JdbcTransferReturnSettlementItemRepository(jdbcTemplate);
    }

    @Bean
    WarehouseTransferDocumentProcessor warehouseTransferDocumentProcessor(
            WarehouseTransferDocumentRepository warehouseTransferDocumentRepository,
            TransferDocumentSendAllocationRepository transferDocumentSendAllocationRepository,
            WarehouseOperationEngine warehouseOperationEngine,
            TransferOperationContextRepository transferOperationContextRepository,
            MaterialReferenceRepository materialReferenceRepository,
            WarehouseCatalogRepository warehouseCatalogRepository,
            TransferDocumentSettlementRepository transferDocumentSettlementRepository,
            TransferReceiptSettlementItemRepository transferReceiptSettlementItemRepository,
            TransferReturnSettlementItemRepository transferReturnSettlementItemRepository,
            Clock clock) {
        return new WarehouseTransferDocumentProcessor(
                warehouseTransferDocumentRepository,
                transferDocumentSendAllocationRepository,
                warehouseOperationEngine,
                transferOperationContextRepository,
                materialReferenceRepository,
                warehouseCatalogRepository,
                transferDocumentSettlementRepository,
                transferReceiptSettlementItemRepository,
                transferReturnSettlementItemRepository,
                clock);
    }

    @Bean
    WarehouseTransferDocumentService warehouseTransferDocumentService(
            DocumentEngine documentEngine,
            WarehouseTransferDocumentRepository warehouseTransferDocumentRepository,
            TransferTaskStateRepository transferTaskStateRepository,
            WarehouseCatalogRepository warehouseCatalogRepository,
            MaterialReferenceRepository materialReferenceRepository,
            WarehouseResponsibilityGuard warehouseResponsibilityGuard,
            PlatformTransactionManager platformTransactionManager) {
        return new WarehouseTransferDocumentService(
                documentEngine,
                warehouseTransferDocumentRepository,
                transferTaskStateRepository,
                warehouseCatalogRepository,
                materialReferenceRepository,
                warehouseResponsibilityGuard,
                new TransactionTemplate(platformTransactionManager));
    }

    @Bean
    WarehouseTransferSendService warehouseTransferSendService(
            DocumentEngine documentEngine,
            WarehouseTransferDocumentRepository warehouseTransferDocumentRepository,
            WarehouseTransferDocumentService warehouseTransferDocumentService,
            TransferDocumentSendAllocationRepository transferDocumentSendAllocationRepository,
            TransferTaskStateRepository transferTaskStateRepository,
            WarehouseCatalogRepository warehouseCatalogRepository,
            WarehouseResponsibilityGuard warehouseResponsibilityGuard,
            PlatformTransactionManager platformTransactionManager,
            Clock clock) {
        return new WarehouseTransferSendService(
                documentEngine,
                warehouseTransferDocumentRepository,
                warehouseTransferDocumentService,
                transferDocumentSendAllocationRepository,
                transferTaskStateRepository,
                warehouseCatalogRepository,
                warehouseResponsibilityGuard,
                new TransactionTemplate(platformTransactionManager),
                clock);
    }

    @Bean
    WarehouseTransferReceiveService warehouseTransferReceiveService(
            DocumentEngine documentEngine,
            WarehouseTransferDocumentRepository warehouseTransferDocumentRepository,
            WarehouseTransferDocumentService warehouseTransferDocumentService,
            TransferDocumentSettlementRepository transferDocumentSettlementRepository,
            TransferDocumentSendAllocationRepository transferDocumentSendAllocationRepository,
            TransferReceiptSettlementItemRepository transferReceiptSettlementItemRepository,
            TransferTaskStateRepository transferTaskStateRepository,
            WarehouseOperationEngine warehouseOperationEngine,
            MaterialReferenceRepository materialReferenceRepository,
            WarehouseCatalogRepository warehouseCatalogRepository,
            WarehouseResponsibilityGuard warehouseResponsibilityGuard,
            PlatformTransactionManager platformTransactionManager,
            Clock clock) {
        return new WarehouseTransferReceiveService(
                documentEngine,
                warehouseTransferDocumentRepository,
                warehouseTransferDocumentService,
                transferDocumentSettlementRepository,
                transferDocumentSendAllocationRepository,
                transferReceiptSettlementItemRepository,
                transferTaskStateRepository,
                warehouseOperationEngine,
                materialReferenceRepository,
                warehouseCatalogRepository,
                warehouseResponsibilityGuard,
                new TransactionTemplate(platformTransactionManager),
                clock);
    }

    @Bean
    WarehouseTransferRejectService warehouseTransferRejectService(
            DocumentEngine documentEngine,
            WarehouseTransferDocumentRepository warehouseTransferDocumentRepository,
            TransferDocumentSettlementRepository transferDocumentSettlementRepository,
            TransferDocumentSendAllocationRepository transferDocumentSendAllocationRepository,
            TransferReceiptSettlementItemRepository transferReceiptSettlementItemRepository,
            TransferTaskStateRepository transferTaskStateRepository,
            WarehouseResponsibilityGuard warehouseResponsibilityGuard,
            AuthenticationService authenticationService,
            PlatformTransactionManager platformTransactionManager,
            Clock clock) {
        return new WarehouseTransferRejectService(
                documentEngine,
                warehouseTransferDocumentRepository,
                transferDocumentSettlementRepository,
                transferDocumentSendAllocationRepository,
                transferReceiptSettlementItemRepository,
                transferTaskStateRepository,
                warehouseResponsibilityGuard,
                authenticationService,
                new TransactionTemplate(platformTransactionManager),
                clock);
    }

    @Bean
    WarehouseTransferReturnService warehouseTransferReturnService(
            DocumentEngine documentEngine,
            WarehouseTransferDocumentRepository warehouseTransferDocumentRepository,
            TransferDocumentSettlementRepository transferDocumentSettlementRepository,
            TransferDocumentSendAllocationRepository transferDocumentSendAllocationRepository,
            TransferReceiptSettlementItemRepository transferReceiptSettlementItemRepository,
            TransferReturnSettlementItemRepository transferReturnSettlementItemRepository,
            TransferTaskStateRepository transferTaskStateRepository,
            WarehouseOperationEngine warehouseOperationEngine,
            MaterialReferenceRepository materialReferenceRepository,
            WarehouseCatalogRepository warehouseCatalogRepository,
            WarehouseResponsibilityGuard warehouseResponsibilityGuard,
            PlatformTransactionManager platformTransactionManager,
            Clock clock) {
        return new WarehouseTransferReturnService(
                documentEngine,
                warehouseTransferDocumentRepository,
                transferDocumentSettlementRepository,
                transferDocumentSendAllocationRepository,
                transferReceiptSettlementItemRepository,
                transferReturnSettlementItemRepository,
                transferTaskStateRepository,
                warehouseOperationEngine,
                materialReferenceRepository,
                warehouseCatalogRepository,
                warehouseResponsibilityGuard,
                new TransactionTemplate(platformTransactionManager),
                clock);
    }

    @Bean
    WarehouseOperationalInboxService warehouseOperationalInboxService(
            DocumentEngine documentEngine,
            WarehouseTransferDocumentRepository warehouseTransferDocumentRepository,
            TransferDocumentSettlementRepository transferDocumentSettlementRepository,
            TransferTaskStateRepository transferTaskStateRepository,
            WarehouseUserResponsibilityRepository warehouseUserResponsibilityRepository,
            WarehouseCatalogRepository warehouseCatalogRepository,
            WarehouseResponsibilityGuard warehouseResponsibilityGuard,
            AuthenticationService authenticationService,
            PlatformTransactionManager platformTransactionManager,
            Clock clock) {
        return new WarehouseOperationalInboxService(
                documentEngine,
                warehouseTransferDocumentRepository,
                transferDocumentSettlementRepository,
                transferTaskStateRepository,
                warehouseUserResponsibilityRepository,
                warehouseCatalogRepository,
                warehouseResponsibilityGuard,
                authenticationService,
                new TransactionTemplate(platformTransactionManager),
                clock);
    }

    @Bean
    WarehouseDocumentProcessorRegistrar warehouseDocumentProcessorRegistrar(
            DocumentEngine documentEngine,
            WarehouseTransferDocumentProcessor warehouseTransferDocumentProcessor) {
        return new WarehouseDocumentProcessorRegistrar(
                documentEngine, warehouseTransferDocumentProcessor);
    }

    @Bean
    WarehouseTransferService warehouseTransferService(
            WarehouseOperationEngine warehouseOperationEngine,
            WarehouseOperationRepository warehouseOperationRepository,
            TransferOperationContextRepository transferOperationContextRepository,
            WarehouseCatalogRepository warehouseCatalogRepository,
            PlatformTransactionManager platformTransactionManager) {
        return new WarehouseTransferService(
                warehouseOperationEngine,
                warehouseOperationRepository,
                transferOperationContextRepository,
                warehouseCatalogRepository,
                new TransactionTemplate(platformTransactionManager));
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
            WarehouseResponsibilityGuard warehouseResponsibilityGuard,
            WarehouseAdjustmentService warehouseAdjustmentService,
            StockPositionRepository stockPositionRepository) {
        return new WarehouseInventoryService(
                authorizationService,
                warehouseResponsibilityGuard,
                warehouseAdjustmentService,
                stockPositionRepository);
    }

    @Bean
    WarehouseReservationLinkService warehouseReservationLinkService(
            MaterialReservationLinkRepository materialReservationLinkRepository, Clock clock) {
        return new WarehouseReservationLinkService(materialReservationLinkRepository, clock);
    }

    @Bean("warehouseMaterialDisplayFallback")
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
    AvailableStockAggregationQuery availableStockAggregationQuery(JdbcTemplate jdbcTemplate) {
        return new JdbcAvailableStockAggregationQuery(jdbcTemplate);
    }

    @Bean
    MaterialSourceRoutingService materialSourceRoutingService(
            AvailableStockAggregationQuery availableStockAggregationQuery) {
        return new MaterialSourceRoutingService(availableStockAggregationQuery);
    }

    @Bean
    WarehouseApi warehouseApi(
            AuthorizationService authorizationService,
            AuthenticationService authenticationService,
            WarehouseResponsibilityGuard warehouseResponsibilityGuard,
            WarehouseUserResponsibilityRepository warehouseUserResponsibilityRepository,
            WarehouseCatalogRepository warehouseCatalogRepository,
            StockPositionRepository stockPositionRepository,
            MaterialReferenceRepository materialReferenceRepository,
            MaterialReferenceDisplayPort materialReferenceDisplayPort,
            WarehouseReservationLinkService warehouseReservationLinkService,
            WarehouseReceiptService warehouseReceiptService,
            WarehouseMoveService warehouseMoveService,
            WarehouseTransferService warehouseTransferService,
            WarehouseTransferDocumentService warehouseTransferDocumentService,
            WarehouseConsumptionService warehouseConsumptionService,
            WarehouseAdjustmentService warehouseAdjustmentService,
            WarehouseOperationRepository warehouseOperationRepository,
            TransferOperationContextRepository transferOperationContextRepository,
            MaterialSourceRoutingService materialSourceRoutingService,
            WarehouseOperationalInboxService warehouseOperationalInboxService,
            WarehouseTransferSendService warehouseTransferSendService,
            WarehouseTransferReceiveService warehouseTransferReceiveService,
            WarehouseTransferRejectService warehouseTransferRejectService,
            WarehouseTransferReturnService warehouseTransferReturnService,
            TransferDocumentSendAllocationRepository transferDocumentSendAllocationRepository,
            TransferDocumentSettlementRepository transferDocumentSettlementRepository,
            TransferReceiptSettlementItemRepository transferReceiptSettlementItemRepository,
            TransferReturnSettlementItemRepository transferReturnSettlementItemRepository) {
        return new DefaultWarehouseApi(
                authorizationService,
                authenticationService,
                warehouseResponsibilityGuard,
                warehouseUserResponsibilityRepository,
                warehouseCatalogRepository,
                stockPositionRepository,
                materialReferenceRepository,
                materialReferenceDisplayPort,
                warehouseReservationLinkService,
                warehouseReceiptService,
                warehouseMoveService,
                warehouseTransferService,
                warehouseTransferDocumentService,
                warehouseConsumptionService,
                warehouseAdjustmentService,
                warehouseOperationRepository,
                transferOperationContextRepository,
                materialSourceRoutingService,
                warehouseOperationalInboxService,
                warehouseTransferSendService,
                warehouseTransferReceiveService,
                warehouseTransferRejectService,
                warehouseTransferReturnService,
                transferDocumentSendAllocationRepository,
                transferDocumentSettlementRepository,
                transferReceiptSettlementItemRepository,
                transferReturnSettlementItemRepository);
    }

    @Bean
    WarehouseDemandCommandApi warehouseDemandCommandApi(
            MaterialSourceRoutingService materialSourceRoutingService,
            WarehouseTransferDocumentService warehouseTransferDocumentService,
            WarehouseCatalogRepository warehouseCatalogRepository,
            MaterialReferenceRepository materialReferenceRepository,
            PlatformTransactionManager platformTransactionManager) {
        return new DefaultWarehouseDemandCommandApi(
                materialSourceRoutingService,
                warehouseTransferDocumentService,
                warehouseCatalogRepository,
                materialReferenceRepository,
                new TransactionTemplate(platformTransactionManager));
    }

    @Bean
    WarehouseQueryApi warehouseQueryApi(WarehouseApi warehouseApi) {
        return warehouseApi;
    }

    @Bean
    WarehouseCommandApi warehouseCommandApi(WarehouseApi warehouseApi) {
        return warehouseApi;
    }

    @Bean
    WarehouseReferenceQueryApi warehouseReferenceQueryApi(
            WarehouseCatalogRepository warehouseCatalogRepository,
            MaterialReferenceRepository materialReferenceRepository) {
        return new DefaultWarehouseReferenceQueryApi(
                warehouseCatalogRepository, materialReferenceRepository);
    }

    @Bean
    WarehouseCapability warehouseCapability() {
        return new WarehouseCapability();
    }

    /** Registers Warehouse document processors on the Document Engine at startup. */
    static final class WarehouseDocumentProcessorRegistrar {

        private final DocumentEngine documentEngine;
        private final WarehouseTransferDocumentProcessor transferProcessor;

        WarehouseDocumentProcessorRegistrar(
                DocumentEngine documentEngine,
                WarehouseTransferDocumentProcessor transferProcessor) {
            this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
            this.transferProcessor =
                    Objects.requireNonNull(transferProcessor, "transferProcessor");
        }

        @PostConstruct
        void register() {
            documentEngine.registerProcessor(transferProcessor);
        }
    }
}
