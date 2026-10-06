package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.tmp.warehouse.api.WarehouseApi.CreateTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.ReceiveTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.RejectTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.ReturnTransferMaterialsCommand;
import com.tmp.warehouse.api.WarehouseApi.SendTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentDestinationAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentLineInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReceiveResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSourceAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentView;
import com.tmp.warehouse.api.WarehouseDemandCommandApi;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.AcceptProductionDemandCommand;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.AcceptProductionDemandResult;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.DemandLineRoutingOutcome;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.ProductionDemandLine;
import com.tmp.warehouse.api.WarehouseDemandDerivedStatus;
import com.tmp.warehouse.api.WarehouseDemandQueryApi;
import com.tmp.warehouse.api.WarehouseDemandQueryApi.WarehouseDemandLineView;
import com.tmp.warehouse.api.WarehouseDemandQueryApi.WarehouseDemandView;
import com.tmp.warehouse.application.document.WarehouseTransferDocumentProcessor;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.TransferSettlementState;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
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
 * B3B-3A: derived Demand fulfillment from settled Transfer receipt facts + continuation lineage.
 */
@Testcontainers
class WarehouseDemandFulfillmentQueryIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-05T10:00:00Z"), ZoneOffset.UTC);

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
        JdbcWarehouseDemandRepository demands = new JdbcWarehouseDemandRepository(jdbc);
        demandApi =
                new DefaultWarehouseDemandCommandApi(
                        new MaterialSourceRoutingService(
                                new JdbcAvailableStockAggregationQuery(jdbc)),
                        bundle.transferDocumentService(),
                        bundle.catalog(),
                        bundle.materials(),
                        demands,
                        bundle.transferDocuments(),
                        new JdbcWarehouseDemandFulfillmentReadQuery(jdbc),
                        CLOCK,
                        new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
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
    void zeroStockWaitingQuery() {
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(command(line(UUID.randomUUID(), materialA, "10")));
        WarehouseDemandView view = demandQuery.getDemand(accepted.demandId()).orElseThrow();
        WarehouseDemandLineView line = view.lines().getFirst();
        assertEquals(WarehouseDemandDerivedStatus.WAITING_FOR_SUPPLY, view.derivedStatus());
        assertEquals(WarehouseDemandDerivedStatus.WAITING_FOR_SUPPLY, line.derivedStatus());
        assertEquals(0, line.receivedQuantity().compareTo(BigDecimal.ZERO));
        assertEquals(0, line.remainingQuantity().compareTo(new BigDecimal("10")));
        assertEquals("NO_AVAILABLE_STOCK", line.effectiveWaitingReason());
        assertTrue(line.linkedTransfers().isEmpty());
    }

    @Test
    void unmatchedMaterialWaitingQuery() {
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(
                        command(
                                new ProductionDemandLine(
                                        UUID.randomUUID(),
                                        "UNKNOWN-ART",
                                        "Unknown",
                                        "",
                                        "шт.",
                                        null,
                                        new BigDecimal("7"),
                                        null)));
        WarehouseDemandLineView line =
                demandQuery.getDemand(accepted.demandId()).orElseThrow().lines().getFirst();
        assertEquals(WarehouseDemandDerivedStatus.WAITING_FOR_SUPPLY, line.derivedStatus());
        assertEquals("MATERIAL_UNMATCHED", line.effectiveWaitingReason());
        assertNull(line.materialReferenceId());
        assertEquals(0, line.receivedQuantity().compareTo(BigDecimal.ZERO));
        assertEquals(0, line.remainingQuantity().compareTo(new BigDecimal("7")));
    }

    @Test
    void activeDraftTransferIsInFulfillmentWithZeroReceived() {
        seedAvailable(source, sourceCell, materialA, "100");
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(command(line(UUID.randomUUID(), materialA, "10")));
        WarehouseDemandView view = demandQuery.getDemand(accepted.demandId()).orElseThrow();
        WarehouseDemandLineView line = view.lines().getFirst();
        assertEquals(WarehouseDemandDerivedStatus.IN_FULFILLMENT, view.derivedStatus());
        assertEquals(WarehouseDemandDerivedStatus.IN_FULFILLMENT, line.derivedStatus());
        assertNull(line.effectiveWaitingReason());
        assertEquals(0, line.receivedQuantity().compareTo(BigDecimal.ZERO));
        assertEquals(0, line.remainingQuantity().compareTo(new BigDecimal("10")));
        assertEquals(1, line.linkedTransfers().size());
    }

    @Test
    void partialReceiveCreatesContinuationLinkAndInFulfillment() {
        seedAvailable(source, sourceCell, materialA, "100");
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(command(line(UUID.randomUUID(), materialA, "10")));
        UUID documentId = accepted.documents().getFirst().documentId();
        sendFull(documentId, "10");
        TransferDocumentReceiveResult received = receivePartial(documentId, "4");

        assertNotNull(received.continuationDocumentId());
        WarehouseDemandView view = demandQuery.getDemand(accepted.demandId()).orElseThrow();
        WarehouseDemandLineView line = view.lines().getFirst();
        assertEquals(0, line.receivedQuantity().compareTo(new BigDecimal("4")));
        assertEquals(0, line.remainingQuantity().compareTo(new BigDecimal("6")));
        assertEquals(WarehouseDemandDerivedStatus.IN_FULFILLMENT, line.derivedStatus());
        assertEquals(2, line.linkedTransfers().size());
        assertTrue(
                line.linkedTransfers().stream()
                        .anyMatch(l -> l.transferDocumentId().equals(documentId)));
        assertTrue(
                line.linkedTransfers().stream()
                        .anyMatch(
                                l ->
                                        l.transferDocumentId()
                                                .equals(received.continuationDocumentId())));
        TransferDocumentView continuation =
                api.getTransferDocument(received.continuationDocumentId());
        assertEquals(DocumentStatus.DRAFT.name(), continuation.documentStatus());
        assertEquals(0, continuation.lines().getFirst().quantity().compareTo(new BigDecimal("6")));
    }

    @Test
    void partialReceiveWithoutActiveContinuationIsWaitingWithFallbackReason() {
        seedAvailable(source, sourceCell, materialA, "100");
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(command(line(UUID.randomUUID(), materialA, "10")));
        UUID documentId = accepted.documents().getFirst().documentId();
        sendFull(documentId, "10");
        TransferDocumentReceiveResult received = receivePartial(documentId, "4");
        // Cancel continuation DRAFT by deleting links + document simulation: clear continuation
        // Demand link and close continuation document to leave no active Transfer.
        jdbc.update(
                "DELETE FROM warehouse.warehouse_demand_transfer_links WHERE transfer_document_id = ?",
                received.continuationDocumentId());
        jdbc.update(
                "UPDATE documents.documents SET status = 'CLOSED' WHERE id = ?",
                received.continuationDocumentId());

        WarehouseDemandLineView line =
                demandQuery.getDemand(accepted.demandId()).orElseThrow().lines().getFirst();
        assertEquals(0, line.receivedQuantity().compareTo(new BigDecimal("4")));
        assertEquals(0, line.remainingQuantity().compareTo(new BigDecimal("6")));
        assertEquals(WarehouseDemandDerivedStatus.WAITING_FOR_SUPPLY, line.derivedStatus());
        assertEquals("ROUTING_DEFERRED", line.effectiveWaitingReason());
    }

    @Test
    void finalContinuationReceiveFulfillsDemand() {
        seedAvailable(source, sourceCell, materialA, "100");
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(command(line(UUID.randomUUID(), materialA, "10")));
        UUID documentId = accepted.documents().getFirst().documentId();
        sendFull(documentId, "10");
        TransferDocumentReceiveResult partial = receivePartial(documentId, "4");
        sendFull(partial.continuationDocumentId(), "6");
        receiveFull(partial.continuationDocumentId(), "6");

        WarehouseDemandView view = demandQuery.getDemand(accepted.demandId()).orElseThrow();
        WarehouseDemandLineView line = view.lines().getFirst();
        assertEquals(0, line.receivedQuantity().compareTo(new BigDecimal("10")));
        assertEquals(0, line.remainingQuantity().compareTo(BigDecimal.ZERO));
        assertEquals(WarehouseDemandDerivedStatus.FULFILLED, line.derivedStatus());
        assertEquals(WarehouseDemandDerivedStatus.FULFILLED, view.derivedStatus());
        assertNull(line.effectiveWaitingReason());
    }

    @Test
    void duplicateReceiveDoesNotDoubleCount() {
        seedAvailable(source, sourceCell, materialA, "100");
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(command(line(UUID.randomUUID(), materialA, "10")));
        UUID documentId = accepted.documents().getFirst().documentId();
        sendFull(documentId, "10");
        receiveFull(documentId, "10");
        BigDecimal before =
                demandQuery
                        .getDemand(accepted.demandId())
                        .orElseThrow()
                        .lines()
                        .getFirst()
                        .receivedQuantity();
        assertThrows(RuntimeException.class, () -> receiveFull(documentId, "10"));
        BigDecimal after =
                demandQuery
                        .getDemand(accepted.demandId())
                        .orElseThrow()
                        .lines()
                        .getFirst()
                        .receivedQuantity();
        assertEquals(0, before.compareTo(after));
        assertEquals(0, after.compareTo(new BigDecimal("10")));
    }

    @Test
    void mixedLinesAggregateHeaderToInFulfillment() {
        WarehouseId sourceB = WarehouseId.generate();
        StorageCellId cellSourceB = StorageCellId.generate();
        bundle.catalog().save(Warehouse.create(sourceB, "SRC-B", "Source B"));
        bundle.catalog().save(StorageCell.create(cellSourceB, sourceB, "B-1"));
        api.assignUserToWarehouse(sourceB.value(), worker);

        seedAvailable(source, sourceCell, materialA, "100");
        seedAvailable(sourceB, cellSourceB, materialC, "100");
        UUID lineA = UUID.randomUUID();
        UUID lineB = UUID.randomUUID();
        UUID lineC = UUID.randomUUID();
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(
                        command(
                                line(lineA, materialA, "10"),
                                new ProductionDemandLine(
                                        lineB,
                                        "UNKNOWN-X",
                                        "Unknown",
                                        "",
                                        "шт.",
                                        null,
                                        new BigDecimal("5"),
                                        null),
                                line(lineC, materialC, "8")));

        UUID routedA =
                accepted.lineOutcomes().stream()
                        .filter(o -> o.sourceMaterialRequirementLineId().equals(lineA))
                        .findFirst()
                        .orElseThrow()
                        .warehouseDocumentId();
        sendFull(routedA, "10");
        receiveFull(routedA, "10");

        WarehouseDemandView view = demandQuery.getDemand(accepted.demandId()).orElseThrow();
        assertEquals(WarehouseDemandDerivedStatus.IN_FULFILLMENT, view.derivedStatus());
        assertEquals(
                WarehouseDemandDerivedStatus.FULFILLED,
                lineBySource(view, lineA).derivedStatus());
        assertEquals(
                WarehouseDemandDerivedStatus.WAITING_FOR_SUPPLY,
                lineBySource(view, lineB).derivedStatus());
        assertEquals(
                WarehouseDemandDerivedStatus.IN_FULFILLMENT,
                lineBySource(view, lineC).derivedStatus());
        assertFalse(
                jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='warehouse' AND table_name='warehouse_demands' AND column_name='status')",
                        Boolean.class));
    }

    @Test
    void cancelMetadataMakesDerivedCancelled() {
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
        WarehouseDemandView view = demandQuery.getDemand(accepted.demandId()).orElseThrow();
        assertEquals(WarehouseDemandDerivedStatus.CANCELLED, view.derivedStatus());
        assertEquals(
                WarehouseDemandDerivedStatus.CANCELLED, view.lines().getFirst().derivedStatus());
    }

    @Test
    void rejectDoesNotCountAsReceive() {
        seedAvailable(source, sourceCell, materialA, "100");
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(command(line(UUID.randomUUID(), materialA, "10")));
        UUID documentId = accepted.documents().getFirst().documentId();
        sendFull(documentId, "10");
        api.takeTransferTaskInWork(documentId);
        api.rejectTransferDocument(
                new RejectTransferDocumentCommand(documentId, 0L, "damaged packaging"));
        WarehouseDemandLineView line =
                demandQuery.getDemand(accepted.demandId()).orElseThrow().lines().getFirst();
        assertEquals(0, line.receivedQuantity().compareTo(BigDecimal.ZERO));
        assertEquals(WarehouseDemandDerivedStatus.WAITING_FOR_SUPPLY, line.derivedStatus());
        assertEquals("ROUTING_DEFERRED", line.effectiveWaitingReason());
    }

    @Test
    void receiveThenReturnDoesNotChangeHistoricalDemandFulfillment() {
        seedAvailable(source, sourceCell, materialA, "100");
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(command(line(UUID.randomUUID(), materialA, "10")));
        UUID documentId = accepted.documents().getFirst().documentId();
        sendFull(documentId, "10");
        receivePartial(documentId, "4");
        // Return outstanding IN_TRANSIT for RETURN_PENDING parent — Demand received stays 4.
        api.returnTransferMaterials(
                new ReturnTransferMaterialsCommand(documentId, 1L, List.of()));
        WarehouseDemandLineView line =
                demandQuery.getDemand(accepted.demandId()).orElseThrow().lines().getFirst();
        assertEquals(0, line.receivedQuantity().compareTo(new BigDecimal("4")));
        assertEquals(WarehouseDemandDerivedStatus.IN_FULFILLMENT, line.derivedStatus());
    }

    @Test
    void shortfallContinuationPreservesDemandLineage() {
        seedAvailable(source, sourceCell, materialA, "60");
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(command(line(UUID.randomUUID(), materialA, "100")));
        UUID documentId = accepted.documents().getFirst().documentId();
        TransferDocumentView loaded = api.getTransferDocument(documentId);
        api.takeTransferTaskInWork(documentId);
        var sent =
                api.sendTransferDocument(
                        new SendTransferDocumentCommand(
                                documentId,
                                bundle.documentEngine().findById(documentId).orElseThrow().version(),
                                loaded.payloadRevision(),
                                List.of(
                                        new TransferDocumentSourceAllocationInput(
                                                loaded.lines().getFirst().lineId(),
                                                sourceCell.value(),
                                                new BigDecimal("60")))));
        assertNotNull(sent.continuationDocumentId());
        WarehouseDemandLineView line =
                demandQuery.getDemand(accepted.demandId()).orElseThrow().lines().getFirst();
        assertEquals(2, line.linkedTransfers().size());
        assertTrue(
                line.linkedTransfers().stream()
                        .anyMatch(
                                l ->
                                        l.transferDocumentId()
                                                .equals(sent.continuationDocumentId())));
        assertEquals(WarehouseDemandDerivedStatus.IN_FULFILLMENT, line.derivedStatus());
    }

    @Test
    void legacyTransferWithoutDemandLinkUnchangedAndInvisibleToDemandQuery() {
        seedAvailable(source, sourceCell, materialA, "100");
        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                source.value(),
                                destination.value(),
                                List.of(
                                        new TransferDocumentLineInput(
                                                null,
                                                materialA.id().value(),
                                                new BigDecimal("10"),
                                                1))));
        sendFull(created.documentId(), "10");
        receiveFull(created.documentId(), "10");
        assertEquals(
                TransferSettlementState.SETTLED.name(),
                api.getTransferDocument(created.documentId()).settlementState());
        assertEquals(0, count("warehouse.warehouse_demand_transfer_links"));
        assertTrue(demandQuery.getDemand(UUID.randomUUID()).isEmpty());
    }

    @Test
    void getDemandBySourceMaterialRequirementId() {
        seedAvailable(source, sourceCell, materialA, "100");
        UUID sourceMrId = UUID.randomUUID();
        AcceptProductionDemandResult accepted =
                demandApi.acceptProductionDemand(
                        new AcceptProductionDemandCommand(
                                sourceMrId,
                                destination.value(),
                                "system",
                                List.of(line(UUID.randomUUID(), materialA, "3"))));
        WarehouseDemandView view =
                demandQuery
                        .getDemandBySourceMaterialRequirementId(sourceMrId)
                        .orElseThrow();
        assertEquals(accepted.demandId(), view.demandId());
        assertEquals(DemandLineRoutingOutcome.ROUTED, accepted.lineOutcomes().getFirst().outcome());
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

    private WarehouseDemandLineView lineBySource(WarehouseDemandView view, UUID sourceLineId) {
        return view.lines().stream()
                .filter(l -> l.sourceMaterialRequirementLineId().equals(sourceLineId))
                .findFirst()
                .orElseThrow();
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
                Instant.parse("2026-10-05T10:00:00Z"));
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
}
