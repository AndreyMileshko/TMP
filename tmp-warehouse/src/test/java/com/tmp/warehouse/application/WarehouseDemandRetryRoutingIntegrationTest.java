package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.DocumentStatus;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import com.tmp.warehouse.api.WarehouseApi.ReceiveTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.SendTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentDestinationAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReceiveResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSourceAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentView;
import com.tmp.warehouse.api.WarehouseDemandCommandApi;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.AcceptProductionDemandCommand;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.AcceptProductionDemandResult;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.DemandLineRoutingOutcome;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.ProductionDemandLine;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.RetryDemandLineOutcome;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.RetryDemandLineResult;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.RetryDemandRoutingResult;
import com.tmp.warehouse.api.WarehouseDemandDerivedStatus;
import com.tmp.warehouse.api.WarehouseDemandQueryApi;
import com.tmp.warehouse.api.WarehouseDemandQueryApi.WarehouseDemandLineView;
import com.tmp.warehouse.api.WarehouseDemandQueryApi.WarehouseDemandView;
import com.tmp.warehouse.application.document.WarehouseTransferDocumentProcessor;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.MaterialReferenceId;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseDemand;
import com.tmp.warehouse.domain.WarehouseDemandId;
import com.tmp.warehouse.domain.WarehouseDemandLineId;
import com.tmp.warehouse.domain.WarehouseDemandTransferLink;
import com.tmp.warehouse.domain.WarehouseDemandWaitingReason;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseTransferLineId;
import com.tmp.warehouse.domain.repository.WarehouseDemandRepository;
import com.tmp.warehouse.persistence.JdbcAvailableStockAggregationQuery;
import com.tmp.warehouse.persistence.JdbcWarehouseDemandFulfillmentReadQuery;
import com.tmp.warehouse.persistence.JdbcWarehouseDemandRepository;
import com.tmp.warehouse.security.WarehousePermissions;
import com.tmp.warehouse.testsupport.WarehouseIntegrationTestSupport;
import com.tmp.warehouse.testsupport.WarehouseJdbcTestSupport;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * B3B-3B1: manual {@code retryDemandRouting} — WAITING lines only, remaining quantity, no
 * duplicate active Transfer.
 */
