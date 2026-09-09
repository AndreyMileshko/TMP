package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementId;
import com.tmp.production.domain.MaterialRequirementLine;
import com.tmp.production.domain.MaterialRequirementShortageException;
import com.tmp.production.domain.MaterialRequirementStatus;
import com.tmp.production.domain.MaterialRequirementSubmissionCorruptedException;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.repository.MaterialRequirementSubmissionRepository;
import com.tmp.production.domain.repository.MaterialRequirementSubmissionRepository.GeneratedDocumentLink;
import com.tmp.production.domain.repository.MaterialRequirementSubmissionRepository.RoutingSnapshotRow;
import com.tmp.production.persistence.JdbcMaterialRequirementRepository;
import com.tmp.production.persistence.JdbcMaterialRequirementSubmissionRepository;
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
import com.tmp.warehouse.persistence.JdbcWarehouseStockRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 * Stage 3.5.10 PostgreSQL proofs: Submit ACID transaction, concurrency, idempotency, rollback,
 * routing snapshot, zero stock mutation.
 */
@Testcontainers
class SubmitMaterialRequirementPostgresIT {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);
    private static final Instant T0 = Instant.parse("2026-09-10T12:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static PlatformTransactionManager txManager;

    private JdbcMaterialRequirementRepository requirements;
    private JdbcMaterialRequirementSubmissionRepository submissions;
    private WarehouseCatalogRepository catalog;
    private MaterialReferenceRepository materials;
    private StockPositionRepository stockPositions;
    private WarehouseTransferDocumentService transferDocuments;
    private SubmitMaterialRequirementService service;

    private WarehouseId destination;
    private WarehouseId sourceA;
    private WarehouseId sourceB;
    private StorageCellId cellA;
    private StorageCellId cellB;
    private MaterialReference materialA;
    private MaterialReference materialB;

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
        jdbc.update("DELETE FROM production.material_requirements");
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
        transferDocuments =
                WarehouseTestDoubles.transferDocumentService(
                        jdbc,
                        CLOCK,
                        catalog,
                        materials,
                        WarehouseTestDoubles.permitAllResponsibility(),
                        warehouseTx);
        requirements = new JdbcMaterialRequirementRepository(jdbc, CLOCK, txManager);
        submissions = new JdbcMaterialRequirementSubmissionRepository(jdbc);
        service = newService(transferDocuments, submissions);

        destination = WarehouseId.generate();
        sourceA = WarehouseId.generate();
        sourceB = WarehouseId.generate();
        catalog.save(Warehouse.create(destination, "DEST", "Destination"));
        catalog.save(Warehouse.create(sourceA, "SRC-A", "Source A"));
        catalog.save(Warehouse.create(sourceB, "SRC-B", "Source B"));
        cellA = StorageCellId.generate();
        cellB = StorageCellId.generate();
        catalog.save(StorageCell.create(cellA, sourceA, "A-1"));
        catalog.save(StorageCell.create(cellB, sourceB, "B-1"));
        materialA = materials.create(MaterialReference.legacyArticle("ART-A"));
        materialB = materials.create(MaterialReference.legacyArticle("ART-B"));
    }

    @Test
    void submitPersistsSubmittedStateDocumentsAndSnapshotWithoutStockDelta() {
        seed(sourceA, cellA, materialA, "100");
        MaterialRequirement draft = persistDraft(line(materialA, "40"));
        int stockRows = count("warehouse.stock_positions");
        BigDecimal stockSum = stockSum();
        int ops = count("warehouse.warehouse_operations");
        int moves = count("warehouse.warehouse_movements");

        SubmitMaterialRequirementResult result =
                service.submit(draft.requirementId(), draft.version(), "user-1");

        assertTrue(result.created());
        MaterialRequirement loaded = requirements.findById(draft.requirementId()).orElseThrow();
        assertEquals(MaterialRequirementStatus.SUBMITTED, loaded.status());
        assertEquals(Optional.of("user-1"), loaded.submittedBy());
        assertEquals(draft.version() + 1, loaded.version());
        assertEquals(1, result.documents().size());
        assertEquals(1, submissions.findGeneratedDocuments(draft.requirementId()).size());
        assertEquals(1, submissions.findRoutingSnapshot(draft.requirementId()).size());
        assertEquals(1, count("warehouse.transfer_document_payload"));
        assertEquals(stockRows, count("warehouse.stock_positions"));
        assertEquals(0, stockSum.compareTo(stockSum()));
        assertEquals(ops, count("warehouse.warehouse_operations"));
        assertEquals(moves, count("warehouse.warehouse_movements"));
        assertEquals(
                0,
                new BigDecimal("40")
                        .compareTo(
                                jdbc.queryForObject(
                                        "SELECT quantity FROM warehouse.transfer_document_lines LIMIT 1",
                                        BigDecimal.class)));
    }

    @Test
    void multiSourceCreatesOneDocumentPerSource() {
        seed(sourceA, cellA, materialA, "50");
        seed(sourceB, cellB, materialB, "50");
        MaterialRequirement draft =
                persistDraft(line(materialA, "10"), line(materialB, "20"));

        SubmitMaterialRequirementResult result =
                service.submit(draft.requirementId(), 0L, "user-1");

        assertEquals(2, result.documents().size());
        assertEquals(2, submissions.findGeneratedDocuments(draft.requirementId()).size());
        assertEquals(2, submissions.findRoutingSnapshot(draft.requirementId()).size());
        assertEquals(2, count("warehouse.transfer_document_payload"));
    }

    @Test
    void retryReturnsExistingResultAndCreatesNoNewDocuments() {
        seed(sourceA, cellA, materialA, "50");
        MaterialRequirement draft = persistDraft(line(materialA, "10"));
        SubmitMaterialRequirementResult first =
                service.submit(draft.requirementId(), 0L, "user-1");
        int docs = count("warehouse.transfer_document_payload");

        SubmitMaterialRequirementResult retry =
                service.submit(draft.requirementId(), 0L, "other");

        assertTrue(!retry.created());
        assertEquals(first.documents().getFirst().warehouseDocumentId(), retry.documents().getFirst().warehouseDocumentId());
        assertEquals(docs, count("warehouse.transfer_document_payload"));
    }

    @Test
    void shortageRollsBackEverything() {
        seed(sourceA, cellA, materialA, "50");
        MaterialRequirement draft =
                persistDraft(line(materialA, "10"), line(materialB, "10"));

        assertThrows(
                MaterialRequirementShortageException.class,
                () -> service.submit(draft.requirementId(), 0L, "user-1"));

        assertEquals(
                MaterialRequirementStatus.DRAFT,
                requirements.findById(draft.requirementId()).orElseThrow().status());
        assertEquals(0, submissions.findGeneratedDocuments(draft.requirementId()).size());
        assertEquals(0, submissions.findRoutingSnapshot(draft.requirementId()).size());
        assertEquals(0, count("warehouse.transfer_document_payload"));
        assertEquals(0, count("documents.documents"));
    }

    @Test
    void secondDocumentFailureRollsBackFirst() {
        seed(sourceA, cellA, materialA, "50");
        seed(sourceB, cellB, materialB, "50");
        AtomicInteger payloadInserts = new AtomicInteger();
        JdbcTemplate wrapping =
                new JdbcTemplate(dataSource) {
                    @Override
                    public int update(String sql, Object... args) {
                        if (sql.contains("transfer_document_payload") && sql.contains("INSERT")) {
                            if (payloadInserts.incrementAndGet() >= 2) {
                                throw new IllegalStateException("injected second document failure");
                            }
                        }
                        return super.update(sql, args);
                    }
                };
        WarehouseTransferDocumentService failingTransfers =
                WarehouseTestDoubles.transferDocumentService(
                        wrapping,
                        CLOCK,
                        catalog,
                        materials,
                        WarehouseTestDoubles.permitAllResponsibility(),
                        new TransactionTemplate(txManager));
        SubmitMaterialRequirementService failingService = newService(failingTransfers, submissions);
        MaterialRequirement draft =
                persistDraft(line(materialA, "10"), line(materialB, "10"));

        assertThrows(
                IllegalStateException.class,
                () -> failingService.submit(draft.requirementId(), 0L, "user-1"));

        assertEquals(
                MaterialRequirementStatus.DRAFT,
                requirements.findById(draft.requirementId()).orElseThrow().status());
        assertEquals(0, count("warehouse.transfer_document_payload"));
        assertEquals(0, count("documents.documents"));
        assertEquals(0, submissions.findGeneratedDocuments(draft.requirementId()).size());
        assertEquals(0, submissions.findRoutingSnapshot(draft.requirementId()).size());
    }

    @Test
    void failureAfterWarehouseDocsCreatedRollsBackDocuments() {
        seed(sourceA, cellA, materialA, "50");
        MaterialRequirementSubmissionRepository failingSubmissions =
                new MaterialRequirementSubmissionRepository() {
                    @Override
                    public void saveGeneratedDocuments(
                            MaterialRequirementId requirementId, List<GeneratedDocumentLink> documents) {
                        throw new IllegalStateException("injected production persistence failure");
                    }

                    @Override
                    public void saveRoutingSnapshot(
                            MaterialRequirementId requirementId, List<RoutingSnapshotRow> snapshot) {}

                    @Override
                    public List<GeneratedDocumentLink> findGeneratedDocuments(
                            MaterialRequirementId requirementId) {
                        return List.of();
                    }

                    @Override
                    public List<RoutingSnapshotRow> findRoutingSnapshot(
                            MaterialRequirementId requirementId) {
                        return List.of();
                    }
                };
        SubmitMaterialRequirementService failingService =
                newService(transferDocuments, failingSubmissions);
        MaterialRequirement draft = persistDraft(line(materialA, "10"));

        assertThrows(
                IllegalStateException.class,
                () -> failingService.submit(draft.requirementId(), 0L, "user-1"));

        assertEquals(
                MaterialRequirementStatus.DRAFT,
                requirements.findById(draft.requirementId()).orElseThrow().status());
        assertEquals(0, count("warehouse.transfer_document_payload"));
        assertEquals(0, count("documents.documents"));
        assertEquals(0, count("production.material_requirement_generated_documents"));
        assertEquals(0, count("production.material_requirement_routing_snapshot"));
    }

    @Test
    void concurrentSubmitCreatesDocumentsExactlyOnce() throws Exception {
        seed(sourceA, cellA, materialA, "50");
        MaterialRequirement draft = persistDraft(line(materialA, "10"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger retries = new AtomicInteger();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();

        Runnable task =
                () -> {
                    ready.countDown();
                    try {
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        SubmitMaterialRequirementResult result =
                                service.submit(draft.requirementId(), 0L, "user-1");
                        if (result.created()) {
                            created.incrementAndGet();
                        } else {
                            retries.incrementAndGet();
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        unexpected.compareAndSet(null, ex);
                    } catch (RuntimeException ex) {
                        unexpected.compareAndSet(null, ex);
                    }
                };
        Future<?> a = executor.submit(task);
        Future<?> b = executor.submit(task);
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        a.get(30, TimeUnit.SECONDS);
        b.get(30, TimeUnit.SECONDS);
        executor.shutdownNow();
        if (unexpected.get() != null) {
            throw new AssertionError("Unexpected concurrent failure", unexpected.get());
        }
        assertEquals(1, created.get());
        assertEquals(1, retries.get());
        assertEquals(1, count("warehouse.transfer_document_payload"));
        assertEquals(
                MaterialRequirementStatus.SUBMITTED,
                requirements.findById(draft.requirementId()).orElseThrow().status());
    }

    @Test
    void corruptedSubmittedWithoutLinksFailsClosed() {
        seed(sourceA, cellA, materialA, "50");
        MaterialRequirement draft = persistDraft(line(materialA, "10"));
        service.submit(draft.requirementId(), 0L, "user-1");
        jdbc.update("DELETE FROM production.material_requirement_generated_documents");

        assertThrows(
                MaterialRequirementSubmissionCorruptedException.class,
                () -> service.submit(draft.requirementId(), 0L, "user-1"));
        assertEquals(1, count("warehouse.transfer_document_payload"));
    }

    @Test
    void partialAvailableStillCreatesFullDocumentQuantity() {
        seed(sourceA, cellA, materialA, "60");
        MaterialRequirement draft = persistDraft(line(materialA, "100"));

        SubmitMaterialRequirementResult result =
                service.submit(draft.requirementId(), 0L, "user-1");

        assertEquals(0, new BigDecimal("60").compareTo(result.routing().getFirst().routedQuantity()));
        assertEquals(0, new BigDecimal("40").compareTo(result.routing().getFirst().uncoveredQuantity()));
        assertEquals(
                0,
                new BigDecimal("100")
                        .compareTo(
                                jdbc.queryForObject(
                                        "SELECT quantity FROM warehouse.transfer_document_lines LIMIT 1",
                                        BigDecimal.class)));
    }

    private SubmitMaterialRequirementService newService(
            WarehouseTransferDocumentService transfers,
            MaterialRequirementSubmissionRepository submissionRepository) {
        WarehouseDemandCommandApi demand =
                new DefaultWarehouseDemandCommandApi(
                        new MaterialSourceRoutingService(new JdbcAvailableStockAggregationQuery(jdbc)),
                        transfers,
                        catalog,
                        materials,
                        new TransactionTemplate(txManager));
        return new SubmitMaterialRequirementService(
                requirements, submissionRepository, demand, txManager, CLOCK);
    }

    private MaterialRequirement persistDraft(MaterialRequirementLine... lines) {
        return requirements.save(
                MaterialRequirement.create(
                        SourceOrderId.generate(), destination.value(), T0, List.of(lines)));
    }

    private MaterialRequirementLine line(MaterialReference material, String qty) {
        return MaterialRequirementLine.create(
                MaterialReferenceId.of(material.id().value()),
                material.article(),
                material.article(),
                "",
                material.unitOfMeasure() == null || material.unitOfMeasure().isBlank()
                        ? "шт"
                        : material.unitOfMeasure(),
                new BigDecimal(qty),
                Set.of(SourceOrderItemId.generate()));
    }

    private void seed(
            WarehouseId warehouse, StorageCellId cell, MaterialReference material, String qty) {
        stockPositions.create(
                StockPosition.of(
                        warehouse, cell, material, StockState.AVAILABLE, StockQuantity.of(new BigDecimal(qty))));
    }

    private int count(String table) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }

    private BigDecimal stockSum() {
        BigDecimal sum =
                jdbc.queryForObject(
                        "SELECT COALESCE(SUM(quantity),0) FROM warehouse.stock_positions",
                        BigDecimal.class);
        return sum == null ? BigDecimal.ZERO : sum;
    }
}
