package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.application.ConfirmMaterialTransferCommand.CellAllocation;
import com.tmp.production.domain.CuttingLinkStatus;
import com.tmp.production.domain.MaterialPlanningSource;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialTransferTemplate;
import com.tmp.production.domain.MaterialTransferTemplateLine;
import com.tmp.production.domain.MaterialTransferTemplateStatus;
import com.tmp.production.domain.ProductionMaterialTransfer;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.persistence.JdbcMaterialTransferTemplateRepository;
import com.tmp.production.persistence.JdbcProductionHistoryRepository;
import com.tmp.production.persistence.JdbcProductionMaterialTransferRepository;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseCommandApi;
import com.tmp.warehouse.api.WarehouseQueryApi;
import com.tmp.warehouse.application.CodeOnlyMaterialReferenceDisplayPort;
import com.tmp.warehouse.application.DefaultWarehouseApi;
import com.tmp.warehouse.application.WarehouseAdjustmentService;
import com.tmp.warehouse.application.WarehouseConsumptionService;
import com.tmp.warehouse.application.WarehouseMoveService;
import com.tmp.warehouse.application.WarehouseOperationEngine;
import com.tmp.warehouse.application.WarehouseReceiptService;
import com.tmp.warehouse.application.WarehouseReservationLinkService;
import com.tmp.warehouse.application.WarehouseTransferService;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import com.tmp.warehouse.persistence.JdbcMaterialReferenceRepository;
import com.tmp.warehouse.persistence.JdbcMaterialReservationLinkRepository;
import com.tmp.warehouse.persistence.JdbcStockPositionRepository;
import com.tmp.warehouse.persistence.JdbcTransferOperationContextRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseCatalogRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseMovementRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseOperationRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseStockRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real PostgreSQL confirmation + shared local transaction proofs for STAGE7-010.
 *
 * <p>Deeper multi-document orchestration remainders may be reinforced in STAGE7-018; this class
 * already proves Production confirmation and Warehouse createTransferDraft join one ambient TX.
 */