@Testcontainers
class WarehouseDemandRetryRoutingIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-06T09:00:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    private final AtomicReference<SessionSummary> session = new AtomicReference<>();
    private final AtomicReference<Set<PermissionId>> permissions = new AtomicReference<>(Set.of());

    private WarehouseIntegrationTestSupport.ApiBundle bundle;
    private DefaultWarehouseApi api;
    private WarehouseDemandCommandApi demandApi;
    private WarehouseDemandQueryApi demandQuery;
    private JdbcWarehouseDemandRepository demands;
    private TransactionTemplate txTemplate;
    private UUID worker;
    private WarehouseId destination;
    private WarehouseId source;
    private StorageCellId sourceCell;
    private StorageCellId destCell;
    private MaterialReference materialA;
    private MaterialReference materialB;
    private MaterialReference materialC;

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
    }

    @BeforeEach
    void setUp() {
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
        jdbc.update("DELETE FROM warehouse.warehouse_user_responsibility");
        jdbc.update("DELETE FROM warehouse.transfer_operation_context");
        jdbc.update("DELETE FROM warehouse.warehouse_movements");
        jdbc.update("DELETE FROM warehouse.warehouse_operations");
        jdbc.update("DELETE FROM warehouse.stock_positions");
        jdbc.update("DELETE FROM warehouse.storage_cells");
        jdbc.update("DELETE FROM warehouse.warehouses");
        jdbc.update("DELETE FROM warehouse.material_references");

        worker = UUID.randomUUID();
        session.set(sessionFor(worker));
        grantFullWarehousePermissions();

        bundle =
                WarehouseIntegrationTestSupport.createApiBundle(
                        dataSource,
                        CLOCK,
                        authorizationFromPermissions(),
                        authenticationFromSession(),
                        new DefaultWarehouseResponsibilityGuard(
                                authenticationFromSession(),
                                new com.tmp.warehouse.persistence
                                        .JdbcWarehouseUserResponsibilityRepository(jdbc, CLOCK)));
        api = bundle.api();
        if (bundle.documentEngine().registeredTypes().stream()
                .noneMatch(
                        t ->
                                WarehouseTransferDocumentProcessor.DOCUMENT_TYPE_ID.equals(
                                        t.typeId()))) {
            bundle.documentEngine()
                    .registerProcessor(
                            new WarehouseTransferDocumentProcessor(bundle.transferDocuments()));
        }
        demands = new JdbcWarehouseDemandRepository(jdbc);
        txTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        demandApi = newDemandApi(demands, bundle.transferDocumentService());
        demandQuery =
                new DefaultWarehouseDemandQueryApi(
                        demands, new JdbcWarehouseDemandFulfillmentReadQuery(jdbc));

        destination = WarehouseId.generate();
        source = WarehouseId.generate();
        bundle.catalog().save(Warehouse.create(destination, "DEST", "Destination"));
        bundle.catalog().save(Warehouse.create(source, "SRC", "Source"));
        sourceCell = StorageCellId.generate();
        destCell = StorageCellId.generate();
        bundle.catalog().save(StorageCell.create(sourceCell, source, "S-1"));
        bundle.catalog().save(StorageCell.create(destCell, destination, "D-1"));
        materialA =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc, CLOCK, MaterialReference.create("ART-A", "ART-A", "", "", "шт."));
        materialB =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc, CLOCK, MaterialReference.create("ART-B", "ART-B", "", "", "шт."));
        materialC =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc, CLOCK, MaterialReference.create("ART-C", "ART-C", "", "", "шт."));
        api.assignUserToWarehouse(source.value(), worker);
        api.assignUserToWarehouse(destination.value(), worker);
    }

    @Test
    void unknownThenResolvedRoutesRemaining() {
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(
                        command(
                                new ProductionDemandLine(
                                        UUID.randomUUID(),
                                        "MAT-NEW",
                                        "New material",
                                        "",
                                        "шт.",
                                        null,
                                        new BigDecimal("10"),
                                        null)));
        assertEquals(
                DemandLineRoutingOutcome.MATERIAL_UNMATCHED,
                accepted.lineOutcomes().getFirst().outcome());

        MaterialReference resolved =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc,
                        CLOCK,
                        MaterialReference.create("MAT-NEW", "New material", "", "", "шт."));
        seedAvailable(source, sourceCell, resolved, "100");

        RetryDemandRoutingResult retry = demandApi.retryDemandRouting(accepted.demandId());
        assertEquals(1, retry.documents().size());
        assertEquals(RetryDemandLineOutcome.ROUTED, retry.lineOutcomes().getFirst().outcome());
        assertEquals(resolved.id().value(), retry.lineOutcomes().getFirst().materialReferenceId());
        assertEquals(0, new BigDecimal("10").compareTo(retry.lineOutcomes().getFirst().routedQuantity()));

        WarehouseDemandLineView line =
                demandQuery.getDemand(accepted.demandId()).orElseThrow().lines().getFirst();
        assertEquals(WarehouseDemandDerivedStatus.IN_FULFILLMENT, line.derivedStatus());
        assertEquals(resolved.id().value(), line.materialReferenceId());
        assertNull(line.effectiveWaitingReason());
        assertEquals(1, line.linkedTransfers().size());
    }

    @Test
    void zeroStockThenStockCreatesTransferForRemaining() {
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(command(line(UUID.randomUUID(), materialA, "8")));
        assertEquals(
                DemandLineRoutingOutcome.NO_AVAILABLE_STOCK,
                accepted.lineOutcomes().getFirst().outcome());
        assertEquals(1, count("warehouse.warehouse_demands"));
        assertEquals(0, count("warehouse.transfer_document_payload"));

        seedAvailable(source, sourceCell, materialA, "50");
        RetryDemandRoutingResult retry = demandApi.retryDemandRouting(accepted.demandId());
        assertEquals(1, retry.documents().size());
        assertEquals(RetryDemandLineOutcome.ROUTED, retry.lineOutcomes().getFirst().outcome());
        assertEquals(0, new BigDecimal("8").compareTo(retry.lineOutcomes().getFirst().routedQuantity()));
        assertEquals(1, count("warehouse.warehouse_demands"));
        assertEquals(1, count("warehouse.transfer_document_payload"));
    }

    @Test
    void stillZeroStockKeepsWaitingWithoutException() {
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(command(line(UUID.randomUUID(), materialA, "5")));
        RetryDemandRoutingResult retry = demandApi.retryDemandRouting(accepted.demandId());
        assertTrue(retry.documents().isEmpty());
        assertEquals(
                RetryDemandLineOutcome.NO_AVAILABLE_STOCK, retry.lineOutcomes().getFirst().outcome());
        WarehouseDemandLineView line =
                demandQuery.getDemand(accepted.demandId()).orElseThrow().lines().getFirst();
        assertEquals(WarehouseDemandDerivedStatus.WAITING_FOR_SUPPLY, line.derivedStatus());
        assertEquals("NO_AVAILABLE_STOCK", line.effectiveWaitingReason());
        assertEquals(0, count("warehouse.transfer_document_payload"));
    }

    @Test
    void ambiguousThenResolvedRoutes() {
        MaterialReference amb1 =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc,
                        CLOCK,
                        MaterialReference.create("AMB-ART", "Amb A", "", "S1", "шт."));
        MaterialReference amb2 =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc,
                        CLOCK,
                        MaterialReference.create("AMB-ART", "Amb B", "", "S2", "шт."));
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(
                        command(
                                new ProductionDemandLine(
                                        UUID.randomUUID(),
                                        "AMB-ART",
                                        "Amb",
                                        "",
                                        "шт.",
                                        null,
                                        new BigDecimal("4"),
                                        null)));
        assertEquals(
                DemandLineRoutingOutcome.MATERIAL_AMBIGUOUS,
                accepted.lineOutcomes().getFirst().outcome());

        RetryDemandRoutingResult stillAmbiguous =
                demandApi.retryDemandRouting(accepted.demandId());
        assertEquals(
                RetryDemandLineOutcome.STILL_AMBIGUOUS,
                stillAmbiguous.lineOutcomes().getFirst().outcome());
        assertNull(stillAmbiguous.lineOutcomes().getFirst().materialReferenceId());
        assertTrue(stillAmbiguous.documents().isEmpty());

        jdbc.update("DELETE FROM warehouse.material_references WHERE id = ?", amb2.id().value());
        seedAvailable(source, sourceCell, amb1, "20");

        RetryDemandRoutingResult routed = demandApi.retryDemandRouting(accepted.demandId());
        assertEquals(RetryDemandLineOutcome.ROUTED, routed.lineOutcomes().getFirst().outcome());
        assertEquals(amb1.id().value(), routed.lineOutcomes().getFirst().materialReferenceId());
        assertEquals(1, routed.documents().size());
    }

    @Test
    void remainingQuantityAfterPartialReceiveRoutesSixNotTen() {
        seedAvailable(source, sourceCell, materialA, "100");
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(command(line(UUID.randomUUID(), materialA, "10")));
        UUID documentId = accepted.documents().getFirst().documentId();
        sendFull(documentId, "10");
        TransferDocumentReceiveResult partial = receivePartial(documentId, "4");
        closeContinuationAsTerminal(partial.continuationDocumentId());

        int linksBefore = count("warehouse.warehouse_demand_transfer_links");
        RetryDemandRoutingResult retry = demandApi.retryDemandRouting(accepted.demandId());
        assertEquals(1, retry.documents().size());
        assertEquals(RetryDemandLineOutcome.ROUTED, retry.lineOutcomes().getFirst().outcome());
        assertEquals(0, new BigDecimal("6").compareTo(retry.lineOutcomes().getFirst().routedQuantity()));
        assertEquals(
                0,
                new BigDecimal("6")
                        .compareTo(documentLineQuantity(retry.documents().getFirst().documentId())));
        assertEquals(linksBefore + 1, count("warehouse.warehouse_demand_transfer_links"));
        BigDecimal linkedQty =
                jdbc.queryForObject(
                        """
                        SELECT linked_quantity FROM warehouse.warehouse_demand_transfer_links
                         WHERE transfer_document_id = ?
                        """,
                        BigDecimal.class,
                        retry.documents().getFirst().documentId());
        assertEquals(0, new BigDecimal("6").compareTo(linkedQty));
    }

    @Test
    void activeContinuationBlocksDuplicateRetry() {
        seedAvailable(source, sourceCell, materialA, "100");
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(command(line(UUID.randomUUID(), materialA, "10")));
        UUID documentId = accepted.documents().getFirst().documentId();
        sendFull(documentId, "10");
        TransferDocumentReceiveResult partial = receivePartial(documentId, "4");
        assertTrue(partial.continuationDocumentId() != null);

        int docsBefore = count("warehouse.transfer_document_payload");
        int linksBefore = count("warehouse.warehouse_demand_transfer_links");
        RetryDemandRoutingResult retry = demandApi.retryDemandRouting(accepted.demandId());
        assertTrue(retry.documents().isEmpty());
        assertEquals(
                RetryDemandLineOutcome.SKIPPED_IN_FULFILLMENT,
                retry.lineOutcomes().getFirst().outcome());
        assertEquals(docsBefore, count("warehouse.transfer_document_payload"));
        assertEquals(linksBefore, count("warehouse.warehouse_demand_transfer_links"));
        WarehouseDemandLineView line =
                demandQuery.getDemand(accepted.demandId()).orElseThrow().lines().getFirst();
        assertEquals(WarehouseDemandDerivedStatus.IN_FULFILLMENT, line.derivedStatus());
        assertTrue(
                line.linkedTransfers().stream()
                        .anyMatch(
                                l ->
                                        l.transferDocumentId()
                                                .equals(partial.continuationDocumentId())));
    }

    @Test
    void fulfilledDemandCreatesNothing() {
        seedAvailable(source, sourceCell, materialA, "100");
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(command(line(UUID.randomUUID(), materialA, "10")));
        UUID documentId = accepted.documents().getFirst().documentId();
        sendFull(documentId, "10");
        receiveFull(documentId, "10");

        int docsBefore = count("warehouse.transfer_document_payload");
        int linksBefore = count("warehouse.warehouse_demand_transfer_links");
        RetryDemandRoutingResult retry = demandApi.retryDemandRouting(accepted.demandId());
        assertTrue(retry.documents().isEmpty());
        assertEquals(
                RetryDemandLineOutcome.SKIPPED_FULFILLED, retry.lineOutcomes().getFirst().outcome());
        assertEquals(docsBefore, count("warehouse.transfer_document_payload"));
        assertEquals(linksBefore, count("warehouse.warehouse_demand_transfer_links"));
        assertEquals(
                WarehouseDemandDerivedStatus.FULFILLED,
                demandQuery.getDemand(accepted.demandId()).orElseThrow().derivedStatus());
    }

    @Test
    void cancelledDemandDoesNotRouteOrReopen() {
        seedAvailable(source, sourceCell, materialA, "100");
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(command(line(UUID.randomUUID(), materialA, "10")));
        jdbc.update(
                """
                UPDATE warehouse.warehouse_demands
                   SET cancelled_at = ?, cancelled_by = ?
                 WHERE id = ?
                """,
                java.sql.Timestamp.from(CLOCK.instant()),
                "tester",
                accepted.demandId());

        int docsBefore = count("warehouse.transfer_document_payload");
        RetryDemandRoutingResult retry = demandApi.retryDemandRouting(accepted.demandId());
        assertTrue(retry.documents().isEmpty());
        assertEquals(
                RetryDemandLineOutcome.SKIPPED_CANCELLED, retry.lineOutcomes().getFirst().outcome());
        assertEquals(docsBefore, count("warehouse.transfer_document_payload"));
        WarehouseDemandView view = demandQuery.getDemand(accepted.demandId()).orElseThrow();
        assertEquals(WarehouseDemandDerivedStatus.CANCELLED, view.derivedStatus());
        assertTrue(view.cancelledAt() != null);
    }

    @Test
    void mixedDemandRoutesOnlyEligibleWaitingLines() {
        // A WAITING unmatched → later resolved + stock → ROUTED
        // B WAITING unique material, still zero stock → NO_AVAILABLE_STOCK
        // C WAITING unmatched → STILL_UNMATCHED
        // D IN_FULFILLMENT (active DRAFT) → SKIPPED_IN_FULFILLMENT
        // E FULFILLED → SKIPPED_FULFILLED
        // Separate source for D so E gets a single-line Transfer (send/receive helpers are single-line).
        WarehouseId sourceD = WarehouseId.generate();
        StorageCellId cellD = StorageCellId.generate();
        bundle.catalog().save(Warehouse.create(sourceD, "SRC-D", "Source D"));
        bundle.catalog().save(StorageCell.create(cellD, sourceD, "D-1"));
        api.assignUserToWarehouse(sourceD.value(), worker);
        seedAvailable(sourceD, cellD, materialC, "100");
        seedAvailable(source, sourceCell, materialA, "100");
        UUID lineA = UUID.randomUUID();
        UUID lineB = UUID.randomUUID();
        UUID lineC = UUID.randomUUID();
        UUID lineD = UUID.randomUUID();
        UUID lineE = UUID.randomUUID();

        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(
                        command(
                                new ProductionDemandLine(
                                        lineA,
                                        "NOW-OK",
                                        "Now OK",
                                        "",
                                        "шт.",
                                        null,
                                        new BigDecimal("5"),
                                        null),
                                line(lineB, materialB, "5"),
                                new ProductionDemandLine(
                                        lineC,
                                        "UNKNOWN-Z",
                                        "Unknown",
                                        "",
                                        "шт.",
                                        null,
                                        new BigDecimal("3"),
                                        null),
                                line(lineD, materialC, "7"),
                                line(lineE, materialA, "4")));

        UUID docE =
                accepted.lineOutcomes().stream()
                        .filter(o -> o.sourceMaterialRequirementLineId().equals(lineE))
                        .findFirst()
                        .orElseThrow()
                        .warehouseDocumentId();
        sendFull(docE, "4");
        receiveFull(docE, "4");

        MaterialReference nowOk =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc,
                        CLOCK,
                        MaterialReference.create("NOW-OK", "Now OK", "", "", "шт."));
        seedAvailable(source, sourceCell, nowOk, "50");

        int docsBefore = count("warehouse.transfer_document_payload");
        RetryDemandRoutingResult retry = demandApi.retryDemandRouting(accepted.demandId());

        assertEquals(RetryDemandLineOutcome.ROUTED, bySource(retry, lineA).outcome());
        assertEquals(RetryDemandLineOutcome.NO_AVAILABLE_STOCK, bySource(retry, lineB).outcome());
        assertEquals(RetryDemandLineOutcome.STILL_UNMATCHED, bySource(retry, lineC).outcome());
        assertEquals(
                RetryDemandLineOutcome.SKIPPED_IN_FULFILLMENT, bySource(retry, lineD).outcome());
        assertEquals(RetryDemandLineOutcome.SKIPPED_FULFILLED, bySource(retry, lineE).outcome());
        assertEquals(1, retry.documents().size());
        assertEquals(docsBefore + 1, count("warehouse.transfer_document_payload"));
        assertEquals(0, new BigDecimal("5").compareTo(bySource(retry, lineA).routedQuantity()));
        assertEquals(nowOk.id().value(), bySource(retry, lineA).materialReferenceId());
        assertNull(bySource(retry, lineC).materialReferenceId());
    }

    @Test
    void sequentialRetryDoesNotDuplicateTransfer() {
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(command(line(UUID.randomUUID(), materialA, "6")));
        seedAvailable(source, sourceCell, materialA, "40");

        RetryDemandRoutingResult first = demandApi.retryDemandRouting(accepted.demandId());
        assertEquals(1, first.documents().size());
        int docsAfterFirst = count("warehouse.transfer_document_payload");
        int linksAfterFirst = count("warehouse.warehouse_demand_transfer_links");

        RetryDemandRoutingResult second = demandApi.retryDemandRouting(accepted.demandId());
        assertTrue(second.documents().isEmpty());
        assertEquals(
                RetryDemandLineOutcome.SKIPPED_IN_FULFILLMENT,
                second.lineOutcomes().getFirst().outcome());
        assertEquals(docsAfterFirst, count("warehouse.transfer_document_payload"));
        assertEquals(linksAfterFirst, count("warehouse.warehouse_demand_transfer_links"));
    }

    @Test
    void concurrentRetryCreatesExactlyOneTransferSet() throws Exception {
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(command(line(UUID.randomUUID(), materialA, "9")));
        seedAvailable(source, sourceCell, materialA, "50");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger routedDocs = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> f1 =
                    pool.submit(
                            () -> {
                                ready.countDown();
                                start.await(10, TimeUnit.SECONDS);
                                RetryDemandRoutingResult r =
                                        demandApi.retryDemandRouting(accepted.demandId());
                                successes.incrementAndGet();
                                routedDocs.addAndGet(r.documents().size());
                                return null;
                            });
            Future<?> f2 =
                    pool.submit(
                            () -> {
                                ready.countDown();
                                start.await(10, TimeUnit.SECONDS);
                                RetryDemandRoutingResult r =
                                        demandApi.retryDemandRouting(accepted.demandId());
                                successes.incrementAndGet();
                                routedDocs.addAndGet(r.documents().size());
                                return null;
                            });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(2, successes.get());
        assertEquals(1, routedDocs.get());
        assertEquals(1, count("warehouse.transfer_document_payload"));
        assertEquals(1, count("warehouse.warehouse_demand_transfer_links"));
    }

    @Test
    void technicalFailureRollsBackTransferLinkAndResolution() {
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(command(line(UUID.randomUUID(), materialA, "5")));
        seedAvailable(source, sourceCell, materialA, "30");

        WarehouseDemandRepository failing =
                new FailingLinkRepository(demands, new AtomicInteger(1));
        WarehouseDemandCommandApi failingApi =
                newDemandApi(failing, bundle.transferDocumentService());

        assertThrows(
                IllegalStateException.class,
                () -> failingApi.retryDemandRouting(accepted.demandId()));

        assertEquals(0, count("warehouse.transfer_document_payload"));
        assertEquals(0, count("warehouse.warehouse_demand_transfer_links"));
        WarehouseDemandLineView line =
                demandQuery.getDemand(accepted.demandId()).orElseThrow().lines().getFirst();
        assertEquals(WarehouseDemandDerivedStatus.WAITING_FOR_SUPPLY, line.derivedStatus());
        assertEquals("NO_AVAILABLE_STOCK", line.effectiveWaitingReason());
        assertEquals(materialA.id().value(), line.materialReferenceId());
    }

    @Test
    void terminalHistoricalLinkDoesNotBlockRetry() {
        seedAvailable(source, sourceCell, materialA, "100");
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(command(line(UUID.randomUUID(), materialA, "10")));
        UUID documentId = accepted.documents().getFirst().documentId();
        sendFull(documentId, "10");
        api.takeTransferTaskInWork(documentId);
        api.rejectTransferDocument(
                new com.tmp.warehouse.api.WarehouseApi.RejectTransferDocumentCommand(
                        documentId, 0L, "damaged"));

        WarehouseDemandLineView before =
                demandQuery.getDemand(accepted.demandId()).orElseThrow().lines().getFirst();
        assertEquals(WarehouseDemandDerivedStatus.WAITING_FOR_SUPPLY, before.derivedStatus());
        assertEquals(1, before.linkedTransfers().size());
        assertEquals(0, before.receivedQuantity().compareTo(BigDecimal.ZERO));

        RetryDemandRoutingResult retry = demandApi.retryDemandRouting(accepted.demandId());
        assertEquals(1, retry.documents().size());
        assertEquals(RetryDemandLineOutcome.ROUTED, retry.lineOutcomes().getFirst().outcome());
        assertEquals(0, new BigDecimal("10").compareTo(retry.lineOutcomes().getFirst().routedQuantity()));
        assertEquals(2, count("warehouse.warehouse_demand_transfer_links"));
    }

    @Test
    void unknownDemandRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> demandApi.retryDemandRouting(UUID.randomUUID()));
    }

    private WarehouseDemandCommandApi newDemandApi(
            WarehouseDemandRepository repository,
            WarehouseTransferDocumentService transferDocuments) {
        return new DefaultWarehouseDemandCommandApi(
                new MaterialSourceRoutingService(new JdbcAvailableStockAggregationQuery(jdbc)),
                transferDocuments,
                bundle.catalog(),
                bundle.materials(),
                repository,
                bundle.transferDocuments(),
                new JdbcWarehouseDemandFulfillmentReadQuery(jdbc),
                CLOCK,
                txTemplate);
    }

    private void closeContinuationAsTerminal(UUID continuationDocumentId) {
        jdbc.update(
                "DELETE FROM warehouse.warehouse_demand_transfer_links WHERE transfer_document_id = ?",
                continuationDocumentId);
        jdbc.update(
                "UPDATE documents.documents SET status = 'CLOSED' WHERE id = ?",
                continuationDocumentId);
    }

    private void sendFull(UUID documentId, String qty) {
        TransferDocumentView loaded = api.getTransferDocument(documentId);
        if (DocumentStatus.DRAFT.name().equals(loaded.documentStatus())) {
            api.takeTransferTaskInWork(documentId);
            api.sendTransferDocument(
                    new SendTransferDocumentCommand(
                            documentId,
                            bundle.documentEngine().findById(documentId).orElseThrow().version(),
                            loaded.payloadRevision(),
                            List.of(
                                    new TransferDocumentSourceAllocationInput(
                                            loaded.lines().getFirst().lineId(),
                                            sourceCell.value(),
                                            new BigDecimal(qty)))));
        }
    }

    private TransferDocumentReceiveResult receivePartial(UUID documentId, String qty) {
        TransferDocumentView loaded = api.getTransferDocument(documentId);
        api.takeTransferTaskInWork(documentId);
        return api.receiveTransferDocument(
                new ReceiveTransferDocumentCommand(
                        documentId,
                        loaded.operationalRevision(),
                        List.of(
                                new TransferDocumentDestinationAllocationInput(
                                        loaded.lines().getFirst().lineId(),
                                        destCell.value(),
                                        new BigDecimal(qty)))));
    }

    private TransferDocumentReceiveResult receiveFull(UUID documentId, String qty) {
        return receivePartial(documentId, qty);
    }

    private AcceptProductionDemandCommand command(ProductionDemandLine... lines) {
        return new AcceptProductionDemandCommand(
                UUID.randomUUID(), destination.value(), "system", List.of(lines));
    }

    private ProductionDemandLine line(UUID sourceLineId, MaterialReference material, String qty) {
        return new ProductionDemandLine(
                sourceLineId,
                material.article(),
                material.name(),
                material.color(),
                material.unitOfMeasure(),
                null,
                new BigDecimal(qty),
                material.id().value());
    }

    private void seedAvailable(
            WarehouseId warehouse, StorageCellId cell, MaterialReference material, String qty) {
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                warehouse,
                                cell,
                                material,
                                StockState.AVAILABLE,
                                StockQuantity.of(new BigDecimal(qty))));
    }

    private BigDecimal documentLineQuantity(UUID documentId) {
        return jdbc.queryForObject(
                """
                SELECT quantity FROM warehouse.transfer_document_lines
                WHERE document_id = ?
                ORDER BY line_order
                LIMIT 1
                """,
                BigDecimal.class,
                documentId);
    }

    private static RetryDemandLineResult bySource(RetryDemandRoutingResult retry, UUID sourceLineId) {
        return retry.lineOutcomes().stream()
                .filter(o -> o.sourceMaterialRequirementLineId().equals(sourceLineId))
                .findFirst()
                .orElseThrow();
    }

    private int count(String table) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }

    private void grantFullWarehousePermissions() {
        permissions.set(
                Set.of(
                        WarehousePermissions.WAREHOUSE_TRANSFER,
                        WarehousePermissions.WAREHOUSE_VIEW,
                        WarehousePermissions.WAREHOUSE_STRUCTURE_CREATE,
                        WarehousePermissions.WAREHOUSE_STRUCTURE_UPDATE,
                        WarehousePermissions.WAREHOUSE_STRUCTURE_VIEW,
                        WarehousePermissions.STORAGE_CELL_CREATE));
    }

    private SessionSummary sessionFor(UUID userId) {
        return new SessionSummary(
                SessionId.of(UUID.randomUUID()),
                UserId.of(userId),
                Login.of("user-" + userId.toString().substring(0, 8)),
                Instant.parse("2026-10-06T09:00:00Z"));
    }

    private AuthenticationService authenticationFromSession() {
        return new AuthenticationService() {
            @Override
            public SessionSummary login(Login login, char[] password) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SessionSummary completePasswordSetup(
                    Login login, String activationCode, char[] newPassword, char[] confirmPassword) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void logout() {}

            @Override
            public Optional<SessionSummary> currentSession() {
                return Optional.ofNullable(session.get());
            }

            @Override
            public boolean isAuthenticated() {
                return session.get() != null;
            }
        };
    }

    private AuthorizationService authorizationFromPermissions() {
        return new AuthorizationService() {
            @Override
            public boolean hasPermission(PermissionId permissionId) {
                return permissions.get().contains(permissionId);
            }

            @Override
            public void requirePermission(PermissionId permissionId) {
                if (!hasPermission(permissionId)) {
                    throw new AccessDeniedException("Access denied: " + permissionId.value());
                }
            }

            @Override
            public Set<PermissionId> effectivePermissions() {
                return permissions.get();
            }
        };
    }

    /**
     * Delegates to JDBC repository; throws once on {@link #insertTransferLink} to force retry TX
     * rollback after Transfer DRAFT creation attempt.
     */
    private static final class FailingLinkRepository implements WarehouseDemandRepository {
        private final WarehouseDemandRepository delegate;
        private final AtomicInteger failRemaining;

        private FailingLinkRepository(WarehouseDemandRepository delegate, AtomicInteger failRemaining) {
            this.delegate = delegate;
            this.failRemaining = failRemaining;
        }

        @Override
        public void insert(WarehouseDemand demand) {
            delegate.insert(demand);
        }

        @Override
        public Optional<WarehouseDemand> findById(WarehouseDemandId demandId) {
            return delegate.findById(demandId);
        }

        @Override
        public Optional<WarehouseDemand> findBySourceMaterialRequirementId(
                UUID sourceMaterialRequirementId) {
            return delegate.findBySourceMaterialRequirementId(sourceMaterialRequirementId);
        }

        @Override
        public Optional<WarehouseDemand> lockById(WarehouseDemandId demandId) {
            return delegate.lockById(demandId);
        }

        @Override
        public void insertTransferLink(WarehouseDemandTransferLink link) {
            if (failRemaining.getAndDecrement() > 0) {
                throw new IllegalStateException("injected link persistence failure");
            }
            delegate.insertTransferLink(link);
        }

        @Override
        public boolean insertTransferLinkIfAbsent(WarehouseDemandTransferLink link) {
            return delegate.insertTransferLinkIfAbsent(link);
        }

        @Override
        public List<WarehouseDemandTransferLink> findTransferLinksByDemandLineId(
                WarehouseDemandLineId demandLineId) {
            return delegate.findTransferLinksByDemandLineId(demandLineId);
        }

        @Override
        public List<WarehouseDemandTransferLink> findTransferLinksByDemandId(
                WarehouseDemandId demandId) {
            return delegate.findTransferLinksByDemandId(demandId);
        }

        @Override
        public Optional<WarehouseDemandTransferLink> findTransferLinkByTransferLineId(
                WarehouseTransferLineId transferLineId) {
            return delegate.findTransferLinkByTransferLineId(transferLineId);
        }

        @Override
        public void updateLineOperationalResolution(
                WarehouseDemandLineId demandLineId,
                MaterialReferenceId materialReferenceId,
                WarehouseDemandWaitingReason waitingReason) {
            delegate.updateLineOperationalResolution(
                    demandLineId, materialReferenceId, waitingReason);
        }
    }
}
