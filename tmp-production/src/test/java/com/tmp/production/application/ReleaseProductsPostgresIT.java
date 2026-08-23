package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.DefaultDocumentEngine;
import com.tmp.document.DefaultDocumentProcessorRegistry;
import com.tmp.document.TransactionAfterCommitEventPublisher;
import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentEngineStatus;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.DocumentQuery;
import com.tmp.document.api.DocumentStatus;
import com.tmp.document.api.DocumentTypeDescriptor;
import com.tmp.document.api.UpdateDocumentCommand;
import com.tmp.document.persistence.JdbcDocumentStorageAdapter;
import com.tmp.document.persistence.JdbcDocumentVersionAdapter;
import com.tmp.document.persistence.JdbcLifecycleJournalAdapter;
import com.tmp.production.application.ReleaseProductsCommand.CellAllocation;
import com.tmp.production.application.ReleaseProductsCommand.ItemRelease;
import com.tmp.production.application.ReleaseProductsCommand.MaterialActualUsage;
import com.tmp.production.application.ReleaseProductsResult;
import com.tmp.production.domain.ReleaseProductsException;
import com.tmp.production.application.document.ProductionReleaseProcessor;
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
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.persistence.JdbcProductionItemStateRepository;
import com.tmp.production.persistence.JdbcProductionReleaseRepository;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 * PostgreSQL integration proofs for Release Products orchestration (STAGE7-013): shared ACID
 * transaction between Warehouse Consumption and Production Release POST.
 */
