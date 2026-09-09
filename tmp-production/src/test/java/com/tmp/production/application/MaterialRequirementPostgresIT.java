package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.application.port.OrderSpecificationQueryPort;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedSpecification;
import com.tmp.production.application.port.WarehouseReferenceQueryPort;
import com.tmp.production.application.port.WarehouseReferenceQueryPort.MaterialReferenceEntry;
import com.tmp.production.application.port.WarehouseReferenceQueryPort.WarehouseReferenceEntry;
import com.tmp.production.domain.CuttingPlanLinks;
import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementId;
import com.tmp.production.domain.MaterialRequirementLine;
import com.tmp.production.domain.MaterialRequirementLineId;
import com.tmp.production.domain.MaterialRequirementOptimisticLockException;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.persistence.JdbcMaterialRequirementRepository;
import com.tmp.production.persistence.JdbcProductionItemStateRepository;
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
 * Stage 3.5.9: PostgreSQL round-trip for Material Requirement prepare/edit with zero Warehouse
 * mutation.
 */
@Testcontainers
class MaterialRequirementPostgresIT {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC);
    private static final Instant T0 = Instant.parse("2026-09-09T12:00:00Z");
    private static final UUID PROD_WAREHOUSE =
            UUID.fromString("00000000-0000-4000-8000-000000000022");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static PlatformTransactionManager txManager;

    private JdbcProductionItemStateRepository itemStates;
    private MaterialRequirementService service;
    private TrackingSpecificationQuery specificationQuery;
    private TrackingWarehouseQuery warehouseQuery;

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
        jdbc.update("DELETE FROM production.material_requirement_line_source_items");
        jdbc.update("DELETE FROM production.material_requirement_lines");
        jdbc.update("DELETE FROM production.material_requirements");
        jdbc.update("DELETE FROM production.production_item_cutting_plan_links");
        jdbc.update("DELETE FROM production.production_item_states");
        jdbc.update("DELETE FROM warehouse.warehouse_movements");
        jdbc.update("DELETE FROM warehouse.warehouse_operations");
        jdbc.update("DELETE FROM warehouse.stock_positions");
        jdbc.update("DELETE FROM warehouse.transfer_document_lines");
        jdbc.update("DELETE FROM warehouse.transfer_document_payload");

        itemStates = new JdbcProductionItemStateRepository(jdbc, CLOCK);
        specificationQuery = new TrackingSpecificationQuery();
        warehouseQuery = new TrackingWarehouseQuery();
        warehouseQuery.warehouses =
                List.of(new WarehouseReferenceEntry(PROD_WAREHOUSE, "PROD", "Production", true));
        service =
                new MaterialRequirementService(
                        new ProductionOrderViewService(itemStates),
                        new ProductionFoundationQueryService(specificationQuery),
                        new ProductionDestinationWarehouse(PROD_WAREHOUSE),
                        warehouseQuery,
                        new JdbcMaterialRequirementRepository(jdbc, CLOCK, txManager),
                        CLOCK);
    }

    @Test
    void prepareAndChangeQuantityRoundTripOnPostgreSQL() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        launchItem(orderId, itemId, specId);
        specificationQuery.byIdSpec =
                Optional.of(
                        new ResolvedSpecification(
                                specId,
                                itemId,
                                BigDecimal.valueOf(5),
                                List.of(
                                        new ResolvedMaterialLine(
                                                "MAT-PG",
                                                "PG Material",
                                                "WHITE",
                                                null,
                                                BigDecimal.TEN,
                                                "PCS"))));
        UUID materialId = UUID.randomUUID();
        warehouseQuery.materialReferences =
                List.of(new MaterialReferenceEntry(materialId, "MAT-PG", "PG Material", "WHITE", "", "PCS"));

        WarehouseSnapshot before = snapshotWarehouse();

        MaterialRequirement prepared =
                service.prepareMaterialRequirement(orderId, List.of(itemId));
        assertEquals(PROD_WAREHOUSE, prepared.destinationWarehouseId());
        assertEquals(1, prepared.lines().size());
        assertEquals(0, prepared.lines().getFirst().quantity().compareTo(BigDecimal.TEN));
        assertEquals(0L, prepared.version());

        MaterialRequirementLine line = prepared.lines().getFirst();
        MaterialRequirement edited =
                service.changeQuantity(
                        prepared.requirementId(),
                        line.lineId(),
                        BigDecimal.valueOf(12),
                        prepared.version());
        assertEquals(1L, edited.version());
        assertEquals(0, edited.lines().getFirst().quantity().compareTo(BigDecimal.valueOf(12)));

        MaterialRequirement reloaded =
                service.findById(prepared.requirementId()).orElseThrow();
        assertEquals(1L, reloaded.version());
        assertEquals(0, reloaded.lines().getFirst().quantity().compareTo(BigDecimal.valueOf(12)));

        assertWarehouseUnchanged(before, snapshotWarehouse());
        assertTrue(warehouseQuery.findMaterialReferencesCalls >= 1);
    }

    @Test
    void sequentialStaleVersionIsRejected() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        launchItem(orderId, itemId, specId);
        specificationQuery.byIdSpec =
                Optional.of(
                        new ResolvedSpecification(
                                specId,
                                itemId,
                                BigDecimal.ONE,
                                List.of(
                                        new ResolvedMaterialLine(
                                                "MAT-STALE",
                                                "S",
                                                "WHITE",
                                                null,
                                                BigDecimal.TEN,
                                                "PCS"))));
        warehouseQuery.materialReferences =
                List.of(
                        new MaterialReferenceEntry(
                                UUID.randomUUID(), "MAT-STALE", "S", "WHITE", "", "PCS"));

        MaterialRequirement prepared =
                service.prepareMaterialRequirement(orderId, List.of(itemId));
        MaterialRequirementLine line = prepared.lines().getFirst();
        service.changeQuantity(
                prepared.requirementId(), line.lineId(), BigDecimal.valueOf(11), prepared.version());

        assertThrows(
                com.tmp.production.domain.MaterialRequirementOptimisticLockException.class,
                () ->
                        service.changeQuantity(
                                prepared.requirementId(),
                                line.lineId(),
                                BigDecimal.valueOf(99),
                                prepared.version()));
    }

    @Test
    void concurrentChangeQuantityExactlyOneWins() throws Exception {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        launchItem(orderId, itemId, specId);
        specificationQuery.byIdSpec =
                Optional.of(
                        new ResolvedSpecification(
                                specId,
                                itemId,
                                BigDecimal.ONE,
                                List.of(
                                        new ResolvedMaterialLine(
                                                "MAT-CONC",
                                                "C",
                                                "WHITE",
                                                null,
                                                BigDecimal.valueOf(10),
                                                "PCS"))));
        warehouseQuery.materialReferences =
                List.of(
                        new MaterialReferenceEntry(
                                UUID.randomUUID(), "MAT-CONC", "C", "WHITE", "", "PCS"));

        MaterialRequirement prepared =
                service.prepareMaterialRequirement(orderId, List.of(itemId));
        MaterialRequirementLineId lineId = prepared.lines().getFirst().lineId();
        long versionN = prepared.version();
        BigDecimal qtyA = BigDecimal.valueOf(21);
        BigDecimal qtyB = BigDecimal.valueOf(34);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger optimisticFailures = new AtomicInteger();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();
        AtomicReference<BigDecimal> winnerQuantity = new AtomicReference<>();

        Future<?> threadA =
                executor.submit(
                        () ->
                                runConcurrentChange(
                                        prepared.requirementId(),
                                        lineId,
                                        qtyA,
                                        versionN,
                                        ready,
                                        start,
                                        successes,
                                        optimisticFailures,
                                        unexpected,
                                        winnerQuantity));
        Future<?> threadB =
                executor.submit(
                        () ->
                                runConcurrentChange(
                                        prepared.requirementId(),
                                        lineId,
                                        qtyB,
                                        versionN,
                                        ready,
                                        start,
                                        successes,
                                        optimisticFailures,
                                        unexpected,
                                        winnerQuantity));

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        threadA.get(30, TimeUnit.SECONDS);
        threadB.get(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        if (unexpected.get() != null) {
            throw new AssertionError("Unexpected concurrent failure", unexpected.get());
        }
        assertEquals(1, successes.get());
        assertEquals(1, optimisticFailures.get());

        MaterialRequirement finalState = service.findById(prepared.requirementId()).orElseThrow();
        assertEquals(versionN + 1, finalState.version());
        assertEquals(0, winnerQuantity.get().compareTo(finalState.lines().getFirst().quantity()));
        assertTrue(
                finalState.lines().getFirst().quantity().compareTo(qtyA) == 0
                        || finalState.lines().getFirst().quantity().compareTo(qtyB) == 0);
    }

    private void runConcurrentChange(
            MaterialRequirementId requirementId,
            MaterialRequirementLineId lineId,
            BigDecimal quantity,
            long expectedVersion,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger successes,
            AtomicInteger optimisticFailures,
            AtomicReference<Throwable> unexpected,
            AtomicReference<BigDecimal> winnerQuantity) {
        ready.countDown();
        try {
            assertTrue(start.await(10, TimeUnit.SECONDS));
            MaterialRequirement saved =
                    service.changeQuantity(requirementId, lineId, quantity, expectedVersion);
            successes.incrementAndGet();
            winnerQuantity.set(saved.lines().getFirst().quantity());
        } catch (MaterialRequirementOptimisticLockException ex) {
            optimisticFailures.incrementAndGet();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            unexpected.compareAndSet(null, ex);
        } catch (RuntimeException ex) {
            unexpected.compareAndSet(null, ex);
        }
    }

    @Test
    void prepareAndEditLeavesWarehouseTablesUnchanged() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        launchItem(orderId, itemId, specId);
        specificationQuery.byIdSpec =
                Optional.of(
                        new ResolvedSpecification(
                                specId,
                                itemId,
                                BigDecimal.ONE,
                                List.of(
                                        new ResolvedMaterialLine(
                                                "MAT-Z", "Z", "WHITE", null, BigDecimal.ONE, "PCS"))));
        warehouseQuery.materialReferences =
                List.of(
                        new MaterialReferenceEntry(
                                UUID.randomUUID(), "MAT-Z", "Z", "WHITE", "", "PCS"));

        WarehouseSnapshot before = snapshotWarehouse();
        MaterialRequirement prepared =
                service.prepareMaterialRequirement(orderId, List.of(itemId));
        service.changeQuantity(
                prepared.requirementId(),
                prepared.lines().getFirst().lineId(),
                BigDecimal.valueOf(3),
                prepared.version());
        assertWarehouseUnchanged(before, snapshotWarehouse());
        assertEquals(0, before.operations());
        assertEquals(0, before.movements());
        assertEquals(0, before.stockQuantitySum().compareTo(BigDecimal.ZERO));
        assertEquals(0, before.transferDocuments());
    }

    @Test
    void v43MaterialRequirementTablesExistAfterMigrate() {
        Integer applied43 =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '43' AND success = TRUE
                        """,
                        Integer.class);
        assertEquals(1, applied43);

        List<String> tables =
                jdbc.queryForList(
                        """
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'production'
                          AND table_name LIKE 'material_requirement%'
                        ORDER BY table_name
                        """,
                        String.class);
        assertEquals(
                List.of(
                        "material_requirement_line_source_items",
                        "material_requirement_lines",
                        "material_requirements"),
                tables);
        assertTrue(tableExists("warehouse", "warehouse_operations"));
        assertTrue(tableExists("warehouse", "warehouse_movements"));
        assertTrue(tableExists("warehouse", "stock_positions"));
        assertTrue(tableExists("warehouse", "transfer_document_payload"));
    }

    private void launchItem(
            SourceOrderId orderId, SourceOrderItemId itemId, SpecificationId specId) {
        ProductionFoundation foundation =
                ProductionFoundation.freeze(orderId, itemId, specId, T0);
        itemStates.save(
                ProductionItemState.launch(
                        foundation,
                        ProductionQuantity.positive(5),
                        T0,
                        CuttingPlanLinks.empty()));
    }

    private void assertWarehouseUnchanged(WarehouseSnapshot before, WarehouseSnapshot after) {
        assertEquals(before.operations(), after.operations());
        assertEquals(before.movements(), after.movements());
        assertEquals(0, before.stockQuantitySum().compareTo(after.stockQuantitySum()));
        assertEquals(before.transferDocuments(), after.transferDocuments());
    }

    private WarehouseSnapshot snapshotWarehouse() {
        Integer operations =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.warehouse_operations", Integer.class);
        Integer movements =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.warehouse_movements", Integer.class);
        BigDecimal stockSum =
                jdbc.queryForObject(
                        """
                        SELECT COALESCE(SUM(quantity), 0)
                        FROM warehouse.stock_positions
                        """,
                        BigDecimal.class);
        Integer transferDocuments =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.transfer_document_payload", Integer.class);
        return new WarehouseSnapshot(
                operations == null ? 0 : operations,
                movements == null ? 0 : movements,
                stockSum == null ? BigDecimal.ZERO : stockSum,
                transferDocuments == null ? 0 : transferDocuments);
    }

    private boolean tableExists(String schema, String table) {
        Integer count =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = ? AND table_name = ?
                        """,
                        Integer.class,
                        schema,
                        table);
        return count != null && count == 1;
    }

    private record WarehouseSnapshot(
            int operations, int movements, BigDecimal stockQuantitySum, int transferDocuments) {}

    private static final class TrackingSpecificationQuery implements OrderSpecificationQueryPort {
        Optional<ResolvedSpecification> byIdSpec = Optional.empty();

        @Override
        public Optional<ResolvedSpecification> resolveCurrentForLaunch(
                SourceOrderItemId sourceOrderItemId) {
            throw new UnsupportedOperationException("current path must not be used after launch");
        }

        @Override
        public Optional<ResolvedSpecification> resolveById(SpecificationId specificationId) {
            return byIdSpec.filter(spec -> spec.specificationId().equals(specificationId));
        }
    }

    private static final class TrackingWarehouseQuery implements WarehouseReferenceQueryPort {
        List<WarehouseReferenceEntry> warehouses = List.of();
        List<MaterialReferenceEntry> materialReferences = List.of();
        int findMaterialReferencesCalls;

        @Override
        public Optional<WarehouseReferenceEntry> getWarehouse(UUID warehouseId) {
            return warehouses.stream()
                    .filter(entry -> entry.warehouseId().equals(warehouseId))
                    .findFirst();
        }

        @Override
        public List<MaterialReferenceEntry> findMaterialReferencesByIdentity(
                String article, String color, String unitOfMeasure) {
            findMaterialReferencesCalls++;
            String colorKey = color == null ? "" : color.trim();
            String unitKey = unitOfMeasure.trim();
            return materialReferences.stream()
                    .filter(
                            entry ->
                                    entry.article().equals(article)
                                            && entry.color().trim().equals(colorKey)
                                            && entry.unitOfMeasure().trim().equals(unitKey))
                    .toList();
        }
    }
}
