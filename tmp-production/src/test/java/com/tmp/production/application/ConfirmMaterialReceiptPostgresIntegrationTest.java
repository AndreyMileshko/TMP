package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.application.MaterialReceiptConfirmationResult.MaterialReceiptConfirmationStatus;
import com.tmp.production.domain.CuttingLinkStatus;
import com.tmp.production.domain.MaterialPlanningSource;
import com.tmp.production.domain.MaterialReceiptConfirmationException;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialTransferTemplate;
import com.tmp.production.domain.MaterialTransferTemplateId;
import com.tmp.production.domain.MaterialTransferTemplateLine;
import com.tmp.production.domain.MaterialTransferTemplateLineId;
import com.tmp.production.domain.ProductionMaterialTransfer;
import com.tmp.production.domain.ProductionMaterialTransferId;
import com.tmp.production.domain.ProductionMaterialTransferNotFoundException;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.WarehouseTransferOperationRef;
import com.tmp.production.persistence.JdbcMaterialTransferTemplateRepository;
import com.tmp.production.persistence.JdbcProductionMaterialTransferRepository;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.CreateTransferDraftCommand;
import com.tmp.warehouse.api.WarehouseApi.TransferRequestView;
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
import java.util.ArrayList;
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
 * Real PostgreSQL proofs for STAGE7-011 receipt confirmation: Warehouse-owned receive, shared
 * REQUIRED transaction rollback, and stock effects only via {@code WarehouseCommandApi}.
 *
 * <p>Uses Warehouse internals only as test fixture construction (same allowance as
 * ConfirmMaterialTransferPostgresIT). STAGE7-018 final boundary tests must use public contracts
 * only.
 */