@Testcontainers
class ConfirmMaterialTransferPostgresIT {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-21T14:00:00Z"), ZoneOffset.UTC);
    private static final Instant T0 = Instant.parse("2026-08-21T14:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static PlatformTransactionManager txManager;

    private DefaultWarehouseApi warehouseApi;
    private WarehouseCatalogRepository catalog;
    private MaterialReferenceRepository materials;
    private StockPositionRepository stockPositions;
    private JdbcMaterialTransferTemplateRepository templates;
    private JdbcProductionMaterialTransferRepository transfers;
    private ConfirmMaterialTransferService service;
    private WarehouseId mainWarehouseId;
    private WarehouseId productionWarehouseId;
    private StorageCellId mainCellA;
    private StorageCellId mainCellB;
    private StorageCellId productionCell;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUsername(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        dataSource = ds;
        jdbc = new JdbcTemplate(dataSource);
        txManager = new DataSourceTransactionManager(dataSource);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("TRUNCATE TABLE production.production_history");
        jdbc.update("DELETE FROM production.material_transfer_operation_refs");
        jdbc.update("DELETE FROM production.material_transfers");
        jdbc.update("DELETE FROM production.material_transfer_template_line_cutting_refs");
        jdbc.update("DELETE FROM production.material_transfer_template_line_source_items");
        jdbc.update("DELETE FROM production.material_transfer_template_lines");
        jdbc.update("DELETE FROM production.material_transfer_templates");
        jdbc.update("DELETE FROM warehouse.transfer_operation_context");
        jdbc.update("DELETE FROM warehouse.warehouse_movements");
        jdbc.update("DELETE FROM warehouse.warehouse_operations");
        jdbc.update("DELETE FROM warehouse.stock_positions");
        jdbc.update("DELETE FROM warehouse.storage_cells");
        jdbc.update("DELETE FROM warehouse.warehouses");
        jdbc.update("DELETE FROM warehouse.material_references");

        var stockJdbc = new JdbcWarehouseStockRepository(jdbc, CLOCK);
        materials = new JdbcMaterialReferenceRepository(jdbc, CLOCK);
        catalog = new JdbcWarehouseCatalogRepository(jdbc, CLOCK);
        var operations = new JdbcWarehouseOperationRepository(stockJdbc, CLOCK);
        var transferContexts = new JdbcTransferOperationContextRepository(jdbc);
        stockPositions = new JdbcStockPositionRepository(stockJdbc);
        var movements = new JdbcWarehouseMovementRepository(jdbc);
        TransactionTemplate warehouseTx = new TransactionTemplate(txManager);
        WarehouseOperationEngine engine =
                new WarehouseOperationEngine(
                        operations, stockPositions, movements, warehouseTx, CLOCK);
        warehouseApi =
                new DefaultWarehouseApi(
                        authorizationAllowAll(),
                        catalog,
                        stockPositions,
                        materials,
                        new CodeOnlyMaterialReferenceDisplayPort(),
                        new WarehouseReservationLinkService(
                                new JdbcMaterialReservationLinkRepository(jdbc), CLOCK),
                        new WarehouseReceiptService(engine, stockPositions, materials),
                        new WarehouseMoveService(engine),
                        new WarehouseTransferService(
                                engine, operations, transferContexts, warehouseTx),
                        new WarehouseConsumptionService(engine, stockPositions),
                        new WarehouseAdjustmentService(engine, stockPositions),
                        operations,
                        transferContexts);

        templates = new JdbcMaterialTransferTemplateRepository(jdbc, CLOCK, txManager);
        transfers = new JdbcProductionMaterialTransferRepository(jdbc, txManager);
        JdbcProductionHistoryRepository jdbcHistory = new JdbcProductionHistoryRepository(jdbc);
        ProductionHistoryService historyService = new ProductionHistoryService(jdbcHistory, CLOCK);
        service =
                new ConfirmMaterialTransferService(
                        templates,
                        transfers,
                        warehouseApi,
                        warehouseApi,
                        historyService,
                        txManager,
                        CLOCK);

        mainWarehouseId = WarehouseId.generate();
        productionWarehouseId = WarehouseId.generate();
        mainCellA = StorageCellId.generate();
        mainCellB = StorageCellId.generate();
        productionCell = StorageCellId.generate();
        catalog.save(Warehouse.create(mainWarehouseId, "WH-MAIN", "Main"));
        catalog.save(Warehouse.create(productionWarehouseId, "WH-PROD", "Production"));
        catalog.save(StorageCell.create(mainCellA, mainWarehouseId, "M-A"));
        catalog.save(StorageCell.create(mainCellB, mainWarehouseId, "M-B"));
        catalog.save(StorageCell.create(productionCell, productionWarehouseId, "P-01"));
    }

    @Test
    void confirmationCreatesDraftOnlyAndDoesNotChangeStock() {
        MaterialReference material = materialWithAvailable(20L);
        MaterialTransferTemplate template = persistTemplate(material, BigDecimal.TEN);

        ProductionMaterialTransfer result =
                service.confirmMaterialTransferCreate(
                        new ConfirmMaterialTransferCommand(
                                template.templateId(),
                                template.version(),
                                List.of(
                                        new CellAllocation(
                                                template.lines().getFirst().lineId(),
                                                mainCellA.value(),
                                                productionCell.value(),
                                                BigDecimal.TEN))));

        assertEquals(1, result.warehouseOperationRefs().size());
        UUID draftId = result.warehouseOperationRefs().getFirst().warehouseDraftOperationId();
        assertEquals("DRAFT", warehouseApi.getTransferStatus(draftId).status());
        assertEquals(
                0,
                available(mainWarehouseId, mainCellA, material).compareTo(BigDecimal.valueOf(20)));
        assertEquals(0, inTransit(mainWarehouseId, mainCellA, material).compareTo(BigDecimal.ZERO));
        assertEquals(
                MaterialTransferTemplateStatus.CONFIRMED,
                templates.findById(template.templateId()).orElseThrow().status());
    }

    @Test
    void multiCellSplitCreatesTwoWarehouseDraftsAndOneLogicalTransfer() {
        MaterialReference material = materialWithAvailable(20L);
        MaterialTransferTemplate template = persistTemplate(material, BigDecimal.TEN);

        ProductionMaterialTransfer result =
                service.confirmMaterialTransferCreate(
                        new ConfirmMaterialTransferCommand(
                                template.templateId(),
                                template.version(),
                                List.of(
                                        new CellAllocation(
                                                template.lines().getFirst().lineId(),
                                                mainCellA.value(),
                                                productionCell.value(),
                                                BigDecimal.valueOf(6)),
                                        new CellAllocation(
                                                template.lines().getFirst().lineId(),
                                                mainCellB.value(),
                                                productionCell.value(),
                                                BigDecimal.valueOf(4)))));

        assertEquals(1, transfers.findByTemplateId(template.templateId()).stream().count());
        assertEquals(2, result.warehouseOperationRefs().size());
        BigDecimal total =
                result.warehouseOperationRefs().stream()
                        .map(ref -> ref.quantity())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, total.compareTo(BigDecimal.TEN));
    }

