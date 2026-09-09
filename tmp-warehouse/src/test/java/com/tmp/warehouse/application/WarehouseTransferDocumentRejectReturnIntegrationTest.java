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
import com.tmp.warehouse.api.WarehouseApi.CreateStorageCellCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.ReceiptCommand;
import com.tmp.warehouse.api.WarehouseApi.ReceiveTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.RejectTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.ReturnTransferMaterialsCommand;
import com.tmp.warehouse.api.WarehouseApi.SendTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentDestinationAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentLineInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReceiveResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentRejectResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReturnAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReturnResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSendResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSourceAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentView;
import com.tmp.warehouse.api.WarehouseApi.TransferStatusView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskKind;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskState;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskView;
import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.TransferContinuationReason;
import com.tmp.warehouse.domain.TransferDocumentSettlement;
import com.tmp.warehouse.domain.TransferSettlementState;
import com.tmp.warehouse.domain.repository.TransferDocumentSettlementRepository;
import com.tmp.warehouse.security.WarehousePermissions;
import com.tmp.warehouse.testsupport.WarehouseIntegrationTestSupport;
import com.tmp.warehouse.testsupport.WarehouseJdbcTestSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
 * Stage 3.5.8.3: full reject + physical TRANSFER_RETURN for Warehouse Transfer Documents.
 */