@Testcontainers
class ReleaseProductsPostgresIT {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneOffset.UTC);
    private static final Instant T0 = Instant.parse("2026-08-23T12:00:00Z");

    private static final UUID MAIN = UUID.fromString("00000000-0000-4000-8000-000000000101");
    private static final UUID PROD = UUID.fromString("00000000-0000-4000-8000-000000000102");
    private static final UUID MAIN_CELL = UUID.fromString("00000000-0000-4000-8000-000000000201");
    private static final UUID PROD_CELL = UUID.fromString("00000000-0000-4000-8000-000000000203");

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
    private JdbcProductionItemStateRepository itemRepository;
    private JdbcProductionReleaseRepository releaseRepository;
    private DefaultDocumentEngine documentEngine;
    private ProductionReleaseDocumentService releaseDocumentService;
    private TrackingSpecificationQuery specificationQuery;
    private WarehouseAvailabilityQueryPort warehouseAvailabilityQuery;
    private WarehouseId mainWarehouseId;
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

        itemRepository = new JdbcProductionItemStateRepository(jdbc, CLOCK);
        releaseRepository = new JdbcProductionReleaseRepository(jdbc, CLOCK);
        documentEngine =
                new DefaultDocumentEngine(
                        new DefaultDocumentProcessorRegistry(),
                        new JdbcDocumentStorageAdapter(jdbc),
                        new JdbcLifecycleJournalAdapter(jdbc),
                        new JdbcDocumentVersionAdapter(jdbc),
                        new TransactionAfterCommitEventPublisher());
        documentEngine.registerProcessor(
                new ProductionReleaseProcessor(releaseRepository, itemRepository));
        releaseDocumentService =
                new ProductionReleaseDocumentService(documentEngine, releaseRepository, CLOCK);

        specificationQuery = new TrackingSpecificationQuery();
        warehouseAvailabilityQuery = new WarehouseApiAvailabilityQuery(warehouseApi);

        mainWarehouseId = WarehouseId.of(MAIN);
        productionWarehouseId = WarehouseId.of(PROD);
        productionCell = StorageCellId.of(PROD_CELL);
        catalog.save(Warehouse.create(mainWarehouseId, "WH-MAIN", "Main"));
        catalog.save(Warehouse.create(productionWarehouseId, "WH-PROD", "Production"));
        catalog.save(StorageCell.create(StorageCellId.of(MAIN_CELL), mainWarehouseId, "M-01"));
        catalog.save(StorageCell.create(productionCell, productionWarehouseId, "P-01"));
    }

    @Test
    void concurrentReleasesSerializeWithoutLostUpdate() throws Exception {
        ReleaseScenario scenario = prepareScenario(bd(200));
        ReleaseProductsService service = newService(warehouseApi, releaseDocumentService);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successes = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Future<?> first =
                executor.submit(
                        () ->
                                runConcurrentRelease(
                                        service,
                                        scenario,
                                        3,
                                        bd("5.100000"),
                                        ready,
                                        start,
                                        successes,
                                        failure));
        Future<?> second =
                executor.submit(
                        () ->
                                runConcurrentRelease(
                                        service,
                                        scenario,
                                        4,
                                        bd("6.800000"),
                                        ready,
                                        start,
                                        successes,
                                        failure));

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        first.get(30, TimeUnit.SECONDS);
        second.get(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        if (failure.get() != null) {
            throw new AssertionError("Concurrent release failed", failure.get());
        }
        assertEquals(2, successes.get());
        assertEquals(
                7L,
                itemRepository
                        .findByIdentity(
                                scenario.orderId(), scenario.itemId(), scenario.specId())
                        .orElseThrow()
                        .releasedQuantity()
                        .value()
                        .longValueExact());
        assertEquals(2, countPostedReleases());
    }

    @Test
    void cumulativePlanConcurrencyFormsNonOverlappingSegments() throws Exception {
        ReleaseScenario scenario = prepareScenario(bd(200));
        ReleaseProductsService service = newService(warehouseApi, releaseDocumentService);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successes = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Future<?> releaseThree =
                executor.submit(
                        () ->
                                runConcurrentRelease(
                                        service,
                                        scenario,
                                        3,
                                        bd("5.100000"),
                                        ready,
                                        start,
                                        successes,
                                        failure));
        Future<?> releaseFour =
                executor.submit(
                        () ->
                                runConcurrentRelease(
                                        service,
                                        scenario,
                                        4,
                                        bd("6.800000"),
                                        ready,
                                        start,
                                        successes,
                                        failure));

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        releaseThree.get(30, TimeUnit.SECONDS);
        releaseFour.get(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        if (failure.get() != null) {
            throw new AssertionError("Concurrent release failed", failure.get());
        }
        assertEquals(2, successes.get());

        List<BigDecimal> planned =
                jdbc.query(
                        """
                        SELECT planned_quantity
                        FROM production.production_release_material_lines ml
                        JOIN production.production_releases pr ON pr.document_id = ml.document_id
                        WHERE pr.posted = TRUE
                        ORDER BY pr.released_at, ml.planned_quantity
                        """,
                        (rs, rowNum) -> rs.getBigDecimal("planned_quantity"));
        assertEquals(2, planned.size());
        assertTrue(
                planned.contains(bd("5.100000")) && planned.contains(bd("6.800000")),
                "Expected both cumulative segments, got " + planned);
        BigDecimal totalPlan = planned.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalPlan.compareTo(bd("11.900000")));
    }

    @Test
    void concurrentOverReleaseOnlyOneSucceeds() throws Exception {
        ReleaseScenario scenario = prepareScenarioWithQuantity(bd(200), 5);
        ReleaseProductsService service = newService(warehouseApi, releaseDocumentService);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Future<?> first =
                executor.submit(
                        () ->
                                runConcurrentReleaseExpectingFailure(
                                        service,
                                        scenario,
                                        4,
                                        bd("6.800000"),
                                        ready,
                                        start,
                                        successes,
                                        rejections,
                                        failure));
        Future<?> second =
                executor.submit(
                        () ->
                                runConcurrentReleaseExpectingFailure(
                                        service,
                                        scenario,
                                        4,
                                        bd("6.800000"),
                                        ready,
                                        start,
                                        successes,
                                        rejections,
                                        failure));

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        first.get(30, TimeUnit.SECONDS);
        second.get(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        if (failure.get() != null) {
            throw new AssertionError("Concurrent over-release test failed", failure.get());
        }
        assertEquals(1, successes.get());
        assertEquals(1, rejections.get());
        long released =
                itemRepository
                        .findByIdentity(
                                scenario.orderId(), scenario.itemId(), scenario.specId())
                        .orElseThrow()
                        .releasedQuantity()
                        .value()
                        .longValueExact();
        assertTrue(released <= 5L);
        assertEquals(1, countPostedReleases());
        assertEquals(1, countConsumptionOperations());
    }

    @Test
    void consumptionFailureRollsBackNoPostedReleaseNoStockChange() {
        ReleaseScenario scenario = prepareScenario(bd(20));
        WarehouseCommandApi failingCommands =
                new DelegatingCommandApi(warehouseApi) {
                    @Override
                    public WarehouseApi.OperationResult consume(
                            WarehouseApi.ConsumptionCommand command) {
                        throw new RuntimeException("controlled consumption failure");
                    }
                };
        ReleaseProductsService service =
                newService(failingCommands, releaseDocumentService);

        BigDecimal stockBefore = availableProductionStock(scenario.material());

        assertThrows(
                RuntimeException.class,
                () ->
                        service.releaseProducts(
                                releaseCommand(
                                        scenario,
                                        bd("5.100000"),
                                        List.of(
                                                new CellAllocation(
                                                        PROD_CELL, bd("5.100000"))))));

        assertEquals(0, countPostedReleases());
        assertEquals(0, countConsumptionOperations());
        assertEquals(
                0, availableProductionStock(scenario.material()).compareTo(stockBefore));
        assertEquals(
                ProductionStatus.IN_PRODUCTION,
                itemRepository
                        .findByIdentity(
                                scenario.orderId(), scenario.itemId(), scenario.specId())
                        .orElseThrow()
                        .status());
    }

    @Test
    void releasePostFailureAfterSuccessfulConsumptionRollsBackStock() {
        ReleaseScenario scenario = prepareScenario(bd(20));
        ProductionReleaseDocumentService failingReleaseDocumentService =
                new ProductionReleaseDocumentService(
                        new FailingPostDocumentEngine(documentEngine),
                        releaseRepository,
                        CLOCK);
        ReleaseProductsService service =
                newService(warehouseApi, failingReleaseDocumentService);

        BigDecimal stockBefore = availableProductionStock(scenario.material());

        assertThrows(
                RuntimeException.class,
                () ->
                        service.releaseProducts(
                                releaseCommand(
                                        scenario,
                                        bd("5.100000"),
                                        List.of(
                                                new CellAllocation(
                                                        PROD_CELL, bd("5.100000"))))));

        assertEquals(0, countPostedReleases());
        assertEquals(
                0, availableProductionStock(scenario.material()).compareTo(stockBefore));
        assertEquals(
                ProductionStatus.IN_PRODUCTION,
                itemRepository
                        .findByIdentity(
                                scenario.orderId(), scenario.itemId(), scenario.specId())
                        .orElseThrow()
                        .status());
    }

    private ReleaseProductsService newService(
            WarehouseCommandApi commands, ProductionReleaseDocumentService documentService) {
        return new ReleaseProductsService(
                new ProductionOrderViewService(itemRepository),
                new ProductionFoundationQueryService(specificationQuery),
                warehouseAvailabilityQuery,
                commands,
                warehouseApi,
                new ProductionWarehouseScope(MAIN, PROD),
                documentService,
                txManager,
                CLOCK);
    }

    private ReleaseScenario prepareScenario(BigDecimal productionStock) {
        return prepareScenarioWithQuantity(productionStock, 10);
    }

    private ReleaseScenario prepareScenarioWithQuantity(
            BigDecimal productionStock, long orderedQuantity) {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        MaterialReference material = materials.create(MaterialReference.create("MAT-IT", "MAT-IT", "", "", "шт."));
        itemRepository.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemId, specId, T0),
                        ProductionQuantity.positive(orderedQuantity),
                        T0));
        specificationQuery.byIdSpec =
                Optional.of(
                        new ResolvedSpecification(
                                specId,
                                itemId,
                                BigDecimal.TEN,
                                List.of(
                                        new ResolvedMaterialLine(
                                                "MAT-IT",
                                                "MAT-IT",
                                                "",
                                                null,
                                                new BigDecimal("17"),
                                                "шт."))));
        stockPositions.create(
                StockPosition.of(
                        productionWarehouseId,
                        productionCell,
                        material,
                        StockState.AVAILABLE,
                        StockQuantity.of(productionStock.longValueExact())));
        return new ReleaseScenario(orderId, itemId, specId, material);
    }

    private static void runConcurrentRelease(
            ReleaseProductsService service,
            ReleaseScenario scenario,
            long releaseQuantity,
            BigDecimal actual,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger successes,
            AtomicReference<Throwable> failure) {
        try {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            service.releaseProducts(
                    releaseCommand(scenario, releaseQuantity, actual, List.of(
                            new CellAllocation(PROD_CELL, actual))));
            successes.incrementAndGet();
        } catch (Throwable ex) {
            failure.compareAndSet(null, ex);
        }
    }

    private static void runConcurrentReleaseExpectingFailure(
            ReleaseProductsService service,
            ReleaseScenario scenario,
            long releaseQuantity,
            BigDecimal actual,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger successes,
            AtomicInteger rejections,
            AtomicReference<Throwable> failure) {
        try {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            service.releaseProducts(
                    releaseCommand(scenario, releaseQuantity, actual, List.of(
                            new CellAllocation(PROD_CELL, actual))));
            successes.incrementAndGet();
        } catch (ReleaseProductsException ex) {
            rejections.incrementAndGet();
        } catch (Throwable ex) {
            failure.compareAndSet(null, ex);
        }
    }

    private static ReleaseProductsCommand releaseCommand(
            ReleaseScenario scenario, BigDecimal actual, List<CellAllocation> allocations) {
        return releaseCommand(scenario, 3, actual, allocations);
    }

    private static ReleaseProductsCommand releaseCommand(
            ReleaseScenario scenario,
            long releaseQuantity,
            BigDecimal actual,
            List<CellAllocation> allocations) {
        return new ReleaseProductsCommand(
                scenario.orderId().value(),
                List.of(new ItemRelease(scenario.itemId().value(), releaseQuantity)),
                List.of(
                        new MaterialActualUsage(
                                scenario.itemId().value(),
                                scenario.material().id().value(),
                                actual,
                                allocations)));
    }

    private BigDecimal availableProductionStock(MaterialReference material) {
        return stockPositions
                .findByNaturalKey(
                        productionWarehouseId,
                        productionCell,
                        material,
                        StockState.AVAILABLE)
                .map(pos -> pos.quantity().value())
                .orElse(BigDecimal.ZERO);
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

    private record ReleaseScenario(
            SourceOrderId orderId,
            SourceOrderItemId itemId,
            SpecificationId specId,
            MaterialReference material) {}

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

    private static final class FailingPostDocumentEngine implements DocumentEngine {

        private final DocumentEngine delegate;

        FailingPostDocumentEngine(DocumentEngine delegate) {
            this.delegate = delegate;
        }

        @Override
        public DocumentProcessorRegistration registerProcessor(
                com.tmp.document.api.DocumentProcessor processor) {
            return delegate.registerProcessor(processor);
        }

        @Override
        public DocumentMetadata createDocument(CreateDocumentCommand command) {
            return delegate.createDocument(command);
        }

        @Override
        public DocumentMetadata updateDocument(UpdateDocumentCommand command) {
            return delegate.updateDocument(command);
        }

        @Override
        public DocumentMetadata postDocument(UUID documentId) {
            throw new RuntimeException("controlled release post failure");
        }

        @Override
        public DocumentMetadata unpostDocument(UUID documentId) {
            return delegate.unpostDocument(documentId);
        }

        @Override
        public DocumentMetadata closeDocument(UUID documentId) {
            return delegate.closeDocument(documentId);
        }

        @Override
        public void deleteDocument(UUID documentId) {
            delegate.deleteDocument(documentId);
        }

        @Override
        public Optional<DocumentMetadata> findById(UUID documentId) {
            return delegate.findById(documentId);
        }

        @Override
        public List<DocumentMetadata> search(DocumentQuery query) {
            return delegate.search(query);
        }

        @Override
        public List<DocumentTypeDescriptor> registeredTypes() {
            return delegate.registeredTypes();
        }

        @Override
        public DocumentEngineStatus status() {
            return delegate.status();
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