    @Test
    void secondWarehouseDraftFailureRollsBackLogicalTransferAndWarehouseDraftA() {
        MaterialReference materialA = materialWithAvailable(20L, "A1");
        MaterialReference materialB = materialWithAvailable(20L, "B1");
        MaterialTransferTemplateLine lineA = lineFor(materialA, BigDecimal.valueOf(5));
        MaterialTransferTemplateLine lineB = lineFor(materialB, BigDecimal.valueOf(5));
        MaterialTransferTemplate template =
                templates.save(
                        MaterialTransferTemplate.create(
                                SourceOrderId.generate(),
                                mainWarehouseId.value(),
                                productionWarehouseId.value(),
                                T0,
                                List.of(lineA, lineB)));

        AtomicInteger drafts = new AtomicInteger();
        WarehouseCommandApi failingCommands =
                new DelegatingCommandApi(warehouseApi) {
                    @Override
                    public WarehouseApi.TransferRequestView createTransferDraft(
                            WarehouseApi.CreateTransferDraftCommand command) {
                        if (drafts.incrementAndGet() == 2) {
                            throw new RuntimeException("controlled second draft failure");
                        }
                        return super.createTransferDraft(command);
                    }
                };
        ConfirmMaterialTransferService failingService =
                new ConfirmMaterialTransferService(
                        templates,
                        transfers,
                        failingCommands,
                        warehouseApi,
                        new ProductionHistoryService(
                                new JdbcProductionHistoryRepository(jdbc), CLOCK),
                        txManager,
                        CLOCK);

        assertThrows(
                RuntimeException.class,
                () ->
                        failingService.confirmMaterialTransferCreate(
                                new ConfirmMaterialTransferCommand(
                                        template.templateId(),
                                        template.version(),
                                        List.of(
                                                new CellAllocation(
                                                        lineA.lineId(),
                                                        mainCellA.value(),
                                                        productionCell.value(),
                                                        BigDecimal.valueOf(5)),
                                                new CellAllocation(
                                                        lineB.lineId(),
                                                        mainCellA.value(),
                                                        productionCell.value(),
                                                        BigDecimal.valueOf(5))))));

        assertTrue(transfers.findByTemplateId(template.templateId()).isEmpty());
        assertEquals(
                MaterialTransferTemplateStatus.DRAFT,
                templates.findById(template.templateId()).orElseThrow().status());
        Integer warehouseOps =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.warehouse_operations", Integer.class);
        assertEquals(0, warehouseOps);
    }

    @Test
    void storedDraftOperationIdIsSendReceiveReferenceViaPublicQuery() {
        MaterialReference material = materialWithAvailable(20L);
        MaterialTransferTemplate template = persistTemplate(material, BigDecimal.valueOf(8));
        ProductionMaterialTransfer transfer =
                service.confirmMaterialTransferCreate(
                        new ConfirmMaterialTransferCommand(
                                template.templateId(),
                                template.version(),
                                List.of(
                                        new CellAllocation(
                                                template.lines().getFirst().lineId(),
                                                mainCellA.value(),
                                                productionCell.value(),
                                                BigDecimal.valueOf(8)))));

        UUID draftOperationId =
                transfer.warehouseOperationRefs().getFirst().warehouseDraftOperationId();
        WarehouseQueryApi query = warehouseApi;
        WarehouseCommandApi command = warehouseApi;

        assertEquals("DRAFT", query.getTransferStatus(draftOperationId).status());
        WarehouseApi.OperationResult sent = command.sendTransfer(draftOperationId);
        assertEquals(draftOperationId, sent.operationId());
        assertEquals("SENT", query.getTransferStatus(draftOperationId).status());
        // STAGE7-011 will call receiveTransfer(sendOperationId); send id == stored draft id.
        command.receiveTransfer(draftOperationId);
        assertEquals("RECEIVED", query.getTransferStatus(draftOperationId).status());
    }