@Testcontainers
class WarehouseTransferDocumentRejectReturnIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-09T07:00:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    private final AtomicReference<SessionSummary> session = new AtomicReference<>();
    private final AtomicReference<Set<PermissionId>> permissions = new AtomicReference<>(Set.of());

    private WarehouseIntegrationTestSupport.ApiBundle bundle;
    private DefaultWarehouseApi api;
    private UUID userSource;
    private UUID userDestination;
    private UUID userDest2;
    private UUID userSourceOnly;
    private UUID userUnrelated;
    private UUID sourceWarehouseId;
    private UUID destinationWarehouseId;
    private UUID foreignWarehouseId;
    private UUID materialA;
    private UUID cellA1;
    private UUID cellA2;
    private UUID cellA5;
    private UUID cellA6;
    private UUID cellAInactive;
    private UUID cellB1;
    private UUID cellB2;
    private UUID cellF1;

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
        jdbc.update("DELETE FROM warehouse.material_reservation_links");
        jdbc.update("DELETE FROM warehouse.warehouse_movements");
        jdbc.update("DELETE FROM warehouse.warehouse_operations");
        jdbc.update("DELETE FROM warehouse.stock_positions");
        jdbc.update("DELETE FROM warehouse.storage_cells");
        jdbc.update("DELETE FROM warehouse.warehouses");
        jdbc.update("DELETE FROM warehouse.material_references");

        userSource = UUID.randomUUID();
        userDestination = UUID.randomUUID();
        userDest2 = UUID.randomUUID();
        userSourceOnly = UUID.randomUUID();
        userUnrelated = UUID.randomUUID();
        session.set(sessionFor(userSource));
        permissions.set(
                Set.of(
                        WarehousePermissions.WAREHOUSE_TRANSFER,
                        WarehousePermissions.WAREHOUSE_VIEW,
                        WarehousePermissions.WAREHOUSE_STRUCTURE_CREATE,
                        WarehousePermissions.WAREHOUSE_STRUCTURE_UPDATE,
                        WarehousePermissions.WAREHOUSE_STRUCTURE_VIEW,
                        WarehousePermissions.WAREHOUSE_RECEIPT,
                        WarehousePermissions.STORAGE_CELL_CREATE));

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

        var source = api.createWarehouse(new CreateWarehouseCommand("SRC", "Source", true));
        var destination = api.createWarehouse(new CreateWarehouseCommand("DST", "Destination", true));
        var foreign = api.createWarehouse(new CreateWarehouseCommand("FOREIGN", "Foreign", true));
        sourceWarehouseId = source.warehouseId();
        destinationWarehouseId = destination.warehouseId();
        foreignWarehouseId = foreign.warehouseId();

        api.assignUserToWarehouse(sourceWarehouseId, userSource);
        api.assignUserToWarehouse(sourceWarehouseId, userSourceOnly);
        api.assignUserToWarehouse(destinationWarehouseId, userDestination);
        api.assignUserToWarehouse(destinationWarehouseId, userDest2);

        MaterialReference matA =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc, CLOCK, MaterialReference.legacyArticle("MAT-A"));
        materialA = matA.id().value();

        cellA1 =
                api.createStorageCell(new CreateStorageCellCommand(sourceWarehouseId, "A1", true))
                        .storageCellId();
        cellA2 =
                api.createStorageCell(new CreateStorageCellCommand(sourceWarehouseId, "A2", true))
                        .storageCellId();
        cellA5 =
                api.createStorageCell(new CreateStorageCellCommand(sourceWarehouseId, "A5", true))
                        .storageCellId();
        cellA6 =
                api.createStorageCell(new CreateStorageCellCommand(sourceWarehouseId, "A6", true))
                        .storageCellId();
        cellAInactive =
                api.createStorageCell(
                                new CreateStorageCellCommand(sourceWarehouseId, "A_OFF", false))
                        .storageCellId();
        cellB1 =
                api.createStorageCell(
                                new CreateStorageCellCommand(destinationWarehouseId, "B1", true))
                        .storageCellId();
        cellB2 =
                api.createStorageCell(
                                new CreateStorageCellCommand(destinationWarehouseId, "B2", true))
                        .storageCellId();
        cellF1 =
                api.createStorageCell(new CreateStorageCellCommand(foreignWarehouseId, "F1", true))
                        .storageCellId();
    }

    @Test
    void fullRejectCreatesReturnPendingNoContinuationNoStockChange() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        BigDecimal transitBefore =
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT);
        BigDecimal availableBefore =
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.AVAILABLE);
        int docsBefore = documentCount();

        session.set(sessionFor(userDestination));
        TransferDocumentRejectResult rejected =
                api.rejectTransferDocument(
                        new RejectTransferDocumentCommand(
                                sent.documentId(), 0L, "  damaged pallet  "));

        assertEquals(DocumentStatus.POSTED.name(), rejected.documentStatus());
        assertEquals(TransferSettlementState.RETURN_PENDING.name(), rejected.settlementState());
        assertEquals("REJECTED", rejected.decision());
        assertEquals(1L, rejected.operationalRevision());
        assertEquals("damaged pallet", rejected.rejectionReason());
        assertEquals(
                0,
                jdbc.queryForObject(
                                """
                                SELECT COUNT(*) FROM warehouse.transfer_document_payload
                                 WHERE continuation_of_document_id = ?
                                """,
                                Integer.class,
                                sent.documentId())
                        .intValue());
        assertEquals(docsBefore, documentCount());
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                        .compareTo(transitBefore));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.AVAILABLE)
                        .compareTo(availableBefore));

        session.set(sessionFor(userDestination));
        assertTrue(
                api.listMyWarehouseTasks(null).stream()
                        .noneMatch(t -> t.documentId().equals(sent.documentId())));

        session.set(sessionFor(userSource));
        List<WarehouseTaskView> sourceTasks = api.listMyWarehouseTasks(null);
        assertTrue(
                sourceTasks.stream()
                        .anyMatch(
                                t ->
                                        t.documentId().equals(sent.documentId())
                                                && t.taskKind()
                                                        == WarehouseTaskKind.RETURN_MATERIALS
                                                && t.taskState() == WarehouseTaskState.NEW
                                                && "REJECTED".equals(t.settlementDecision())
                                                && "damaged pallet".equals(t.rejectionReason())));
    }

    @Test
    void blankRejectReasonsRejected() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "10");
        session.set(sessionFor(userDestination));
        for (String reason : new String[] {null, "", "   "}) {
            assertThrows(
                    InvalidWarehouseStateException.class,
                    () ->
                            api.rejectTransferDocument(
                                    new RejectTransferDocumentCommand(
                                            sent.documentId(), 0L, reason)));
        }
        assertEquals(
                TransferSettlementState.AWAITING_RECEIPT.name(),
                settlementState(sent.documentId()));
        assertEquals(0L, operationalRevision(sent.documentId()));
    }

    @Test
    void rejectAuthorization() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "10");

        session.set(sessionFor(userSourceOnly));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.rejectTransferDocument(
                                new RejectTransferDocumentCommand(
                                        sent.documentId(), 0L, "no")));

        session.set(sessionFor(userDestination));
        permissions.set(Set.of(WarehousePermissions.WAREHOUSE_VIEW));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.rejectTransferDocument(
                                new RejectTransferDocumentCommand(
                                        sent.documentId(), 0L, "no")));

        permissions.set(
                Set.of(
                        WarehousePermissions.WAREHOUSE_TRANSFER,
                        WarehousePermissions.WAREHOUSE_VIEW));
        assertEquals(
                TransferSettlementState.AWAITING_RECEIPT.name(),
                settlementState(sent.documentId()));
    }

    @Test
    void rejectAfterPartialReceiveFails() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());
        session.set(sessionFor(userDestination));
        receiveDoc(sent.documentId(), 0L, List.of(destAlloc(line, cellB1, "98")));

        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        api.rejectTransferDocument(
                                new RejectTransferDocumentCommand(
                                        sent.documentId(), 1L, "too late")));
        assertEquals(
                TransferSettlementState.RETURN_PENDING.name(), settlementState(sent.documentId()));
        assertEquals("ACCEPTED", settlementDecision(sent.documentId()));
    }

    @Test
    void rejectAfterFullReceiveFails() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());
        session.set(sessionFor(userDestination));
        receiveDoc(sent.documentId(), 0L, List.of(destAlloc(line, cellB1, "100")));

        assertThrows(
                Exception.class,
                () ->
                        api.rejectTransferDocument(
                                new RejectTransferDocumentCommand(
                                        sent.documentId(), 1L, "too late")));
        assertEquals(TransferSettlementState.SETTLED.name(), settlementState(sent.documentId()));
    }

    @Test
    void receiveAfterRejectFails() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());
        session.set(sessionFor(userDestination));
        api.rejectTransferDocument(
                new RejectTransferDocumentCommand(sent.documentId(), 0L, "reject"));

        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        receiveDoc(
                                sent.documentId(),
                                1L,
                                List.of(destAlloc(line, cellB1, "100"))));
        assertEquals(0, receiptItemCount(sent.documentId()));
    }

    @Test
    void partialReturnDefault() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());

        session.set(sessionFor(userDestination));
        TransferDocumentReceiveResult received =
                receiveDoc(sent.documentId(), 0L, List.of(destAlloc(line, cellB1, "98")));
        assertNotNull(received.continuationDocumentId());
        UUID continuationId = received.continuationDocumentId();

        session.set(sessionFor(userSource));
        TransferDocumentReturnResult returned =
                api.returnTransferMaterials(
                        new ReturnTransferMaterialsCommand(
                                sent.documentId(), 1L, List.of()));

        assertEquals(DocumentStatus.CLOSED.name(), returned.documentStatus());
        assertEquals(TransferSettlementState.SETTLED.name(), returned.settlementState());
        assertEquals("ACCEPTED", returned.decision());
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                        .compareTo(BigDecimal.ZERO));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.AVAILABLE)
                        .compareTo(new BigDecimal("2")));
        assertEquals(
                0,
                stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE)
                        .compareTo(new BigDecimal("98")));

        TransferDocumentView continuation = api.getTransferDocument(continuationId);
        assertEquals(DocumentStatus.DRAFT.name(), continuation.documentStatus());
        assertEquals(
                TransferContinuationReason.RECEIVE_SHORTFALL.name(),
                continuation.continuationReason());
        assertEquals(0, continuation.lines().get(0).quantity().compareTo(new BigDecimal("2")));
        assertEquals(1, returnItemCount(sent.documentId()));
    }

    @Test
    void rejectReturnDefault() {
        seedAvailable(materialA, cellA1, "60");
        seedAvailable(materialA, cellA2, "40");
        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, new BigDecimal("100"), 1))));
        TransferDocumentSendResult sent =
                api.sendTransferDocument(
                        new SendTransferDocumentCommand(
                                created.documentId(),
                                documentVersion(created.documentId()),
                                created.payloadRevision(),
                                List.of(
                                        alloc(created, materialA, cellA1, "60"),
                                        alloc(created, materialA, cellA2, "40"))));

        session.set(sessionFor(userDestination));
        TransferDocumentRejectResult rejected =
                api.rejectTransferDocument(
                        new RejectTransferDocumentCommand(
                                sent.documentId(), 0L, "full reject"));
        assertEquals("REJECTED", rejected.decision());

        session.set(sessionFor(userSource));
        TransferDocumentReturnResult returned =
                api.returnTransferMaterials(
                        new ReturnTransferMaterialsCommand(
                                sent.documentId(), 1L, List.of()));

        assertEquals(DocumentStatus.CLOSED.name(), returned.documentStatus());
        assertEquals(TransferSettlementState.SETTLED.name(), returned.settlementState());
        assertEquals("REJECTED", returned.decision());
        assertEquals("full reject", settlementRejectionReason(sent.documentId()));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                        .add(stockQty(sourceWarehouseId, cellA2, materialA, StockState.IN_TRANSIT))
                        .compareTo(BigDecimal.ZERO));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.AVAILABLE)
                        .compareTo(new BigDecimal("60")));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA2, materialA, StockState.AVAILABLE)
                        .compareTo(new BigDecimal("40")));
    }

    @Test
    void returnOverrideToAnotherSourceCell() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "25");
        session.set(sessionFor(userDestination));
        api.rejectTransferDocument(
                new RejectTransferDocumentCommand(sent.documentId(), 0L, "override"));
        UUID line = singleLineId(sent.documentId());

        session.set(sessionFor(userSource));
        TransferDocumentReturnResult returned =
                api.returnTransferMaterials(
                        new ReturnTransferMaterialsCommand(
                                sent.documentId(),
                                1L,
                                List.of(
                                        new TransferDocumentReturnAllocationInput(
                                                line, cellA5, new BigDecimal("25")))));

        assertEquals(DocumentStatus.CLOSED.name(), returned.documentStatus());
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                        .compareTo(BigDecimal.ZERO));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA5, materialA, StockState.AVAILABLE)
                        .compareTo(new BigDecimal("25")));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.AVAILABLE)
                        .compareTo(BigDecimal.ZERO));
    }

    @Test
    void multiTargetReturn() {
        seedAvailable(materialA, cellA1, "60");
        seedAvailable(materialA, cellA2, "40");
        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, new BigDecimal("100"), 1))));
        TransferDocumentSendResult sent =
                api.sendTransferDocument(
                        new SendTransferDocumentCommand(
                                created.documentId(),
                                documentVersion(created.documentId()),
                                created.payloadRevision(),
                                List.of(
                                        alloc(created, materialA, cellA1, "60"),
                                        alloc(created, materialA, cellA2, "40"))));
        UUID line = lineId(created, materialA);

        session.set(sessionFor(userDestination));
        receiveDoc(
                sent.documentId(),
                0L,
                List.of(destAlloc(line, cellB1, "50"), destAlloc(line, cellB2, "25")));

        session.set(sessionFor(userSource));
        TransferDocumentReturnResult returned =
                api.returnTransferMaterials(
                        new ReturnTransferMaterialsCommand(
                                sent.documentId(),
                                1L,
                                List.of(
                                        new TransferDocumentReturnAllocationInput(
                                                line, cellA5, new BigDecimal("10")),
                                        new TransferDocumentReturnAllocationInput(
                                                line, cellA6, new BigDecimal("15")))));

        assertEquals(DocumentStatus.CLOSED.name(), returned.documentStatus());
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA5, materialA, StockState.AVAILABLE)
                        .compareTo(new BigDecimal("10")));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA6, materialA, StockState.AVAILABLE)
                        .compareTo(new BigDecimal("15")));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                        .add(stockQty(sourceWarehouseId, cellA2, materialA, StockState.IN_TRANSIT))
                        .compareTo(BigDecimal.ZERO));
    }

    @Test
    void wrongWarehouseReturnCellRejected() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "10");
        UUID line = singleLineId(sent.documentId());
        session.set(sessionFor(userDestination));
        api.rejectTransferDocument(
                new RejectTransferDocumentCommand(sent.documentId(), 0L, "reason"));

        session.set(sessionFor(userSource));
        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        api.returnTransferMaterials(
                                new ReturnTransferMaterialsCommand(
                                        sent.documentId(),
                                        1L,
                                        List.of(
                                                new TransferDocumentReturnAllocationInput(
                                                        line, cellF1, new BigDecimal("10"))))));
        assertEquals(
                TransferSettlementState.RETURN_PENDING.name(), settlementState(sent.documentId()));
    }

    @Test
    void inactiveReturnCellRejected() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "10");
        UUID line = singleLineId(sent.documentId());
        session.set(sessionFor(userDestination));
        api.rejectTransferDocument(
                new RejectTransferDocumentCommand(sent.documentId(), 0L, "reason"));

        session.set(sessionFor(userSource));
        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        api.returnTransferMaterials(
                                new ReturnTransferMaterialsCommand(
                                        sent.documentId(),
                                        1L,
                                        List.of(
                                                new TransferDocumentReturnAllocationInput(
                                                        line,
                                                        cellAInactive,
                                                        new BigDecimal("10"))))));
    }

    @Test
    void underAndOverReturnRejected() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());
        session.set(sessionFor(userDestination));
        receiveDoc(sent.documentId(), 0L, List.of(destAlloc(line, cellB1, "98")));

        session.set(sessionFor(userSource));
        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        api.returnTransferMaterials(
                                new ReturnTransferMaterialsCommand(
                                        sent.documentId(),
                                        1L,
                                        List.of(
                                                new TransferDocumentReturnAllocationInput(
                                                        line, cellA1, new BigDecimal("1"))))));
        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        api.returnTransferMaterials(
                                new ReturnTransferMaterialsCommand(
                                        sent.documentId(),
                                        1L,
                                        List.of(
                                                new TransferDocumentReturnAllocationInput(
                                                        line, cellA1, new BigDecimal("3"))))));
        assertEquals(
                TransferSettlementState.RETURN_PENDING.name(), settlementState(sent.documentId()));
        assertEquals(0, returnItemCount(sent.documentId()));
    }

    @Test
    void conservationPartialAndReject() {
        TransferDocumentSendResult partial = sendPostedDocument(materialA, cellA1, "100");
        UUID lineP = singleLineId(partial.documentId());
        session.set(sessionFor(userDestination));
        receiveDoc(partial.documentId(), 0L, List.of(destAlloc(lineP, cellB1, "98")));
        session.set(sessionFor(userSource));
        api.returnTransferMaterials(
                new ReturnTransferMaterialsCommand(partial.documentId(), 1L, List.of()));
        assertConservation(partial.documentId());

        TransferDocumentSendResult rejected = sendPostedDocument(materialA, cellA2, "40");
        session.set(sessionFor(userDestination));
        api.rejectTransferDocument(
                new RejectTransferDocumentCommand(rejected.documentId(), 0L, "rej"));
        session.set(sessionFor(userSource));
        api.returnTransferMaterials(
                new ReturnTransferMaterialsCommand(rejected.documentId(), 1L, List.of()));
        assertConservation(rejected.documentId());
    }

    @Test
    void concurrentReceiveVsReject() throws Exception {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());

        ReceiveTransferDocumentCommand receive =
                new ReceiveTransferDocumentCommand(
                        sent.documentId(), 0L, List.of(destAlloc(line, cellB1, "100")));
        RejectTransferDocumentCommand reject =
                new RejectTransferDocumentCommand(sent.documentId(), 0L, "race");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            futures.add(
                    pool.submit(
                            () -> {
                                session.set(sessionFor(userDestination));
                                permissions.set(
                                        Set.of(
                                                WarehousePermissions.WAREHOUSE_TRANSFER,
                                                WarehousePermissions.WAREHOUSE_VIEW));
                                ready.countDown();
                                start.await(10, TimeUnit.SECONDS);
                                try {
                                    api.receiveTransferDocument(receive);
                                    successes.incrementAndGet();
                                } catch (RuntimeException ex) {
                                    failures.incrementAndGet();
                                }
                                return null;
                            }));
            futures.add(
                    pool.submit(
                            () -> {
                                session.set(sessionFor(userDestination));
                                permissions.set(
                                        Set.of(
                                                WarehousePermissions.WAREHOUSE_TRANSFER,
                                                WarehousePermissions.WAREHOUSE_VIEW));
                                ready.countDown();
                                start.await(10, TimeUnit.SECONDS);
                                try {
                                    api.rejectTransferDocument(reject);
                                    successes.incrementAndGet();
                                } catch (RuntimeException ex) {
                                    failures.incrementAndGet();
                                }
                                return null;
                            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, successes.get());
        assertEquals(1, failures.get());
        String state = settlementState(sent.documentId());
        assertTrue(
                TransferSettlementState.SETTLED.name().equals(state)
                        || TransferSettlementState.RETURN_PENDING.name().equals(state));
    }

    @Test
    void concurrentReturn() throws Exception {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "50");
        session.set(sessionFor(userDestination));
        api.rejectTransferDocument(
                new RejectTransferDocumentCommand(sent.documentId(), 0L, "race-return"));

        ReturnTransferMaterialsCommand command =
                new ReturnTransferMaterialsCommand(sent.documentId(), 1L, List.of());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(
                        pool.submit(
                                () -> {
                                    session.set(sessionFor(userSource));
                                    permissions.set(
                                            Set.of(
                                                    WarehousePermissions.WAREHOUSE_TRANSFER,
                                                    WarehousePermissions.WAREHOUSE_VIEW));
                                    ready.countDown();
                                    start.await(10, TimeUnit.SECONDS);
                                    try {
                                        api.returnTransferMaterials(command);
                                        successes.incrementAndGet();
                                    } catch (RuntimeException ex) {
                                        failures.incrementAndGet();
                                    }
                                    return null;
                                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, successes.get());
        assertEquals(1, failures.get());
        assertEquals(DocumentStatus.CLOSED.name(), documentStatus(sent.documentId()));
        assertEquals(TransferSettlementState.SETTLED.name(), settlementState(sent.documentId()));
    }

    @Test
    void transferStatusAfterRejectReturnPartial() {
        TransferDocumentSendResult rejected = sendPostedDocument(materialA, cellA1, "10");
        UUID sendOpRejected = sendOperationId(rejected.documentId());
        session.set(sessionFor(userDestination));
        api.rejectTransferDocument(
                new RejectTransferDocumentCommand(rejected.documentId(), 0L, "st"));
        TransferStatusView afterReject = api.getTransferStatus(sendOpRejected);
        assertEquals("REJECTED", afterReject.status());

        session.set(sessionFor(userSource));
        api.returnTransferMaterials(
                new ReturnTransferMaterialsCommand(rejected.documentId(), 1L, List.of()));
        assertEquals("RETURNED", api.getTransferStatus(sendOpRejected).status());

        TransferDocumentSendResult partial = sendPostedDocument(materialA, cellA2, "20");
        UUID line = singleLineId(partial.documentId());
        UUID sendOpPartial = sendOperationId(partial.documentId());
        session.set(sessionFor(userDestination));
        receiveDoc(partial.documentId(), 0L, List.of(destAlloc(line, cellB1, "15")));
        assertEquals("PARTIALLY_RECEIVED", api.getTransferStatus(sendOpPartial).status());
    }

    @Test
    void sendShortfallThenReject() {
        seedAvailable(materialA, cellA1, "200");
        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, new BigDecimal("100"), 1))));
        TransferDocumentSendResult sent =
                api.sendTransferDocument(
                        new SendTransferDocumentCommand(
                                created.documentId(),
                                documentVersion(created.documentId()),
                                created.payloadRevision(),
                                List.of(
                                        new TransferDocumentSourceAllocationInput(
                                                lineId(created, materialA),
                                                cellA1,
                                                new BigDecimal("98")))));
        assertNotNull(sent.continuationDocumentId());
        UUID shortfallId = sent.continuationDocumentId();

        session.set(sessionFor(userDestination));
        api.rejectTransferDocument(
                new RejectTransferDocumentCommand(sent.documentId(), 0L, "after shortfall"));

        assertEquals(
                1,
                jdbc.queryForObject(
                                """
                                SELECT COUNT(*) FROM warehouse.transfer_document_payload
                                 WHERE continuation_of_document_id = ?
                                """,
                                Integer.class,
                                sent.documentId())
                        .intValue());
        TransferDocumentView shortfall = api.getTransferDocument(shortfallId);
        assertEquals(DocumentStatus.DRAFT.name(), shortfall.documentStatus());
        assertEquals(
                TransferContinuationReason.SHORTFALL.name(), shortfall.continuationReason());
        assertEquals(
                0,
                jdbc.queryForObject(
                                """
                                SELECT COUNT(*) FROM warehouse.transfer_document_payload
                                 WHERE continuation_of_document_id = ?
                                   AND continuation_reason = 'RECEIVE_SHORTFALL'
                                """,
                                Integer.class,
                                sent.documentId())
                        .intValue());
    }

    @Test
    void fullReceiveRegressionStillSettledClosedReceivedZeroReturnItems() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());
        session.set(sessionFor(userDestination));
        TransferDocumentReceiveResult received =
                receiveDoc(sent.documentId(), 0L, List.of(destAlloc(line, cellB1, "100")));

        assertEquals(DocumentStatus.CLOSED.name(), received.documentStatus());
        assertEquals(TransferSettlementState.SETTLED.name(), received.settlementState());
        assertEquals(0, returnItemCount(sent.documentId()));
        assertEquals("RECEIVED", api.getTransferStatus(sendOperationId(sent.documentId())).status());
    }

    @Test
    void returnHistoryIsTransferReturnTypeOnOperationsAndMovements() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "12");
        session.set(sessionFor(userDestination));
        api.rejectTransferDocument(
                new RejectTransferDocumentCommand(sent.documentId(), 0L, "hist"));
        session.set(sessionFor(userSource));
        TransferDocumentReturnResult returned =
                api.returnTransferMaterials(
                        new ReturnTransferMaterialsCommand(sent.documentId(), 1L, List.of()));

        assertFalse(returned.returnOperationIds().isEmpty());
        for (UUID opId : returned.returnOperationIds()) {
            String type =
                    jdbc.queryForObject(
                            """
                            SELECT operation_type FROM warehouse.warehouse_operations WHERE id = ?
                            """,
                            String.class,
                            opId);
            assertEquals("TRANSFER_RETURN", type);
        }
        Integer movementCount =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM warehouse.warehouse_movements
                         WHERE operation_type = 'TRANSFER_RETURN'
                        """,
                        Integer.class);
        assertTrue(movementCount != null && movementCount >= 2);
    }

    @Test
    void rejectRollbackPreservesAwaitingReceipt() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "40");
        session.set(sessionFor(userDestination));
        api.takeTransferTaskInWork(sent.documentId());
        BigDecimal transitBefore =
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT);

        TransferDocumentSettlementRepository failing =
                new FailingRejectSettlementRepository(bundle.settlements());
        WarehouseTransferRejectService failingReject =
                new WarehouseTransferRejectService(
                        bundle.documentEngine(),
                        bundle.transferDocuments(),
                        failing,
                        bundle.sendAllocations(),
                        bundle.receiptItems(),
                        bundle.taskStates(),
                        new DefaultWarehouseResponsibilityGuard(
                                authenticationFromSession(),
                                new com.tmp.warehouse.persistence
                                        .JdbcWarehouseUserResponsibilityRepository(jdbc, CLOCK)),
                        authenticationFromSession(),
                        new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                        CLOCK);

        assertThrows(
                RuntimeException.class,
                () ->
                        failingReject.reject(
                                new WarehouseTransferRejectService.RejectCommand(
                                        sent.documentId(), 0L, "should rollback")));

        assertEquals(DocumentStatus.POSTED.name(), documentStatus(sent.documentId()));
        assertEquals(
                TransferSettlementState.AWAITING_RECEIPT.name(), settlementState(sent.documentId()));
        assertNull(settlementDecision(sent.documentId()));
        assertEquals(0L, operationalRevision(sent.documentId()));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                        .compareTo(transitBefore));
        assertEquals(
                userDestination,
                bundle.taskStates().findByDocumentId(sent.documentId()).orElseThrow().workingUserId());
    }

    @Test
    void returnRollbackPreservesReturnPending() {
        seedAvailable(materialA, cellA1, "60");
        seedAvailable(materialA, cellA2, "40");
        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, new BigDecimal("100"), 1))));
        TransferDocumentSendResult sent =
                api.sendTransferDocument(
                        new SendTransferDocumentCommand(
                                created.documentId(),
                                documentVersion(created.documentId()),
                                created.payloadRevision(),
                                List.of(
                                        alloc(created, materialA, cellA1, "60"),
                                        alloc(created, materialA, cellA2, "40"))));
        session.set(sessionFor(userDestination));
        api.rejectTransferDocument(
                new RejectTransferDocumentCommand(sent.documentId(), 0L, "rollback-return"));
        session.set(sessionFor(userSource));
        api.takeTransferTaskInWork(sent.documentId());

        BigDecimal transitA1 =
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT);
        BigDecimal transitA2 =
                stockQty(sourceWarehouseId, cellA2, materialA, StockState.IN_TRANSIT);
        long returnOpsBefore = countReturnOps();

        TransferDocumentSettlementRepository failing =
                new FailingReturnedSettledSettlementRepository(bundle.settlements());
        WarehouseTransferReturnService failingReturn =
                new WarehouseTransferReturnService(
                        bundle.documentEngine(),
                        bundle.transferDocuments(),
                        failing,
                        bundle.sendAllocations(),
                        bundle.receiptItems(),
                        bundle.returnItems(),
                        bundle.taskStates(),
                        bundle.operationEngine(),
                        bundle.materials(),
                        bundle.catalog(),
                        new DefaultWarehouseResponsibilityGuard(
                                authenticationFromSession(),
                                new com.tmp.warehouse.persistence
                                        .JdbcWarehouseUserResponsibilityRepository(jdbc, CLOCK)),
                        new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                        CLOCK);

        assertThrows(
                RuntimeException.class,
                () ->
                        failingReturn.returnMaterials(
                                new WarehouseTransferReturnService.ReturnCommand(
                                        sent.documentId(), 1L, List.of())));

        assertEquals(DocumentStatus.POSTED.name(), documentStatus(sent.documentId()));
        assertEquals(
                TransferSettlementState.RETURN_PENDING.name(), settlementState(sent.documentId()));
        assertEquals("REJECTED", settlementDecision(sent.documentId()));
        assertEquals(1L, operationalRevision(sent.documentId()));
        assertEquals(0, returnItemCount(sent.documentId()));
        assertEquals(returnOpsBefore, countReturnOps());
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                        .compareTo(transitA1));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA2, materialA, StockState.IN_TRANSIT)
                        .compareTo(transitA2));
        assertEquals(
                userSource,
                bundle.taskStates().findByDocumentId(sent.documentId()).orElseThrow().workingUserId());
    }

    private void assertConservation(UUID documentId) {
        List<UUID> allocationIds =
                jdbc.query(
                        """
                        SELECT id FROM warehouse.transfer_document_send_allocation
                         WHERE document_id = ?
                        """,
                        (rs, rowNum) -> (UUID) rs.getObject("id"),
                        documentId);
        for (UUID allocationId : allocationIds) {
            BigDecimal sentQty =
                    jdbc.queryForObject(
                            """
                            SELECT quantity FROM warehouse.transfer_document_send_allocation
                             WHERE id = ?
                            """,
                            BigDecimal.class,
                            allocationId);
            BigDecimal accepted =
                    jdbc.queryForObject(
                            """
                            SELECT COALESCE(SUM(quantity), 0)
                              FROM warehouse.transfer_receipt_settlement_item
                             WHERE send_allocation_id = ?
                            """,
                            BigDecimal.class,
                            allocationId);
            BigDecimal returnedQty =
                    jdbc.queryForObject(
                            """
                            SELECT COALESCE(SUM(quantity), 0)
                              FROM warehouse.transfer_return_settlement_item
                             WHERE send_allocation_id = ?
                            """,
                            BigDecimal.class,
                            allocationId);
            assertEquals(0, sentQty.compareTo(accepted.add(returnedQty)));
        }
    }

    private TransferDocumentSendResult sendPostedDocument(
            UUID materialId, UUID sourceCellId, String qty) {
        seedAvailable(materialId, sourceCellId, qty);
        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialId, new BigDecimal(qty), 1))));
        return api.sendTransferDocument(
                new SendTransferDocumentCommand(
                        created.documentId(),
                        documentVersion(created.documentId()),
                        created.payloadRevision(),
                        List.of(
                                new TransferDocumentSourceAllocationInput(
                                        lineId(created, materialId),
                                        sourceCellId,
                                        new BigDecimal(qty)))));
    }

    private TransferDocumentReceiveResult receiveDoc(
            UUID documentId,
            long revision,
            List<TransferDocumentDestinationAllocationInput> destinationAllocations) {
        return api.receiveTransferDocument(
                new ReceiveTransferDocumentCommand(documentId, revision, destinationAllocations));
    }

    private TransferDocumentDestinationAllocationInput destAlloc(
            UUID lineId, UUID destinationCellId, String qty) {
        return new TransferDocumentDestinationAllocationInput(
                lineId, destinationCellId, new BigDecimal(qty));
    }

    private TransferDocumentSourceAllocationInput alloc(
            TransferDocumentView doc, UUID materialId, UUID cellId, String qty) {
        return new TransferDocumentSourceAllocationInput(
                lineId(doc, materialId), cellId, new BigDecimal(qty));
    }

    private static UUID lineId(TransferDocumentView doc, UUID materialId) {
        return doc.lines().stream()
                .filter(l -> materialId.equals(l.materialReferenceId()))
                .findFirst()
                .orElseThrow()
                .lineId();
    }

    private UUID singleLineId(UUID documentId) {
        return jdbc.queryForObject(
                """
                SELECT id FROM warehouse.transfer_document_lines
                 WHERE document_id = ?
                 ORDER BY line_order
                 LIMIT 1
                """,
                UUID.class,
                documentId);
    }

    private void seedAvailable(UUID materialId, UUID cellId, String qty) {
        String article =
                jdbc.queryForObject(
                        "SELECT article FROM warehouse.material_references WHERE id = ?",
                        String.class,
                        materialId);
        api.receive(
                new ReceiptCommand(
                        article,
                        article,
                        "",
                        "",
                        "",
                        new BigDecimal(qty),
                        sourceWarehouseId,
                        cellId));
    }

    private long documentVersion(UUID documentId) {
        return bundle.documentEngine().findById(documentId).orElseThrow().version();
    }

    private BigDecimal stockQty(
            UUID warehouseId, UUID cellId, UUID materialId, StockState state) {
        BigDecimal qty =
                jdbc.query(
                        """
                        SELECT quantity FROM warehouse.stock_positions
                         WHERE warehouse_id = ?
                           AND storage_cell_id = ?
                           AND material_reference_id = ?
                           AND stock_state = ?
                        """,
                        rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO,
                        warehouseId,
                        cellId,
                        materialId,
                        state.name());
        return qty == null ? BigDecimal.ZERO : qty;
    }

    private String settlementState(UUID documentId) {
        return jdbc.queryForObject(
                """
                SELECT settlement_state FROM warehouse.transfer_document_settlement
                 WHERE document_id = ?
                """,
                String.class,
                documentId);
    }

    private String settlementDecision(UUID documentId) {
        return jdbc.queryForObject(
                """
                SELECT decision FROM warehouse.transfer_document_settlement
                 WHERE document_id = ?
                """,
                String.class,
                documentId);
    }

    private String settlementRejectionReason(UUID documentId) {
        return jdbc.queryForObject(
                """
                SELECT rejection_reason FROM warehouse.transfer_document_settlement
                 WHERE document_id = ?
                """,
                String.class,
                documentId);
    }

    private long operationalRevision(UUID documentId) {
        return jdbc.queryForObject(
                        """
                        SELECT operational_revision FROM warehouse.transfer_document_settlement
                         WHERE document_id = ?
                        """,
                        Long.class,
                        documentId)
                .longValue();
    }

    private int receiptItemCount(UUID documentId) {
        return jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM warehouse.transfer_receipt_settlement_item
                         WHERE document_id = ?
                        """,
                        Integer.class,
                        documentId)
                .intValue();
    }

    private int returnItemCount(UUID documentId) {
        return jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM warehouse.transfer_return_settlement_item
                         WHERE document_id = ?
                        """,
                        Integer.class,
                        documentId)
                .intValue();
    }

    private int documentCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM documents.documents", Integer.class)
                .intValue();
    }

    private String documentStatus(UUID documentId) {
        return bundle.documentEngine().findById(documentId).orElseThrow().status().name();
    }

    private UUID sendOperationId(UUID documentId) {
        return jdbc.queryForObject(
                """
                SELECT send_operation_id FROM warehouse.transfer_document_send_allocation
                 WHERE document_id = ?
                 ORDER BY created_at, id
                 LIMIT 1
                """,
                UUID.class,
                documentId);
    }

    private SessionSummary sessionFor(UUID userId) {
        return new SessionSummary(
                SessionId.of(UUID.randomUUID()),
                UserId.of(userId),
                Login.of("u-" + userId.toString().substring(0, 8)),
                Instant.now(CLOCK));
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
                    throw new AccessDeniedException("missing " + permissionId);
                }
            }

            @Override
            public Set<PermissionId> effectivePermissions() {
                return permissions.get();
            }
        };
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

    private long countReturnOps() {
        return jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM warehouse.warehouse_operations
                         WHERE operation_type = 'TRANSFER_RETURN'
                        """,
                        Long.class)
                .longValue();
    }

    private static final class FailingRejectSettlementRepository
            implements TransferDocumentSettlementRepository {
        private final TransferDocumentSettlementRepository delegate;

        private FailingRejectSettlementRepository(TransferDocumentSettlementRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public void insertAwaitingReceipt(TransferDocumentSettlement settlement) {
            delegate.insertAwaitingReceipt(settlement);
        }

        @Override
        public Optional<TransferDocumentSettlement> findByDocumentId(UUID documentId) {
            return delegate.findByDocumentId(documentId);
        }

        @Override
        public Map<UUID, TransferDocumentSettlement> findByDocumentIds(
                Collection<UUID> documentIds) {
            return delegate.findByDocumentIds(documentIds);
        }

        @Override
        public Optional<TransferDocumentSettlement> lockByDocumentId(UUID documentId) {
            return delegate.lockByDocumentId(documentId);
        }

        @Override
        public void markAcceptedAndSettled(
                UUID documentId,
                long expectedOperationalRevision,
                TransferDocumentSettlement updated) {
            delegate.markAcceptedAndSettled(documentId, expectedOperationalRevision, updated);
        }

        @Override
        public void markAcceptedAndReturnPending(
                UUID documentId,
                long expectedOperationalRevision,
                TransferDocumentSettlement updated) {
            delegate.markAcceptedAndReturnPending(
                    documentId, expectedOperationalRevision, updated);
        }

        @Override
        public void markRejectedAndReturnPending(
                UUID documentId,
                long expectedOperationalRevision,
                TransferDocumentSettlement updated) {
            throw new RuntimeException("forced markRejectedAndReturnPending failure");
        }

        @Override
        public void markReturnedAndSettled(
                UUID documentId,
                long expectedOperationalRevision,
                TransferDocumentSettlement updated) {
            delegate.markReturnedAndSettled(documentId, expectedOperationalRevision, updated);
        }
    }

    private static final class FailingReturnedSettledSettlementRepository
            implements TransferDocumentSettlementRepository {
        private final TransferDocumentSettlementRepository delegate;

        private FailingReturnedSettledSettlementRepository(
                TransferDocumentSettlementRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public void insertAwaitingReceipt(TransferDocumentSettlement settlement) {
            delegate.insertAwaitingReceipt(settlement);
        }

        @Override
        public Optional<TransferDocumentSettlement> findByDocumentId(UUID documentId) {
            return delegate.findByDocumentId(documentId);
        }

        @Override
        public Map<UUID, TransferDocumentSettlement> findByDocumentIds(
                Collection<UUID> documentIds) {
            return delegate.findByDocumentIds(documentIds);
        }

        @Override
        public Optional<TransferDocumentSettlement> lockByDocumentId(UUID documentId) {
            return delegate.lockByDocumentId(documentId);
        }

        @Override
        public void markAcceptedAndSettled(
                UUID documentId,
                long expectedOperationalRevision,
                TransferDocumentSettlement updated) {
            delegate.markAcceptedAndSettled(documentId, expectedOperationalRevision, updated);
        }

        @Override
        public void markAcceptedAndReturnPending(
                UUID documentId,
                long expectedOperationalRevision,
                TransferDocumentSettlement updated) {
            delegate.markAcceptedAndReturnPending(
                    documentId, expectedOperationalRevision, updated);
        }

        @Override
        public void markRejectedAndReturnPending(
                UUID documentId,
                long expectedOperationalRevision,
                TransferDocumentSettlement updated) {
            delegate.markRejectedAndReturnPending(
                    documentId, expectedOperationalRevision, updated);
        }

        @Override
        public void markReturnedAndSettled(
                UUID documentId,
                long expectedOperationalRevision,
                TransferDocumentSettlement updated) {
            throw new RuntimeException("forced markReturnedAndSettled failure");
        }
    }
}
