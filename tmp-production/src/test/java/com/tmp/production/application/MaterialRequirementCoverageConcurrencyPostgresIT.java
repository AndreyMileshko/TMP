package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.domain.CuttingPlanLinks;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementCoverageConflictException;
import com.tmp.production.domain.MaterialRequirementLine;
import com.tmp.production.domain.MaterialRequirementLineContribution;
import com.tmp.production.domain.MaterialRequirementSourceItem;
import com.tmp.production.domain.MaterialRequirementStatus;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.MaterialRequirementSubmissionRepository;
import com.tmp.production.persistence.JdbcMaterialRequirementRepository;
import com.tmp.production.persistence.JdbcMaterialRequirementSubmissionRepository;
import com.tmp.production.persistence.JdbcProductionItemStateRepository;
import com.tmp.production.testsupport.WarehouseTestDoubles;
import com.tmp.warehouse.api.WarehouseDemandCommandApi;
import com.tmp.warehouse.application.DefaultWarehouseDemandCommandApi;
import com.tmp.warehouse.application.MaterialSourceRoutingService;
import com.tmp.warehouse.application.WarehouseTransferDocumentService;
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
import com.tmp.warehouse.persistence.JdbcAvailableStockAggregationQuery;
import com.tmp.warehouse.persistence.JdbcMaterialReferenceRepository;
import com.tmp.warehouse.persistence.JdbcStockPositionRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseCatalogRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseDemandRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseStockRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseTransferDocumentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Stage 7 Phase 2: concurrent submit coverage conflict and cross-order lock ordering (no deadlock).
 */
@Testcontainers
class MaterialRequirementCoverageConcurrencyPostgresIT {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-02T09:00:00Z"), ZoneOffset.UTC);
    private static final Instant T0 = Instant.parse("2026-10-02T09:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static PlatformTransactionManager txManager;

    private JdbcMaterialRequirementRepository requirements;
    private JdbcMaterialRequirementSubmissionRepository submissions;
    private JdbcProductionItemStateRepository itemStates;
    private WarehouseCatalogRepository catalog;
    private MaterialReferenceRepository materials;
    private StockPositionRepository stockPositions;
    private SubmitMaterialRequirementService service;

    private WarehouseId destination;
    private WarehouseId sourceA;
    private StorageCellId cellA;
    private MaterialReference materialA;

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
        jdbc.update("DELETE FROM production.material_requirement_routing_snapshot");
        jdbc.update("DELETE FROM production.material_requirement_generated_documents");
        jdbc.update("DELETE FROM production.material_requirement_line_source_items");
        jdbc.update("DELETE FROM production.material_requirement_lines");
        jdbc.update("DELETE FROM production.material_requirement_source_items");
        jdbc.update("DELETE FROM production.material_requirements");
        jdbc.update("DELETE FROM production.production_item_cutting_plan_links");
        jdbc.update("DELETE FROM production.production_item_states");
        jdbc.update("DELETE FROM warehouse.warehouse_demand_transfer_links");
        jdbc.update("DELETE FROM warehouse.warehouse_demand_lines");
        jdbc.update("DELETE FROM warehouse.warehouse_demands");
        jdbc.update("DELETE FROM warehouse.transfer_return_settlement_item");
        jdbc.update("DELETE FROM warehouse.transfer_receipt_settlement_item");
        jdbc.update("DELETE FROM warehouse.transfer_document_settlement");
        jdbc.update("DELETE FROM warehouse.transfer_document_send_allocation");
        jdbc.update("DELETE FROM warehouse.transfer_task_state");
        jdbc.update("DELETE FROM warehouse.transfer_document_lines");
        jdbc.update("DELETE FROM warehouse.transfer_document_payload");
        jdbc.update("DELETE FROM documents.document_lifecycle_journal");
        jdbc.update("DELETE FROM documents.document_versions");
        jdbc.update("DELETE FROM documents.documents");
        jdbc.update("DELETE FROM warehouse.warehouse_movements");
        jdbc.update("DELETE FROM warehouse.warehouse_operations");
        jdbc.update("DELETE FROM warehouse.stock_positions");
        jdbc.update("DELETE FROM warehouse.storage_cells");
        jdbc.update("DELETE FROM warehouse.warehouses");
        jdbc.update("DELETE FROM warehouse.material_references");

        var stockJdbc = new JdbcWarehouseStockRepository(jdbc, CLOCK);
        materials = new JdbcMaterialReferenceRepository(jdbc, CLOCK);
        catalog = new JdbcWarehouseCatalogRepository(jdbc, CLOCK);
        stockPositions = new JdbcStockPositionRepository(stockJdbc);
        TransactionTemplate warehouseTx = new TransactionTemplate(txManager);
        WarehouseTransferDocumentService transferDocuments =
                WarehouseTestDoubles.transferDocumentService(
                        jdbc,
                        CLOCK,
                        catalog,
                        materials,
                        WarehouseTestDoubles.permitAllResponsibility(),
                        warehouseTx);
        requirements = new JdbcMaterialRequirementRepository(jdbc, CLOCK, txManager);
        submissions = new JdbcMaterialRequirementSubmissionRepository(jdbc);
        itemStates = new JdbcProductionItemStateRepository(jdbc, CLOCK);
        service = newService(transferDocuments, submissions);

        destination = WarehouseId.generate();
        sourceA = WarehouseId.generate();
        catalog.save(Warehouse.create(destination, "DEST", "Destination"));
        catalog.save(Warehouse.create(sourceA, "SRC-A", "Source A"));
        cellA = StorageCellId.generate();
        catalog.save(StorageCell.create(cellA, sourceA, "A-1"));
        materialA = materials.create(MaterialReference.create("ART-COV", "ART-COV", "", "", "шт."));
    }