@Testcontainers
class ConfirmMaterialReceiptPostgresIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-21T15:30:00Z"), ZoneOffset.UTC);
    private static final Instant T0 = Instant.parse("2026-08-21T15:30:00Z");

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
    private ConfirmMaterialReceiptService receiptService;
    private WarehouseId mainWarehouseId;
    private WarehouseId productionWarehouseId;
    private StorageCellId mainCell;
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
        receiptService =
                new ConfirmMaterialReceiptService(
                        transfers, warehouseApi, warehouseApi, txManager, CLOCK);

        mainWarehouseId = WarehouseId.generate();
        productionWarehouseId = WarehouseId.generate();
        mainCell = StorageCellId.generate();
        productionCell = StorageCellId.generate();
        catalog.save(Warehouse.create(mainWarehouseId, "WH-MAIN", "Main"));
        catalog.save(Warehouse.create(productionWarehouseId, "WH-PROD", "Production"));
        catalog.save(StorageCell.create(mainCell, mainWarehouseId, "M-01"));
        catalog.save(StorageCell.create(productionCell, productionWarehouseId, "P-01"));
    }

    @Test
    void notFoundDoesNotTouchWarehouse() {
        assertThrows(
                ProductionMaterialTransferNotFoundException.class,
                () ->
                        receiptService.confirmMaterialReceipt(
                                new ConfirmMaterialReceiptCommand(
                                        ProductionMaterialTransferId.generate())));
        Integer ops =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.warehouse_operations", Integer.class);
        assertEquals(0, ops);
    }

    @Test
    void draftBlocksConfirmationWithoutReceiveOrStockChange() {
        MaterialReference material = materialWithAvailable(20L);
        ProductionMaterialTransfer transfer = createLogicalTransfer(material, List.of(8L), false);

        BigDecimal destBefore = available(productionWarehouseId, productionCell, material);
        BigDecimal inTransitBefore = inTransit(mainWarehouseId, mainCell, material);

        assertThrows(
                MaterialReceiptConfirmationException.class,
                () ->
                        receiptService.confirmMaterialReceipt(
                                new ConfirmMaterialReceiptCommand(transfer.logicalTransferId())));

        assertEquals(
                0,
                available(productionWarehouseId, productionCell, material).compareTo(destBefore));
        assertEquals(
                0, inTransit(mainWarehouseId, mainCell, material).compareTo(inTransitBefore));
        assertEquals(
                "DRAFT",
                warehouseApi
                        .getTransferStatus(
                                transfer.warehouseOperationRefs()
                                        .getFirst()
                                        .warehouseDraftOperationId())
                        .status());
    }

    @Test
    void sentConfirmReceiptMovesStockToProductionAvailable() {
        MaterialReference material = materialWithAvailable(20L);
        ProductionMaterialTransfer transfer = createLogicalTransfer(material, List.of(8L), true);
        UUID sendId =
                transfer.warehouseOperationRefs().getFirst().warehouseDraftOperationId();

        assertEquals(0, available(mainWarehouseId, mainCell, material).compareTo(BigDecimal.valueOf(12)));
        assertEquals(0, inTransit(mainWarehouseId, mainCell, material).compareTo(BigDecimal.valueOf(8)));
        assertEquals(0, available(productionWarehouseId, productionCell, material).compareTo(BigDecimal.ZERO));

        MaterialReceiptConfirmationResult result =
                receiptService.confirmMaterialReceipt(
                        new ConfirmMaterialReceiptCommand(transfer.logicalTransferId()));

        assertEquals(MaterialReceiptConfirmationStatus.RECEIVED, result.status());
        assertEquals("RECEIVED", warehouseApi.getTransferStatus(sendId).status());
        assertEquals(0, inTransit(mainWarehouseId, mainCell, material).compareTo(BigDecimal.ZERO));
        assertEquals(
                0,
                available(productionWarehouseId, productionCell, material)
                        .compareTo(BigDecimal.valueOf(8)));
        assertEquals(sendId, result.references().getFirst().warehouseSendOperationId());
        assertEquals(
                warehouseApi.getTransferStatus(sendId).receiveOperationId(),
                result.references().getFirst().receiveOperationId());
    }

    @Test
    void allReceivedIdempotentDoesNotChangeStock() {
        MaterialReference material = materialWithAvailable(20L);
        ProductionMaterialTransfer transfer = createLogicalTransfer(material, List.of(8L), true);
        receiptService.confirmMaterialReceipt(
                new ConfirmMaterialReceiptCommand(transfer.logicalTransferId()));
        BigDecimal destAfterFirst = available(productionWarehouseId, productionCell, material);

        MaterialReceiptConfirmationResult second =
                receiptService.confirmMaterialReceipt(
                        new ConfirmMaterialReceiptCommand(transfer.logicalTransferId()));

        assertEquals(MaterialReceiptConfirmationStatus.ALREADY_RECEIVED, second.status());
        assertEquals(
                0,
                available(productionWarehouseId, productionCell, material)
                        .compareTo(destAfterFirst));
    }

    @Test
    void mixedSentAndReceivedReceivesOnlyRemainingSent() {
        MaterialReference material = materialWithAvailable(30L);
        ProductionMaterialTransfer transfer =
                createLogicalTransfer(material, List.of(5L, 7L), true);
        UUID firstSend =
                transfer.warehouseOperationRefs().getFirst().warehouseDraftOperationId();
        UUID secondSend =
                transfer.warehouseOperationRefs().get(1).warehouseDraftOperationId();
        warehouseApi.receiveTransfer(firstSend);

        MaterialReceiptConfirmationResult result =
                receiptService.confirmMaterialReceipt(
                        new ConfirmMaterialReceiptCommand(transfer.logicalTransferId()));

        assertEquals(MaterialReceiptConfirmationStatus.RECEIVED, result.status());
        assertEquals("RECEIVED", warehouseApi.getTransferStatus(firstSend).status());
        assertEquals("RECEIVED", warehouseApi.getTransferStatus(secondSend).status());
        assertEquals(
                0,
                available(productionWarehouseId, productionCell, material)
                        .compareTo(BigDecimal.valueOf(12)));
    }

    @Test
    void multiRefConfirmReceivesAll() {
        MaterialReference material = materialWithAvailable(40L);
        ProductionMaterialTransfer transfer =
                createLogicalTransfer(material, List.of(3L, 4L, 5L), true);

        MaterialReceiptConfirmationResult result =
                receiptService.confirmMaterialReceipt(
                        new ConfirmMaterialReceiptCommand(transfer.logicalTransferId()));

        assertEquals(3, result.references().size());
        for (WarehouseTransferOperationRef ref : transfer.warehouseOperationRefs()) {
            assertEquals(
                    "RECEIVED",
                    warehouseApi.getTransferStatus(ref.warehouseDraftOperationId()).status());
        }
        assertEquals(
                0,
                available(productionWarehouseId, productionCell, material)
                        .compareTo(BigDecimal.valueOf(12)));
    }

    @Test
    void prevalidationWithDraftRejectsBeforeAnyReceive() {
        MaterialReference material = materialWithAvailable(40L);
        MaterialTransferTemplateLine line = lineFor(material, BigDecimal.valueOf(15));
        TransferRequestView draftA = draftAndSend(material, 5);
        TransferRequestView draftB = createDraftOnly(material, 5);
        TransferRequestView draftC = draftAndSend(material, 5);
        ProductionMaterialTransfer transfer =
                persistLogical(
                        List.of(line),
                        List.of(
                                toRef(draftA, line.lineId()),
                                toRef(draftB, line.lineId()),
                                toRef(draftC, line.lineId())));

        BigDecimal destBefore = available(productionWarehouseId, productionCell, material);

        assertThrows(
                MaterialReceiptConfirmationException.class,
                () ->
                        receiptService.confirmMaterialReceipt(
                                new ConfirmMaterialReceiptCommand(transfer.logicalTransferId())));

        assertEquals("SENT", warehouseApi.getTransferStatus(draftA.operationId()).status());
        assertEquals("DRAFT", warehouseApi.getTransferStatus(draftB.operationId()).status());
        assertEquals("SENT", warehouseApi.getTransferStatus(draftC.operationId()).status());
        assertEquals(
                0,
                available(productionWarehouseId, productionCell, material).compareTo(destBefore));
    }

    @Test
    void partialReceiveFailureRollsBackNewReceives() {
        MaterialReference material = materialWithAvailable(30L);
        ProductionMaterialTransfer transfer =
                createLogicalTransfer(material, List.of(5L, 7L), true);
        UUID sendA = transfer.warehouseOperationRefs().getFirst().warehouseDraftOperationId();
        UUID sendB = transfer.warehouseOperationRefs().get(1).warehouseDraftOperationId();

        AtomicInteger receives = new AtomicInteger();
        ConfirmMaterialReceiptService failing =
                new ConfirmMaterialReceiptService(
                        transfers,
                        new DelegatingCommandApi(warehouseApi) {
                            @Override
                            public WarehouseApi.OperationResult receiveTransfer(UUID sendOperationId) {
                                if (receives.incrementAndGet() == 2) {
                                    throw new RuntimeException("controlled second receive failure");
                                }
                                return super.receiveTransfer(sendOperationId);
                            }
                        },
                        warehouseApi,
                        txManager,
                        CLOCK);

        assertThrows(
                RuntimeException.class,
                () ->
                        failing.confirmMaterialReceipt(
                                new ConfirmMaterialReceiptCommand(transfer.logicalTransferId())));

        assertEquals("SENT", warehouseApi.getTransferStatus(sendA).status());
        assertEquals("SENT", warehouseApi.getTransferStatus(sendB).status());
        assertEquals(
                0,
                available(productionWarehouseId, productionCell, material)
                        .compareTo(BigDecimal.ZERO));
        assertEquals(
                0,
                inTransit(mainWarehouseId, mainCell, material)
                        .compareTo(BigDecimal.valueOf(12)));
        assertTrue(transfers.findById(transfer.logicalTransferId()).isPresent());
    }

    @Test
    void preExistingReceivedSurvivesRollbackOfNewReceives() {
        MaterialReference material = materialWithAvailable(40L);
        ProductionMaterialTransfer transfer =
                createLogicalTransfer(material, List.of(5L, 6L, 7L), true);
        UUID sendA = transfer.warehouseOperationRefs().get(0).warehouseDraftOperationId();
        UUID sendB = transfer.warehouseOperationRefs().get(1).warehouseDraftOperationId();
        UUID sendC = transfer.warehouseOperationRefs().get(2).warehouseDraftOperationId();
        warehouseApi.receiveTransfer(sendA);

        AtomicInteger receives = new AtomicInteger();
        ConfirmMaterialReceiptService failing =
                new ConfirmMaterialReceiptService(
                        transfers,
                        new DelegatingCommandApi(warehouseApi) {
                            @Override
                            public WarehouseApi.OperationResult receiveTransfer(UUID sendOperationId) {
                                int n = receives.incrementAndGet();
                                if (n == 2) {
                                    throw new RuntimeException("controlled receive C failure");
                                }
                                return super.receiveTransfer(sendOperationId);
                            }
                        },
                        warehouseApi,
                        txManager,
                        CLOCK);

        assertThrows(
                RuntimeException.class,
                () ->
                        failing.confirmMaterialReceipt(
                                new ConfirmMaterialReceiptCommand(transfer.logicalTransferId())));

        assertEquals("RECEIVED", warehouseApi.getTransferStatus(sendA).status());
        assertEquals("SENT", warehouseApi.getTransferStatus(sendB).status());
        assertEquals("SENT", warehouseApi.getTransferStatus(sendC).status());
        assertEquals(
                0,
                available(productionWarehouseId, productionCell, material)
                        .compareTo(BigDecimal.valueOf(5)));
    }

    @Test
    void consistencyMismatchFailsBeforeMutation() {
        MaterialReference material = materialWithAvailable(20L);
        ProductionMaterialTransfer transfer = createLogicalTransfer(material, List.of(8L), true);
        WarehouseTransferOperationRef original = transfer.warehouseOperationRefs().getFirst();
        WarehouseTransferOperationRef mismatched =
                new WarehouseTransferOperationRef(
                        original.templateLineId(),
                        original.warehouseDraftOperationId(),
                        MaterialReferenceId.generate(),
                        original.quantity(),
                        original.sourceStorageCellId(),
                        original.destinationStorageCellId());
        ProductionMaterialTransfer corrupted =
                ProductionMaterialTransfer.rehydrate(
                        transfer.logicalTransferId(),
                        transfer.templateId(),
                        transfer.sourceOrderId(),
                        transfer.createdAt(),
                        List.of(mismatched));
        // Overwrite refs via delete+insert would be heavy; use in-TX path with custom repository
        // wrapper that returns corrupted transfer.
        ConfirmMaterialReceiptService mismatchService =
                new ConfirmMaterialReceiptService(
                        new FixedTransferRepository(corrupted),
                        warehouseApi,
                        warehouseApi,
                        txManager,
                        CLOCK);

        BigDecimal destBefore = available(productionWarehouseId, productionCell, material);
        assertThrows(
                MaterialReceiptConfirmationException.class,
                () ->
                        mismatchService.confirmMaterialReceipt(
                                new ConfirmMaterialReceiptCommand(transfer.logicalTransferId())));
        assertEquals("SENT", warehouseApi.getTransferStatus(original.warehouseDraftOperationId()).status());
        assertEquals(
                0,
                available(productionWarehouseId, productionCell, material).compareTo(destBefore));
    }

    private ProductionMaterialTransfer createLogicalTransfer(
            MaterialReference material, List<Long> quantities, boolean send) {
        BigDecimal total =
                quantities.stream()
                        .map(BigDecimal::valueOf)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        MaterialTransferTemplateLine line = lineFor(material, total);
        List<WarehouseTransferOperationRef> refs = new ArrayList<>();
        for (Long qty : quantities) {
            TransferRequestView draft = createDraftOnly(material, qty);
            if (send) {
                warehouseApi.sendTransfer(draft.operationId());
            }
            refs.add(toRef(draft, line.lineId()));
        }
        return persistLogical(List.of(line), refs);
    }

    private TransferRequestView createDraftOnly(MaterialReference material, long qty) {
        return warehouseApi.createTransferDraft(
                new CreateTransferDraftCommand(
                        material.id().value(),
                        BigDecimal.valueOf(qty),
                        mainWarehouseId.value(),
                        mainCell.value(),
                        productionWarehouseId.value(),
                        productionCell.value()));
    }

    private TransferRequestView draftAndSend(MaterialReference material, long qty) {
        TransferRequestView draft = createDraftOnly(material, qty);
        warehouseApi.sendTransfer(draft.operationId());
        return draft;
    }

    private WarehouseTransferOperationRef toRef(
            TransferRequestView draft, MaterialTransferTemplateLineId lineId) {
        return new WarehouseTransferOperationRef(
                lineId,
                draft.operationId(),
                MaterialReferenceId.of(draft.materialReferenceId()),
                draft.quantity(),
                draft.sourceStorageCellId(),
                draft.destinationStorageCellId());
    }

    private ProductionMaterialTransfer persistLogical(
            List<MaterialTransferTemplateLine> lines, List<WarehouseTransferOperationRef> refs) {
        MaterialTransferTemplate template =
                templates.save(
                        MaterialTransferTemplate.create(
                                        SourceOrderId.generate(),
                                        mainWarehouseId.value(),
                                        productionWarehouseId.value(),
                                        T0,
                                        lines)
                                .confirm(T0));
        return transfers.save(
                ProductionMaterialTransfer.create(
                        template.templateId(), template.sourceOrderId(), T0, refs));
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
        MaterialReference material =
                materials.create(
                        MaterialReference.create(
                                "MAT-" + UUID.randomUUID().toString().substring(0, 8),
                                "Material",
                                "",
                                "",
                                "шт."));
        stockPositions.create(
                StockPosition.of(
                        mainWarehouseId,
                        mainCell,
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

    private static final class FixedTransferRepository
            implements com.tmp.production.domain.repository.ProductionMaterialTransferRepository {
        private final ProductionMaterialTransfer transfer;

        private FixedTransferRepository(ProductionMaterialTransfer transfer) {
            this.transfer = transfer;
        }

        @Override
        public ProductionMaterialTransfer save(ProductionMaterialTransfer value) {
            return value;
        }

        @Override
        public java.util.Optional<ProductionMaterialTransfer> findById(
                ProductionMaterialTransferId logicalTransferId) {
            return java.util.Optional.of(transfer);
        }

        @Override
        public java.util.Optional<ProductionMaterialTransfer> findByTemplateId(
                MaterialTransferTemplateId templateId) {
            return java.util.Optional.empty();
        }

        @Override
        public List<ProductionMaterialTransfer> findBySourceOrderId(SourceOrderId sourceOrderId) {
            return List.of();
        }
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