    private MaterialTransferTemplate persistTemplate(
            MaterialReference material, BigDecimal requested) {
        return templates.save(
                MaterialTransferTemplate.create(
                        SourceOrderId.generate(),
                        mainWarehouseId.value(),
                        productionWarehouseId.value(),
                        T0,
                        List.of(lineFor(material, requested))));
    }

    private static MaterialTransferTemplateLine lineFor(
            MaterialReference material, BigDecimal quantity) {
        return MaterialTransferTemplateLine.create(
                MaterialReferenceId.of(material.id().value()),
                material.article(),
                material.name(),
                material.color(),
                material.unitOfMeasure(),
                quantity,
                MaterialPlanningSource.SPECIFICATION,
                null,
                CuttingLinkStatus.NONE,
                List.of(),
                Set.of(SourceOrderItemId.generate()),
                quantity,
                BigDecimal.valueOf(20),
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    private MaterialReference materialWithAvailable(long qty) {
        return materialWithAvailable(qty, "MAT-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private MaterialReference materialWithAvailable(long qty, String article) {
        MaterialReference material =
                materials.create(MaterialReference.create(article, article, "", "", "шт."));
        stockPositions.create(
                StockPosition.of(
                        mainWarehouseId,
                        mainCellA,
                        material,
                        StockState.AVAILABLE,
                        StockQuantity.of(qty)));
        stockPositions.create(
                StockPosition.of(
                        mainWarehouseId,
                        mainCellB,
                        material,
                        StockState.AVAILABLE,
                        StockQuantity.of(qty)));
        return material;
    }

    private BigDecimal available(
            WarehouseId warehouseId, StorageCellId cellId, MaterialReference material) {
        return stockPositions
                .findByNaturalKey(warehouseId, cellId, material, StockState.AVAILABLE)
                .map(pos -> pos.quantity().value())
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal inTransit(
            WarehouseId warehouseId, StorageCellId cellId, MaterialReference material) {
        return stockPositions
                .findByNaturalKey(warehouseId, cellId, material, StockState.IN_TRANSIT)
                .map(pos -> pos.quantity().value())
                .orElse(BigDecimal.ZERO);
    }

    private static AuthorizationService authorizationAllowAll() {
        return new AuthorizationService() {
            @Override
            public boolean hasPermission(PermissionId permissionId) {
                return true;
            }

            @Override
            public void requirePermission(PermissionId permissionId) {}

            @Override
            public java.util.Set<PermissionId> effectivePermissions() {
                return java.util.Set.of();
            }
        };
    }

    private abstract static class DelegatingCommandApi implements WarehouseCommandApi {
        private final WarehouseCommandApi delegate;

        protected DelegatingCommandApi(WarehouseCommandApi delegate) {
            this.delegate = delegate;
        }

        @Override
        public WarehouseApi.WarehouseView createWarehouse(
                WarehouseApi.CreateWarehouseCommand command) {
            return delegate.createWarehouse(command);
        }

        @Override
        public WarehouseApi.StorageCellView createStorageCell(
                WarehouseApi.CreateStorageCellCommand command) {
            return delegate.createStorageCell(command);
        }

        @Override
        public WarehouseApi.ReservationLinkView createReservationLink(
                WarehouseApi.CreateReservationLinkCommand command) {
            return delegate.createReservationLink(command);
        }

        @Override
        public WarehouseApi.OperationResult executeWarehouseOperation(
                WarehouseApi.ExecuteOperationCommand command) {
            return delegate.executeWarehouseOperation(command);
        }

        @Override
        public WarehouseApi.OperationResult receive(WarehouseApi.ReceiptCommand command) {
            return delegate.receive(command);
        }

        @Override
        public WarehouseApi.OperationResult consume(WarehouseApi.ConsumptionCommand command) {
            return delegate.consume(command);
        }

        @Override
        public WarehouseApi.TransferRequestView createTransferDraft(
                WarehouseApi.CreateTransferDraftCommand command) {
            return delegate.createTransferDraft(command);
        }

        @Override
        public WarehouseApi.OperationResult sendTransfer(UUID transferDraftOperationId) {
            return delegate.sendTransfer(transferDraftOperationId);
        }

        @Override
        public WarehouseApi.OperationResult receiveTransfer(UUID sendOperationId) {
            return delegate.receiveTransfer(sendOperationId);
        }
    }
}