    @Test
    void concurrentOverlappingSubmitsAllowExactlyOneWhenCombinedExceedsCoverage() throws Exception {
        seed(sourceA, cellA, materialA, "500");
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        launchItem(orderId, itemId, 10L);

        MaterialRequirement draftA = persistDraft(orderId, itemId, 6L, materialA, "60");
        MaterialRequirement draftB = persistDraft(orderId, itemId, 6L, materialA, "60");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();

        Runnable taskA = concurrentSubmitTask(draftA, ready, start, successes, conflicts, unexpected);
        Runnable taskB = concurrentSubmitTask(draftB, ready, start, successes, conflicts, unexpected);

        Future<?> a = executor.submit(taskA);
        Future<?> b = executor.submit(taskB);
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        a.get(60, TimeUnit.SECONDS);
        b.get(60, TimeUnit.SECONDS);
        executor.shutdownNow();
        if (unexpected.get() != null) {
            throw new AssertionError("Unexpected concurrent failure", unexpected.get());
        }
        assertEquals(1, successes.get());
        assertEquals(1, conflicts.get());
        assertEquals(1, countSubmitted());
    }

    @Test
    void crossOrderReverseLockOrderCompletesWithoutDeadlock() throws Exception {
        seed(sourceA, cellA, materialA, "500");
        // Lexicographically ordered UUID values so Order1 < Order2 by value compare.
        SourceOrderId order1 =
                SourceOrderId.of(UUID.fromString("11111111-1111-4111-8111-111111111111"));
        SourceOrderId order2 =
                SourceOrderId.of(UUID.fromString("22222222-2222-4222-8222-222222222222"));
        SourceOrderItemId item1 = SourceOrderItemId.generate();
        SourceOrderItemId item2 = SourceOrderItemId.generate();
        launchItem(order1, item1, 10L);
        launchItem(order2, item2, 10L);

        MaterialRequirement reqA =
                persistCrossOrderDraft(
                        List.of(
                                source(order1, item1, 5L),
                                source(order2, item2, 5L)),
                        materialA,
                        "50");
        MaterialRequirement reqB =
                persistCrossOrderDraft(
                        List.of(
                                source(order2, item2, 5L),
                                source(order1, item1, 5L)),
                        materialA,
                        "50");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger completed = new AtomicInteger();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();

        Runnable submitA =
                () -> {
                    ready.countDown();
                    try {
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        service.submit(reqA.requirementId(), reqA.version(), "user-a");
                        completed.incrementAndGet();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        unexpected.compareAndSet(null, ex);
                    } catch (RuntimeException ex) {
                        // Coverage conflict is acceptable; deadlock / hang is not.
                        if (!(ex instanceof MaterialRequirementCoverageConflictException)) {
                            unexpected.compareAndSet(null, ex);
                        } else {
                            completed.incrementAndGet();
                        }
                    }
                };
        Runnable submitB =
                () -> {
                    ready.countDown();
                    try {
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        service.submit(reqB.requirementId(), reqB.version(), "user-b");
                        completed.incrementAndGet();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        unexpected.compareAndSet(null, ex);
                    } catch (RuntimeException ex) {
                        if (!(ex instanceof MaterialRequirementCoverageConflictException)) {
                            unexpected.compareAndSet(null, ex);
                        } else {
                            completed.incrementAndGet();
                        }
                    }
                };

        Future<?> a = executor.submit(submitA);
        Future<?> b = executor.submit(submitB);
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        a.get(60, TimeUnit.SECONDS);
        b.get(60, TimeUnit.SECONDS);
        executor.shutdownNow();
        if (unexpected.get() != null) {
            throw new AssertionError("Unexpected concurrent failure", unexpected.get());
        }
        assertEquals(2, completed.get());
        assertTrue(countSubmitted() >= 1);
        assertTrue(countSubmitted() <= 2);
    }

