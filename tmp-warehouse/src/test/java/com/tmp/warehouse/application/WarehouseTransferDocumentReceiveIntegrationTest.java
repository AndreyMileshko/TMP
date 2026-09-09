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
import com.tmp.warehouse.api.WarehouseApi.CreateTransferDraftCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.ReceiptCommand;
import com.tmp.warehouse.api.WarehouseApi.ReceiveTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.SendTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentDestinationAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentLineInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReceiveResult;
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
import com.tmp.warehouse.domain.TransferSettlementState;
import com.tmp.warehouse.security.WarehousePermissions;
import com.tmp.warehouse.testsupport.WarehouseIntegrationTestSupport;
import com.tmp.warehouse.testsupport.WarehouseJdbcTestSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Stage 3.5.8.1 Settlement foundation + full document receive + receiver task.
 */
@Testcontainers
class WarehouseTransferDocumentReceiveIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-08T14:00:00Z"), ZoneOffset.UTC);

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
    private UUID materialB;
    private UUID cellA1;
    private UUID cellA2;
    private UUID cellB1;
    private UUID cellB2;
    private UUID cellBInactive;
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
        MaterialReference matB =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc, CLOCK, MaterialReference.legacyArticle("MAT-B"));
        materialA = matA.id().value();
        materialB = matB.id().value();

        cellA1 =
                api.createStorageCell(new CreateStorageCellCommand(sourceWarehouseId, "A1", true))
                        .storageCellId();
        cellA2 =
                api.createStorageCell(new CreateStorageCellCommand(sourceWarehouseId, "A2", true))
                        .storageCellId();
        cellB1 =
                api.createStorageCell(
                                new CreateStorageCellCommand(destinationWarehouseId, "B1", true))
                        .storageCellId();
        cellB2 =
                api.createStorageCell(
                                new CreateStorageCellCommand(destinationWarehouseId, "B2", true))
                        .storageCellId();
        cellBInactive =
                api.createStorageCell(
                                new CreateStorageCellCommand(
                                        destinationWarehouseId, "B_INACT", false))
                        .storageCellId();
        cellF1 =
                api.createStorageCell(new CreateStorageCellCommand(foreignWarehouseId, "F1", true))
                        .storageCellId();
    }

    @Test
    void v41SchemaAndConstraints() {
        Integer settlementTables =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.tables
                         WHERE table_schema = 'warehouse'
                           AND table_name IN (
                               'transfer_document_settlement',
                               'transfer_receipt_settlement_item')
                        """,
                        Integer.class);
        assertEquals(2, settlementTables);

        Integer receiveOpUnique =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.table_constraints
                         WHERE table_schema = 'warehouse'
                           AND table_name = 'transfer_receipt_settlement_item'
                           AND constraint_name = 'uq_receipt_item_receive_operation_id'
                           AND constraint_type = 'UNIQUE'
                        """,
                        Integer.class);
        assertEquals(1, receiveOpUnique);

        Integer sendAllocUk =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.table_constraints
                         WHERE table_schema = 'warehouse'
                           AND table_name = 'transfer_document_send_allocation'
                           AND constraint_name = 'uk_send_allocation_document_id_id'
                           AND constraint_type = 'UNIQUE'
                        """,
                        Integer.class);
        assertEquals(1, sendAllocUk);
    }

    @Test
    void v41BackfillPostedNotDraft() {
        seedAvailable(materialA, cellA1, "10");
        TransferDocumentView draft =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, new BigDecimal("10"), 1))));
        assertEquals(
                0,
                settlementCount(draft.documentId()));

        TransferDocumentView postedDoc =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, new BigDecimal("10"), 1))));
        TransferDocumentSendResult posted =
                api.sendTransferDocument(
                        new SendTransferDocumentCommand(
                                postedDoc.documentId(),
                                documentVersion(postedDoc.documentId()),
                                postedDoc.payloadRevision(),
                                List.of(
                                        new TransferDocumentSourceAllocationInput(
                                                lineId(postedDoc, materialA),
                                                cellA1,
                                                new BigDecimal("10")))));
        assertEquals(1, totalSettlementCount());
        assertEquals(1, settlementCount(posted.documentId()));
        assertEquals(0, settlementCount(draft.documentId()));

        jdbc.update("DELETE FROM warehouse.transfer_document_settlement");
        assertEquals(0, totalSettlementCount());

        jdbc.update(
                """
                INSERT INTO warehouse.transfer_document_settlement (
                    document_id,
                    settlement_state,
                    operational_revision,
                    decision,
                    rejection_reason,
                    rejected_at,
                    rejected_by,
                    created_at,
                    updated_at
                )
                SELECT
                    p.document_id,
                    'AWAITING_RECEIPT',
                    0,
                    NULL,
                    NULL,
                    NULL,
                    NULL,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                FROM warehouse.transfer_document_payload p
                INNER JOIN documents.documents d ON d.id = p.document_id
                WHERE d.status = 'POSTED'
                  AND d.document_type_id = 'warehouse.transfer'
                  AND NOT EXISTS (
                        SELECT 1
                          FROM warehouse.transfer_document_settlement s
                         WHERE s.document_id = p.document_id
                  )
                """);

        assertEquals(1, totalSettlementCount());
        assertEquals(1, settlementCount(posted.documentId()));
        assertEquals(0, settlementCount(draft.documentId()));
        assertEquals(
                TransferSettlementState.AWAITING_RECEIPT.name(),
                jdbc.queryForObject(
                        """
                        SELECT settlement_state FROM warehouse.transfer_document_settlement
                         WHERE document_id = ?
                        """,
                        String.class,
                        posted.documentId()));
    }

    @Test
    void postCreatesAwaitingReceiptSettlement() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");

        assertEquals(1, settlementCount(sent.documentId()));
        assertEquals(
                TransferSettlementState.AWAITING_RECEIPT.name(),
                jdbc.queryForObject(
                        """
                        SELECT settlement_state FROM warehouse.transfer_document_settlement
                         WHERE document_id = ?
                        """,
                        String.class,
                        sent.documentId()));
        assertEquals(
                0L,
                jdbc.queryForObject(
                                """
                                SELECT operational_revision FROM warehouse.transfer_document_settlement
                                 WHERE document_id = ?
                                """,
                                Long.class,
                                sent.documentId())
                        .longValue());
        assertNull(
                jdbc.queryForObject(
                        """
                        SELECT decision FROM warehouse.transfer_document_settlement
                         WHERE document_id = ?
                        """,
                        String.class,
                        sent.documentId()));

        TransferDocumentView view = api.getTransferDocument(sent.documentId());
        assertEquals(TransferSettlementState.AWAITING_RECEIPT.name(), view.settlementState());
        assertEquals(0L, view.operationalRevision());
        assertEquals(DocumentStatus.POSTED.name(), view.documentStatus());
    }

    @Test
    void fullReceiveHappyPath() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());

        session.set(sessionFor(userDestination));
        TransferDocumentReceiveResult received =
                receiveFull(
                        sent.documentId(),
                        0L,
                        List.of(destAlloc(line, cellB1, "100")));

        assertEquals(DocumentStatus.CLOSED.name(), received.documentStatus());
        assertEquals(TransferSettlementState.SETTLED.name(), received.settlementState());
        assertEquals("ACCEPTED", received.decision());
        assertEquals(1L, received.operationalRevision());
        assertEquals(1, received.receiveOperationIds().size());
        assertNull(received.continuationDocumentId());

        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                        .compareTo(BigDecimal.ZERO));
        assertEquals(
                0,
                stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE)
                        .compareTo(new BigDecimal("100")));
        assertEquals(1, receiptItemCount(sent.documentId()));
        assertTrue(bundle.taskStates().findByDocumentId(sent.documentId()).isEmpty());
        assertEquals(
                DocumentStatus.CLOSED,
                bundle.documentEngine().findById(sent.documentId()).orElseThrow().status());
    }

    @Test
    void multiCellReceive() {
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
        TransferDocumentReceiveResult received =
                receiveFull(
                        sent.documentId(),
                        0L,
                        List.of(destAlloc(line, cellB1, "50"), destAlloc(line, cellB2, "50")));

        assertTrue(received.receiveOperationIds().size() >= 2);
        assertEquals(DocumentStatus.CLOSED.name(), received.documentStatus());
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                        .compareTo(BigDecimal.ZERO));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA2, materialA, StockState.IN_TRANSIT)
                        .compareTo(BigDecimal.ZERO));
        assertEquals(
                0,
                stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE)
                        .compareTo(new BigDecimal("50")));
        assertEquals(
                0,
                stockQty(destinationWarehouseId, cellB2, materialA, StockState.AVAILABLE)
                        .compareTo(new BigDecimal("50")));
        assertEquals(
                DocumentStatus.CLOSED,
                bundle.documentEngine().findById(sent.documentId()).orElseThrow().status());
    }

    @Test
    void multiLineReceive() {
        seedAvailable(materialA, cellA1, "30");
        seedAvailable(materialB, cellA2, "20");
        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, new BigDecimal("30"), 1),
                                        new TransferDocumentLineInput(
                                                null, materialB, new BigDecimal("20"), 2))));
        TransferDocumentSendResult sent =
                api.sendTransferDocument(
                        new SendTransferDocumentCommand(
                                created.documentId(),
                                documentVersion(created.documentId()),
                                created.payloadRevision(),
                                List.of(
                                        alloc(created, materialA, cellA1, "30"),
                                        alloc(created, materialB, cellA2, "20"))));

        session.set(sessionFor(userDestination));
        TransferDocumentReceiveResult received =
                receiveFull(
                        sent.documentId(),
                        0L,
                        List.of(
                                destAlloc(lineId(created, materialA), cellB1, "30"),
                                destAlloc(lineId(created, materialB), cellB2, "20")));

        assertEquals(DocumentStatus.CLOSED.name(), received.documentStatus());
        assertEquals(TransferSettlementState.SETTLED.name(), received.settlementState());
        assertEquals(
                0,
                stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE)
                        .compareTo(new BigDecimal("30")));
        assertEquals(
                0,
                stockQty(destinationWarehouseId, cellB2, materialB, StockState.AVAILABLE)
                        .compareTo(new BigDecimal("20")));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                        .compareTo(BigDecimal.ZERO));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA2, materialB, StockState.IN_TRANSIT)
                        .compareTo(BigDecimal.ZERO));
    }

    @Test
    void overReceiveRejected() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());
        PhysicalSnapshot before = snapshotPhysical(sent.documentId());

        session.set(sessionFor(userDestination));
        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        receiveFull(
                                sent.documentId(),
                                0L,
                                List.of(destAlloc(line, cellB1, "102"))));

        assertUnchangedAwaiting(sent.documentId(), before);
    }

    @Test
    void partialReceiveTransitionsToReturnPending() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());
        long payloadRevisionBefore =
                jdbc.queryForObject(
                        """
                        SELECT payload_revision FROM warehouse.transfer_document_payload
                         WHERE document_id = ?
                        """,
                        Long.class,
                        sent.documentId());

        session.set(sessionFor(userDestination));
        TransferDocumentReceiveResult received =
                receiveFull(
                        sent.documentId(),
                        0L,
                        List.of(destAlloc(line, cellB1, "98")));

        assertEquals(DocumentStatus.POSTED.name(), received.documentStatus());
        assertEquals(TransferSettlementState.RETURN_PENDING.name(), received.settlementState());
        assertEquals("ACCEPTED", received.decision());
        assertEquals(1L, received.operationalRevision());
        assertNotNull(received.continuationDocumentId());
        assertEquals("ACCEPTED", settlementDecision(sent.documentId()));

        assertEquals(
                DocumentStatus.POSTED,
                bundle.documentEngine().findById(sent.documentId()).orElseThrow().status());
        assertEquals(
                0,
                jdbc.queryForObject(
                                """
                                SELECT quantity FROM warehouse.transfer_document_lines
                                 WHERE document_id = ? AND id = ?
                                """,
                                BigDecimal.class,
                                sent.documentId(),
                                line)
                        .compareTo(new BigDecimal("100")));
        assertEquals(
                payloadRevisionBefore,
                jdbc.queryForObject(
                        """
                        SELECT payload_revision FROM warehouse.transfer_document_payload
                         WHERE document_id = ?
                        """,
                        Long.class,
                        sent.documentId()));
        assertEquals(
                0,
                stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE)
                        .compareTo(new BigDecimal("98")));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                        .compareTo(new BigDecimal("2")));

        TransferDocumentView continuation =
                api.getTransferDocument(received.continuationDocumentId());
        assertEquals(DocumentStatus.DRAFT.name(), continuation.documentStatus());
        assertEquals(sent.documentId(), continuation.continuationOfDocumentId());
        assertEquals(
                com.tmp.warehouse.domain.TransferContinuationReason.RECEIVE_SHORTFALL.name(),
                continuation.continuationReason());
        assertEquals(1, continuation.lines().size());
        assertEquals(0, continuation.lines().get(0).quantity().compareTo(new BigDecimal("2")));
        assertEquals(sourceWarehouseId, continuation.sourceWarehouseId());
        assertEquals(destinationWarehouseId, continuation.destinationWarehouseId());
    }

    @Test
    void allZeroReceiveRejected() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        PhysicalSnapshot before = snapshotPhysical(sent.documentId());

        session.set(sessionFor(userDestination));
        assertThrows(
                InvalidWarehouseStateException.class,
                () -> receiveFull(sent.documentId(), 0L, List.of()));

        assertUnchangedAwaiting(sent.documentId(), before);
    }

    @Test
    void wrongDestinationWarehouseCellRejected() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());
        PhysicalSnapshot before = snapshotPhysical(sent.documentId());

        session.set(sessionFor(userDestination));
        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        receiveFull(
                                sent.documentId(),
                                0L,
                                List.of(destAlloc(line, cellF1, "100"))));

        assertUnchangedAwaiting(sent.documentId(), before);
    }

    @Test
    void inactiveDestinationCellRejected() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());
        PhysicalSnapshot before = snapshotPhysical(sent.documentId());

        session.set(sessionFor(userDestination));
        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        receiveFull(
                                sent.documentId(),
                                0L,
                                List.of(destAlloc(line, cellBInactive, "100"))));

        assertUnchangedAwaiting(sent.documentId(), before);
    }

    @Test
    void sourceOnlyUserCannotReceive() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());

        session.set(sessionFor(userSourceOnly));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        receiveFull(
                                sent.documentId(),
                                0L,
                                List.of(destAlloc(line, cellB1, "100"))));
        assertEquals(
                DocumentStatus.POSTED,
                bundle.documentEngine().findById(sent.documentId()).orElseThrow().status());
        assertEquals(TransferSettlementState.AWAITING_RECEIPT.name(), settlementState(sent.documentId()));
    }

    @Test
    void destinationResponsibilityRequired() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());

        session.set(sessionFor(userUnrelated));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        receiveFull(
                                sent.documentId(),
                                0L,
                                List.of(destAlloc(line, cellB1, "100"))));
        assertEquals(
                DocumentStatus.POSTED,
                bundle.documentEngine().findById(sent.documentId()).orElseThrow().status());
    }

    @Test
    void receiverTaskProjection() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());

        session.set(sessionFor(userDestination));
        List<WarehouseTaskView> destTasks = api.listMyWarehouseTasks(null);
        assertEquals(1, destTasks.size());
        assertEquals(sent.documentId(), destTasks.get(0).documentId());
        assertEquals(WarehouseTaskKind.TRANSFER_RECEIPT, destTasks.get(0).taskKind());
        assertEquals(WarehouseTaskState.NEW, destTasks.get(0).taskState());
        assertEquals(TransferSettlementState.AWAITING_RECEIPT.name(), destTasks.get(0).settlementState());

        session.set(sessionFor(userSourceOnly));
        assertTrue(
                api.listMyWarehouseTasks(null).stream()
                        .noneMatch(
                                t ->
                                        t.documentId().equals(sent.documentId())
                                                && t.taskKind()
                                                        == WarehouseTaskKind.TRANSFER_RECEIPT));

        session.set(sessionFor(userDestination));
        WarehouseTaskView taken = api.takeTransferTaskInWork(sent.documentId());
        assertEquals(WarehouseTaskState.IN_WORK, taken.taskState());
        assertEquals(userDestination, taken.workingUserId());

        session.set(sessionFor(userDest2));
        WarehouseTaskView takeover = api.takeTransferTaskInWork(sent.documentId());
        assertEquals(userDest2, takeover.workingUserId());
        assertEquals(WarehouseTaskState.IN_WORK, takeover.taskState());

        TransferDocumentReceiveResult received =
                receiveFull(
                        sent.documentId(),
                        0L,
                        List.of(destAlloc(line, cellB1, "100")));
        assertEquals(DocumentStatus.CLOSED.name(), received.documentStatus());

        session.set(sessionFor(userDestination));
        assertTrue(
                api.listMyWarehouseTasks(null).stream()
                        .noneMatch(t -> t.documentId().equals(sent.documentId())));
        session.set(sessionFor(userDest2));
        assertTrue(
                api.listMyWarehouseTasks(null).stream()
                        .noneMatch(t -> t.documentId().equals(sent.documentId())));
        assertTrue(bundle.taskStates().findByDocumentId(sent.documentId()).isEmpty());
    }

    @Test
    void legacyReceiveBypassGuard() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID sendOpId = sent.sendOperationIds().get(0);
        BigDecimal inTransitBefore =
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT);
        BigDecimal destBefore =
                stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE);

        session.set(sessionFor(userDestination));
        assertThrows(
                InvalidWarehouseStateException.class, () -> api.receiveTransfer(sendOpId));

        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                        .compareTo(inTransitBefore));
        assertEquals(
                0,
                stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE)
                        .compareTo(destBefore));
        assertEquals(
                DocumentStatus.POSTED,
                bundle.documentEngine().findById(sent.documentId()).orElseThrow().status());
    }

    @Test
    void legacyOneLineReceiveStillWorks() {
        seedAvailable(materialA, cellA1, "25");
        var draft =
                api.createTransferDraft(
                        new CreateTransferDraftCommand(
                                materialA,
                                new BigDecimal("25"),
                                sourceWarehouseId,
                                cellA1,
                                destinationWarehouseId,
                                cellB1));
        var sent = api.sendTransfer(draft.operationId());

        session.set(sessionFor(userDestination));
        var received = api.receiveTransfer(sent.operationId());
        assertNotNull(received.operationId());
        assertEquals(
                0,
                stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE)
                        .compareTo(new BigDecimal("25")));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                        .compareTo(BigDecimal.ZERO));
        assertEquals("RECEIVED", api.getTransferStatus(draft.operationId()).status());

        assertThrows(
                InvalidWarehouseStateException.class,
                () -> api.receiveTransfer(sent.operationId()));
        assertEquals(
                0,
                stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE)
                        .compareTo(new BigDecimal("25")));
    }

    @Test
    void documentManagedStatusNotFalsePendingAfterSettle() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID sendOpId = sent.sendOperationIds().get(0);
        UUID line = singleLineId(sent.documentId());

        TransferStatusView afterSend = api.getTransferStatus(sendOpId);
        assertEquals("SENT", afterSend.status());
        assertNull(afterSend.receiveOperationId());

        session.set(sessionFor(userDestination));
        receiveFull(sent.documentId(), 0L, List.of(destAlloc(line, cellB1, "100")));

        TransferStatusView afterReceive = api.getTransferStatus(sendOpId);
        assertEquals("RECEIVED", afterReceive.status());
        assertNull(afterReceive.receiveOperationId());
    }

    @Test
    void directCloseBeforeReceiveRejected() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");

        assertThrows(
                InvalidWarehouseStateException.class,
                () -> bundle.documentEngine().closeDocument(sent.documentId()));

        assertEquals(
                DocumentStatus.POSTED,
                bundle.documentEngine().findById(sent.documentId()).orElseThrow().status());
        assertEquals(TransferSettlementState.AWAITING_RECEIPT.name(), settlementState(sent.documentId()));
        assertEquals(0L, operationalRevision(sent.documentId()));
    }

    @Test
    void concurrentDoubleReceive() throws Exception {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());
        ReceiveTransferDocumentCommand command =
                new ReceiveTransferDocumentCommand(
                        sent.documentId(),
                        0L,
                        List.of(destAlloc(line, cellB1, "100")));

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
                                    session.set(sessionFor(userDestination));
                                    permissions.set(
                                            Set.of(
                                                    WarehousePermissions.WAREHOUSE_TRANSFER,
                                                    WarehousePermissions.WAREHOUSE_VIEW));
                                    ready.countDown();
                                    start.await(10, TimeUnit.SECONDS);
                                    try {
                                        api.receiveTransferDocument(command);
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
        assertEquals(
                DocumentStatus.CLOSED,
                bundle.documentEngine().findById(sent.documentId()).orElseThrow().status());
        assertEquals(
                0,
                stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE)
                        .compareTo(new BigDecimal("100")));
        assertEquals(
                1,
                jdbc.queryForObject(
                                """
                                SELECT COUNT(*) FROM warehouse.warehouse_operations
                                 WHERE operation_type = 'TRANSFER_RECEIVE'
                                   AND status = 'COMPLETED'
                                """,
                                Integer.class)
                        .intValue());
        assertEquals(TransferSettlementState.SETTLED.name(), settlementState(sent.documentId()));
    }

    @Test
    void rollbackOnLaterReceiveSegmentFailure() {
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

        jdbc.update(
                """
                UPDATE warehouse.stock_positions
                   SET quantity = 0
                 WHERE warehouse_id = ?
                   AND storage_cell_id = ?
                   AND material_reference_id = ?
                   AND stock_state = 'IN_TRANSIT'
                """,
                sourceWarehouseId,
                cellA2,
                materialA);

        long receiveOpsBefore = countReceiveOps();
        BigDecimal destB1Before =
                stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE);
        BigDecimal destB2Before =
                stockQty(destinationWarehouseId, cellB2, materialA, StockState.AVAILABLE);

        session.set(sessionFor(userDestination));
        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        receiveFull(
                                sent.documentId(),
                                0L,
                                List.of(
                                        destAlloc(line, cellB1, "60"),
                                        destAlloc(line, cellB2, "40"))));

        assertEquals(
                DocumentStatus.POSTED,
                bundle.documentEngine().findById(sent.documentId()).orElseThrow().status());
        assertEquals(TransferSettlementState.AWAITING_RECEIPT.name(), settlementState(sent.documentId()));
        assertEquals(0L, operationalRevision(sent.documentId()));
        assertEquals(0, receiptItemCount(sent.documentId()));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                        .compareTo(new BigDecimal("60")));
        assertEquals(
                0,
                stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE)
                        .compareTo(destB1Before));
        assertEquals(
                0,
                stockQty(destinationWarehouseId, cellB2, materialA, StockState.AVAILABLE)
                        .compareTo(destB2Before));
        assertEquals(receiveOpsBefore, countReceiveOps());
    }

    @Test
    void conservationAfterFullReceive() {
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
        receiveFull(
                sent.documentId(),
                0L,
                List.of(destAlloc(line, cellB1, "55"), destAlloc(line, cellB2, "45")));

        List<UUID> allocationIds =
                jdbc.query(
                        """
                        SELECT id FROM warehouse.transfer_document_send_allocation
                         WHERE document_id = ?
                         ORDER BY created_at, id
                        """,
                        (rs, rowNum) -> (UUID) rs.getObject("id"),
                        sent.documentId());
        for (UUID allocationId : allocationIds) {
            BigDecimal allocated =
                    jdbc.queryForObject(
                            """
                            SELECT quantity FROM warehouse.transfer_document_send_allocation
                             WHERE id = ?
                            """,
                            BigDecimal.class,
                            allocationId);
            BigDecimal receivedSum =
                    jdbc.queryForObject(
                            """
                            SELECT COALESCE(SUM(quantity), 0)
                              FROM warehouse.transfer_receipt_settlement_item
                             WHERE send_allocation_id = ?
                            """,
                            BigDecimal.class,
                            allocationId);
            assertEquals(0, allocated.compareTo(receivedSum));
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

    private TransferDocumentReceiveResult receiveFull(
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

    private int settlementCount(UUID documentId) {
        return jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM warehouse.transfer_document_settlement
                         WHERE document_id = ?
                        """,
                        Integer.class,
                        documentId)
                .intValue();
    }

    private int totalSettlementCount() {
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.transfer_document_settlement", Integer.class)
                .intValue();
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

    private long countReceiveOps() {
        return jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM warehouse.warehouse_operations
                         WHERE operation_type = 'TRANSFER_RECEIVE'
                        """,
                        Long.class)
                .longValue();
    }

    private PhysicalSnapshot snapshotPhysical(UUID documentId) {
        return new PhysicalSnapshot(
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT),
                stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE),
                stockQty(destinationWarehouseId, cellB2, materialA, StockState.AVAILABLE),
                countReceiveOps(),
                receiptItemCount(documentId),
                countMoves());
    }

    private void assertUnchangedAwaiting(UUID documentId, PhysicalSnapshot before) {
        assertEquals(
                DocumentStatus.POSTED,
                bundle.documentEngine().findById(documentId).orElseThrow().status());
        assertEquals(TransferSettlementState.AWAITING_RECEIPT.name(), settlementState(documentId));
        assertEquals(0L, operationalRevision(documentId));
        assertEquals(0, before.inTransitA1().compareTo(
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)));
        assertEquals(0, before.destB1Available().compareTo(
                stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE)));
        assertEquals(0, before.destB2Available().compareTo(
                stockQty(destinationWarehouseId, cellB2, materialA, StockState.AVAILABLE)));
        assertEquals(before.receiveOps(), countReceiveOps());
        assertEquals(before.receiptItems(), receiptItemCount(documentId));
        assertEquals(before.moves(), countMoves());
    }

    private long countMoves() {
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.warehouse_movements", Long.class)
                .longValue();
    }

    private record PhysicalSnapshot(
            BigDecimal inTransitA1,
            BigDecimal destB1Available,
            BigDecimal destB2Available,
            long receiveOps,
            int receiptItems,
            long moves) {}

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
}
