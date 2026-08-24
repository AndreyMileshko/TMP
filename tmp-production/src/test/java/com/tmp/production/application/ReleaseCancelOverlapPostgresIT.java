package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.DefaultDocumentEngine;
import com.tmp.document.DefaultDocumentProcessorRegistry;
import com.tmp.document.TransactionAfterCommitEventPublisher;
import com.tmp.production.application.ReleaseProductsCommand.CellAllocation;
import com.tmp.production.application.ReleaseProductsCommand.ItemRelease;
import com.tmp.production.application.ReleaseProductsCommand.MaterialActualUsage;
import com.tmp.production.application.document.ProductionCancellationProcessor;
import com.tmp.production.application.document.ProductionReleaseProcessor;
import com.tmp.production.application.internal.ProductionCancellationDocumentService;
import com.tmp.production.application.internal.ProductionReleaseDocumentService;
import com.tmp.production.application.port.OrderSpecificationQueryPort;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedSpecification;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.MaterialReferenceEntry;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.WarehouseCatalogEntry;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.ReleaseProductsException;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import com.tmp.production.persistence.JdbcProductionCancellationRepository;
import com.tmp.production.persistence.JdbcProductionHistoryRepository;
import com.tmp.production.persistence.JdbcProductionItemStateRepository;
import com.tmp.production.persistence.JdbcProductionReleaseRepository;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.warehouse.api.WarehouseApi;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * True overlapping Release ↔ Cancellation row-lock serialization proof (STAGE7-014 verification
 * correction).
 */
@Testcontainers
class ReleaseCancelOverlapPostgresIT {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-23T14:00:00Z"), ZoneOffset.UTC);
    private static final Instant T0 = Instant.parse("2026-08-23T14:00:00Z");

    private static final UUID MAIN = UUID.fromString("00000000-0000-4000-8000-000000000101");
    private static final UUID PROD = UUID.fromString("00000000-0000-4000-8000-000000000102");
    private static final UUID PROD_CELL = UUID.fromString("00000000-0000-4000-8000-000000000203");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static PlatformTransactionManager txManager;

    private JdbcProductionItemStateRepository delegateItemRepository;
    private LockCoordinatingItemStateRepository itemRepository;
    private MaterialReferenceRepository materials;
    private StockPositionRepository stockPositions;
    private WarehouseCatalogRepository catalog;
    private DefaultWarehouseApi warehouseApi;
    private TrackingSpecificationQuery specificationQuery;
    private WarehouseAvailabilityQueryPort warehouseAvailabilityQuery;
    private WarehouseId productionWarehouseId;
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
        jdbc.update("DELETE FROM documents.document_lifecycle_journal");
        jdbc.update("DELETE FROM documents.document_versions");
        jdbc.update("DELETE FROM documents.documents");
        jdbc.update("DELETE FROM documents.document_types");
        jdbc.update("TRUNCATE TABLE production.production_history");
        jdbc.update("DELETE FROM production.production_cancellation_item_lines");
        jdbc.update("DELETE FROM production.production_cancellations");
        jdbc.update("DELETE FROM production.production_release_material_lines");
        jdbc.update("DELETE FROM production.production_release_item_lines");
        jdbc.update("DELETE FROM production.production_releases");
        jdbc.update("DELETE FROM production.production_item_cutting_plan_links");
        jdbc.update("DELETE FROM production.production_item_states");
        jdbc.update("DELETE FROM warehouse.transfer_operation_context");
        jdbc.update("DELETE FROM warehouse.warehouse_movements");
        jdbc.update("DELETE FROM warehouse.warehouse_operations");
        jdbc.update("DELETE FROM warehouse.stock_positions");
        jdbc.update("DELETE FROM warehouse.storage_cells");
        jdbc.update("DELETE FROM warehouse.warehouses");
        jdbc.update("DELETE FROM warehouse.material_references");

        delegateItemRepository = new JdbcProductionItemStateRepository(jdbc, CLOCK);
        itemRepository = new LockCoordinatingItemStateRepository(delegateItemRepository);

        var stockJdbc = new JdbcWarehouseStockRepository(jdbc, CLOCK);
        materials = new JdbcMaterialReferenceRepository(jdbc, CLOCK);
        catalog = new JdbcWarehouseCatalogRepository(jdbc, CLOCK);
        var operations = new JdbcWarehouseOperationRepository(stockJdbc, CLOCK);
        var transferContexts = new JdbcTransferOperationContextRepository(jdbc);
        stockPositions = new JdbcStockPositionRepository(stockJdbc);
        var movements = new JdbcWarehouseMovementRepository(jdbc);
        var warehouseTx = new org.springframework.transaction.support.TransactionTemplate(txManager);
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