    private Runnable concurrentSubmitTask(
            MaterialRequirement draft,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger successes,
            AtomicInteger conflicts,
            AtomicReference<Throwable> unexpected) {
        return () -> {
            ready.countDown();
            try {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                service.submit(draft.requirementId(), draft.version(), "user-1");
                successes.incrementAndGet();
            } catch (MaterialRequirementCoverageConflictException ex) {
                conflicts.incrementAndGet();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                unexpected.compareAndSet(null, ex);
            } catch (RuntimeException ex) {
                unexpected.compareAndSet(null, ex);
            }
        };
    }

    private SubmitMaterialRequirementService newService(
            WarehouseTransferDocumentService transfers,
            MaterialRequirementSubmissionRepository submissionRepository) {
        WarehouseDemandCommandApi demand =
                new DefaultWarehouseDemandCommandApi(
                        new MaterialSourceRoutingService(
                                new JdbcAvailableStockAggregationQuery(jdbc)),
                        transfers,
                        catalog,
                        materials,
                        new JdbcWarehouseDemandRepository(jdbc),
                        new JdbcWarehouseTransferDocumentRepository(jdbc, CLOCK),
                        new com.tmp.warehouse.persistence.JdbcWarehouseDemandFulfillmentReadQuery(
                                jdbc),
                        CLOCK,
                        new TransactionTemplate(txManager));
        ProductionOrderViewService orderViewService = new ProductionOrderViewService(itemStates);
        MaterialRequirementCoverageService coverageService =
                new MaterialRequirementCoverageService(orderViewService, requirements);
        return new SubmitMaterialRequirementService(
                requirements,
                submissionRepository,
                demand,
                orderViewService,
                coverageService,
                txManager,
                CLOCK);
    }

    private void launchItem(SourceOrderId orderId, SourceOrderItemId itemId, long qty) {
        itemStates.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(
                                orderId, itemId, SpecificationId.generate(), T0),
                        ProductionQuantity.positive(qty),
                        T0,
                        CuttingPlanLinks.empty()));
    }

    private MaterialRequirement persistDraft(
            SourceOrderId orderId,
            SourceOrderItemId itemId,
            long productQty,
            MaterialReference material,
            String materialQty) {
        return persistCrossOrderDraft(
                List.of(source(orderId, itemId, productQty)), material, materialQty);
    }

    private MaterialRequirement persistCrossOrderDraft(
            List<MaterialRequirementSourceItem> sourceItems,
            MaterialReference material,
            String materialQty) {
        BigDecimal qty = new BigDecimal(materialQty);
        List<MaterialRequirementLineContribution> contributions = new ArrayList<>();
        for (MaterialRequirementSourceItem item : sourceItems) {
            contributions.add(
                    MaterialRequirementLineContribution.of(
                            item.sourceOrderId(),
                            item.sourceOrderItemId(),
                            qty.divide(BigDecimal.valueOf(sourceItems.size()))));
        }
        MaterialRequirementLine line =
                MaterialRequirementLine.create(
                        MaterialReferenceId.of(material.id().value()),
                        material.article(),
                        material.article(),
                        material.color() == null ? "" : material.color(),
                        material.unitOfMeasure(),
                        null,
                        qty,
                        contributions);
        return requirements.save(
                MaterialRequirement.create(destination.value(), T0, sourceItems, List.of(line)));
    }

    private static MaterialRequirementSourceItem source(
            SourceOrderId orderId, SourceOrderItemId itemId, long productQty) {
        return MaterialRequirementSourceItem.of(orderId, itemId, productQty);
    }

    private void seed(
            WarehouseId warehouseId, StorageCellId cellId, MaterialReference material, String qty) {
        stockPositions.create(
                StockPosition.of(
                        warehouseId,
                        cellId,
                        material,
                        StockState.AVAILABLE,
                        StockQuantity.of(new BigDecimal(qty))));
    }

    private int countSubmitted() {
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM production.material_requirements WHERE status = 'SUBMITTED'",
                        Integer.class);
        return count == null ? 0 : count;
    }
}