        specificationQuery = new TrackingSpecificationQuery();
        warehouseAvailabilityQuery = new WarehouseApiAvailabilityQuery(warehouseApi);
        productionWarehouseId = WarehouseId.of(PROD);
        productionCell = StorageCellId.of(PROD_CELL);
        catalog.save(Warehouse.create(WarehouseId.of(MAIN), "WH-MAIN", "Main"));
        catalog.save(Warehouse.create(productionWarehouseId, "WH-PROD", "Production"));
        catalog.save(StorageCell.create(productionCell, productionWarehouseId, "P-01"));
    }

    @Test
    void cancellationHoldsLockWhileConcurrentReleaseBlocksThenRejects() throws Exception {
        Scenario scenario = prepareInProductionScenario(bd(50));
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseStarted = new CountDownLatch(1);
        CountDownLatch proceedCancellation = new CountDownLatch(1);
        AtomicBoolean releaseFinished = new AtomicBoolean(false);
        itemRepository.onForUpdateAcquired(
                () -> {
                    lockHeld.countDown();
                    awaitQuietly(releaseStarted);
                    awaitQuietly(proceedCancellation);
                });

        CancelOrderProductionService cancelService = cancelService();
        ReleaseProductsService releaseService = releaseService();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Future<?> cancelFuture =
                executor.submit(
                        () -> {
                            try {
                                cancelService.cancelOrderProduction(
                                        new CancelOrderProductionCommand(
                                                scenario.orderId().value()));
                            } catch (Throwable ex) {
                                failure.compareAndSet(null, ex);
                            }
                        });
        assertTrue(lockHeld.await(30, TimeUnit.SECONDS), "Cancellation must acquire FOR UPDATE lock");

        Future<?> releaseFuture =
                executor.submit(
                        () -> {
                            releaseStarted.countDown();
                            try {
                                releaseService.releaseProducts(
                                        releaseCommand(scenario, 3, bd("5.100000")));
                                failure.compareAndSet(
                                        null,
                                        new AssertionError(
                                                "Release must be rejected after cancellation"));
                            } catch (ReleaseProductsException ex) {
                                // expected after cancel commits
                            } catch (Throwable ex) {
                                failure.compareAndSet(null, ex);
                            } finally {
                                releaseFinished.set(true);
                            }
                        });

        Thread.sleep(500);
        assertFalse(releaseFinished.get(), "Release must block while cancellation holds lock");
        proceedCancellation.countDown();

        cancelFuture.get(30, TimeUnit.SECONDS);
        releaseFuture.get(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        if (failure.get() != null) {
            throw new AssertionError("Overlapping cancel/release failed", failure.get());
        }

        assertEquals(1, countPostedCancellations());
        assertEquals(0, countPostedReleases());
        assertEquals(0, countConsumptionOperations());
        assertEquals(
                ProductionStatus.CANCELLED,
                itemRepository
                        .findByIdentity(
                                scenario.orderId(), scenario.itemId(), scenario.specId())
                        .orElseThrow()
                        .status());
    }

    @Test
    void partialReleaseHoldsLockWhileConcurrentCancellationBlocksThenPreservesReleased()
            throws Exception {
        Scenario scenario = prepareInProductionScenario(bd(50));
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch cancelStarted = new CountDownLatch(1);
        CountDownLatch proceedRelease = new CountDownLatch(1);
        AtomicBoolean cancelFinished = new AtomicBoolean(false);
        itemRepository.onForUpdateAcquired(
                () -> {
                    lockHeld.countDown();
                    awaitQuietly(cancelStarted);
                    awaitQuietly(proceedRelease);
                });

        ReleaseProductsService releaseService = releaseService();
        CancelOrderProductionService cancelService = cancelService();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Future<?> releaseFuture =
                executor.submit(
                        () -> {
                            try {
                                releaseService.releaseProducts(
                                        releaseCommand(scenario, 3, bd("5.100000")));
                            } catch (Throwable ex) {
                                failure.compareAndSet(null, ex);
                            }
                        });
        assertTrue(lockHeld.await(30, TimeUnit.SECONDS), "Release must acquire FOR UPDATE lock");

        Future<?> cancelFuture =
                executor.submit(
                        () -> {
                            cancelStarted.countDown();
                            try {
                                cancelService.cancelOrderProduction(
                                        new CancelOrderProductionCommand(
                                                scenario.orderId().value()));
                            } catch (Throwable ex) {
                                failure.compareAndSet(null, ex);
                            } finally {
                                cancelFinished.set(true);
                            }
                        });

        Thread.sleep(500);
        assertFalse(cancelFinished.get(), "Cancellation must block while release holds lock");
        proceedRelease.countDown();

        releaseFuture.get(30, TimeUnit.SECONDS);
        cancelFuture.get(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        if (failure.get() != null) {
            throw new AssertionError("Overlapping partial release/cancel failed", failure.get());
        }

        ProductionItemState state =
                itemRepository
                        .findByIdentity(
                                scenario.orderId(), scenario.itemId(), scenario.specId())
                        .orElseThrow();
        assertEquals(ProductionStatus.CANCELLED, state.status());
        assertEquals(3L, state.releasedQuantity().value().longValueExact());
        assertEquals(0L, state.activeProductionQuantity().value().longValueExact());
        assertEquals(1, countPostedReleases());
        assertEquals(1, countConsumptionOperations());
        assertEquals(1, countPostedCancellations());
    }

    private CancelOrderProductionService cancelService() {
        var cancellationRepository = new JdbcProductionCancellationRepository(jdbc, CLOCK);
        JdbcProductionHistoryRepository jdbcHistory = new JdbcProductionHistoryRepository(jdbc);
        ProductionHistoryService historyService = new ProductionHistoryService(jdbcHistory, CLOCK);
        var documentEngine =
                documentEngine(
                        new ProductionReleaseProcessor(
                                new JdbcProductionReleaseRepository(jdbc, CLOCK),
                                itemRepository,
                                new TransactionAfterCommitEventPublisher(),
                                historyService),
                        new ProductionCancellationProcessor(
                                cancellationRepository,
                                itemRepository,
                                new TransactionAfterCommitEventPublisher(),
                                historyService));
        var cancellationDocumentService =
                new ProductionCancellationDocumentService(
                        documentEngine, cancellationRepository, CLOCK);
        var viewService =
                new ProductionOrderViewService(itemRepository, cancellationRepository);
        return new CancelOrderProductionService(
                viewService, cancellationRepository, cancellationDocumentService, txManager, CLOCK);
    }

    private ReleaseProductsService releaseService() {
        var releaseRepository = new JdbcProductionReleaseRepository(jdbc, CLOCK);
        JdbcProductionHistoryRepository jdbcHistory = new JdbcProductionHistoryRepository(jdbc);
        ProductionHistoryService historyService = new ProductionHistoryService(jdbcHistory, CLOCK);
        var documentEngine =
                documentEngine(
                        new ProductionReleaseProcessor(
                                releaseRepository,
                                itemRepository,
                                new TransactionAfterCommitEventPublisher(),
                                historyService),
                        new ProductionCancellationProcessor(
                                new JdbcProductionCancellationRepository(jdbc, CLOCK),
                                itemRepository,
                                new TransactionAfterCommitEventPublisher(),
                                historyService));
        var releaseDocumentService =
                new ProductionReleaseDocumentService(documentEngine, releaseRepository, CLOCK);
        var viewService = new ProductionOrderViewService(itemRepository);
        return new ReleaseProductsService(
                viewService,
                new ProductionFoundationQueryService(specificationQuery),
                warehouseAvailabilityQuery,
                warehouseApi,
                warehouseApi,
                new ProductionWarehouseScope(MAIN, PROD),
                releaseDocumentService,
                txManager,
                CLOCK);
    }

    private com.tmp.document.api.DocumentEngine documentEngine(
            ProductionReleaseProcessor releaseProcessor,
            ProductionCancellationProcessor cancellationProcessor) {
        var engine =
                new DefaultDocumentEngine(
                        new DefaultDocumentProcessorRegistry(),
                        new com.tmp.document.persistence.JdbcDocumentStorageAdapter(jdbc),
                        new com.tmp.document.persistence.JdbcLifecycleJournalAdapter(jdbc),
                        new com.tmp.document.persistence.JdbcDocumentVersionAdapter(jdbc),
                        new TransactionAfterCommitEventPublisher());
        engine.registerProcessor(releaseProcessor);
        engine.registerProcessor(cancellationProcessor);
        return engine;
    }

    private Scenario prepareInProductionScenario(BigDecimal productionStock) {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        MaterialReference material =
                materials.create(MaterialReference.create("MAT-OVR", "MAT-OVR", "", "", "шт."));
        itemRepository.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemId, specId, T0),
                        ProductionQuantity.positive(10),
                        T0));
        specificationQuery.byIdSpec =
                Optional.of(
                        new ResolvedSpecification(
                                specId,
                                itemId,
                                BigDecimal.TEN,
                                List.of(
                                        new ResolvedMaterialLine(
                                                "MAT-OVR",
                                                "MAT-OVR",
                                                "",
                                                null,
                                                new BigDecimal("17"),
                                                "шт."))));
        if (productionStock.signum() > 0) {
            stockPositions.create(
                    StockPosition.of(
                            productionWarehouseId,
                            productionCell,
                            material,
                            StockState.AVAILABLE,
                            StockQuantity.of(productionStock.longValueExact())));
        }
        return new Scenario(orderId, itemId, specId, material);
    }

    private static ReleaseProductsCommand releaseCommand(
            Scenario scenario, long releaseQuantity, BigDecimal actual) {
        return new ReleaseProductsCommand(
                scenario.orderId().value(),
                List.of(new ItemRelease(scenario.itemId().value(), releaseQuantity)),
                List.of(
                        new MaterialActualUsage(
                                scenario.itemId().value(),
                                scenario.material().id().value(),
                                actual,
                                List.of(new CellAllocation(PROD_CELL, actual)))));
    }

    private int countPostedCancellations() {
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM production.production_cancellations WHERE posted = TRUE",
                        Integer.class);
        return count == null ? 0 : count;
    }

    private int countPostedReleases() {
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM production.production_releases WHERE posted = TRUE",
                        Integer.class);
        return count == null ? 0 : count;
    }

    private int countConsumptionOperations() {
        Integer count =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM warehouse.warehouse_operations
                        WHERE operation_type = 'CONSUMPTION'
                        """,
                        Integer.class);
        return count == null ? 0 : count;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        }
    }

    private static BigDecimal bd(long value) {
        return BigDecimal.valueOf(value);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
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

    private record Scenario(
            SourceOrderId orderId,
            SourceOrderItemId itemId,
            SpecificationId specId,
            MaterialReference material) {}

    private static final class LockCoordinatingItemStateRepository
            implements ProductionItemStateRepository {

        private final ProductionItemStateRepository delegate;
        private volatile Runnable onForUpdateAcquired = () -> {};

        LockCoordinatingItemStateRepository(ProductionItemStateRepository delegate) {
            this.delegate = delegate;
        }

        void onForUpdateAcquired(Runnable hook) {
            this.onForUpdateAcquired = hook == null ? () -> {} : hook;
        }

        @Override
        public ProductionItemState save(ProductionItemState state) {
            return delegate.save(state);
        }

        @Override
        public Optional<ProductionItemState> findByIdentity(
                SourceOrderId sourceOrderId,
                SourceOrderItemId sourceOrderItemId,
                SpecificationId specificationId) {
            return delegate.findByIdentity(sourceOrderId, sourceOrderItemId, specificationId);
        }

        @Override
        public List<ProductionItemState> findBySourceOrderId(SourceOrderId sourceOrderId) {
            return delegate.findBySourceOrderId(sourceOrderId);
        }

        @Override
        public List<ProductionItemState> findBySourceOrderIdForUpdate(SourceOrderId sourceOrderId) {
            List<ProductionItemState> locked = delegate.findBySourceOrderIdForUpdate(sourceOrderId);
            onForUpdateAcquired.run();
            return locked;
        }
    }

    private static final class TrackingSpecificationQuery implements OrderSpecificationQueryPort {
        Optional<ResolvedSpecification> byIdSpec = Optional.empty();

        @Override
        public Optional<ResolvedSpecification> resolveCurrentForLaunch(
                SourceOrderItemId sourceOrderItemId) {
            return Optional.empty();
        }

        @Override
        public Optional<ResolvedSpecification> resolveById(SpecificationId specificationId) {
            return byIdSpec;
        }
    }

    private static final class WarehouseApiAvailabilityQuery
            implements WarehouseAvailabilityQueryPort {

        private final WarehouseQueryApi warehouseQuery;

        WarehouseApiAvailabilityQuery(WarehouseQueryApi warehouseQuery) {
            this.warehouseQuery = warehouseQuery;
        }

        @Override
        public List<WarehouseCatalogEntry> listWarehouses() {
            return warehouseQuery.listWarehouses().stream()
                    .map(
                            view ->
                                    new WarehouseCatalogEntry(
                                            view.warehouseId(),
                                            view.code(),
                                            view.name(),
                                            view.active()))
                    .toList();
        }

        @Override
        public List<MaterialReferenceEntry> listMaterialReferences() {
            return warehouseQuery.listMaterialReferences().stream()
                    .map(
                            view ->
                                    new MaterialReferenceEntry(
                                            view.materialReferenceId(),
                                            view.article(),
                                            view.name(),
                                            view.color(),
                                            view.size(),
                                            view.unitOfMeasure()))
                    .toList();
        }

        @Override
        public BigDecimal availableQuantity(UUID materialReferenceId, UUID warehouseId) {
            return warehouseQuery.getStockByMaterialReferenceId(materialReferenceId).stream()
                    .filter(
                            view ->
                                    warehouseId.equals(view.warehouseId())
                                            && view.stockState()
                                                    == WarehouseApi.StockStateView.AVAILABLE)
                    .map(WarehouseApi.StockView::quantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}
