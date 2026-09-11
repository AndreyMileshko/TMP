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
import com.tmp.warehouse.api.WarehouseApi.SendTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentDestinationAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentLineInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentLineView;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReceiveResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSendResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSourceAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentView;
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
import java.sql.Timestamp;
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
 * Stage 3.5.8.2: partial document receive → RETURN_PENDING + RECEIVE_SHORTFALL continuation +
 * RETURN_MATERIALS inbox projection.
 */
@Testcontainers
class WarehouseTransferDocumentPartialReceiveIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-09T06:00:00Z"), ZoneOffset.UTC);

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
    private UUID materialC;
    private UUID cellA1;
    private UUID cellA2;
    private UUID cellB1;
    private UUID cellB2;

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
        MaterialReference matC =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc, CLOCK, MaterialReference.legacyArticle("MAT-C"));
        materialA = matA.id().value();
        materialB = matB.id().value();
        materialC = matC.id().value();

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
    }

    @Test
    void partialSimpleReceiveShortfall() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());

        session.set(sessionFor(userDestination));
        api.takeTransferTaskInWork(sent.documentId());
        TransferDocumentReceiveResult received =
                receiveDoc(sent.documentId(), 0L, List.of(destAlloc(line, cellB1, "98")));

        assertEquals(DocumentStatus.POSTED.name(), received.documentStatus());
        assertEquals(TransferSettlementState.RETURN_PENDING.name(), received.settlementState());
        assertEquals("ACCEPTED", received.decision());
        assertEquals(1L, received.operationalRevision());
        assertNotNull(received.continuationDocumentId());

        assertEquals(
                0,
                lineQty(sent.documentId(), line).compareTo(new BigDecimal("100")));
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
                TransferContinuationReason.RECEIVE_SHORTFALL.name(),
                continuation.continuationReason());
        assertEquals(1, continuation.lines().size());
        assertEquals(0, continuation.lines().get(0).quantity().compareTo(new BigDecimal("2")));
        assertEquals(sourceWarehouseId, continuation.sourceWarehouseId());
        assertEquals(destinationWarehouseId, continuation.destinationWarehouseId());

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
                                                && t.taskState() == WarehouseTaskState.NEW));
        assertTrue(
                sourceTasks.stream()
                        .anyMatch(
                                t ->
                                        t.documentId()
                                                        .equals(received.continuationDocumentId())
                                                && t.taskKind()
                                                        == WarehouseTaskKind.TRANSFER_PREPARATION
                                                && t.taskState() == WarehouseTaskState.NEW));

        List<com.tmp.warehouse.api.WarehouseApi.TransferDocumentReturnPlanItem> returnPlan =
                api.listTransferDocumentReturnPlan(sent.documentId());
        assertEquals(1, returnPlan.size());
        assertEquals(line, returnPlan.get(0).lineId());
        assertEquals(materialA, returnPlan.get(0).materialReferenceId());
        assertEquals(0, returnPlan.get(0).outstandingQuantity().compareTo(new BigDecimal("2")));
        assertEquals(cellA1, returnPlan.get(0).defaultReturnStorageCellId());
        assertEquals("A1", returnPlan.get(0).defaultReturnStorageCellCode());
    }

    @Test
    void suggestTransferDocumentSourceAllocationsScopedToSourceWarehouse() {
        seedAvailable(materialA, cellA1, "40");
        seedAvailable(materialA, cellA2, "30");
        UUID destCell =
                api.createStorageCell(
                                new CreateStorageCellCommand(
                                        destinationWarehouseId, "DST-STOCK", true))
                        .storageCellId();
        api.assignUserToWarehouse(destinationWarehouseId, userSource);
        seedAvailableAt(destinationWarehouseId, destCell, materialA, "1000");

        TransferDocumentView draft =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, new BigDecimal("50"), 1))));

        List<com.tmp.warehouse.api.WarehouseApi.TransferDocumentSourceSuggestionLine> lines =
                api.suggestTransferDocumentSourceAllocations(draft.documentId());
        assertEquals(1, lines.size());
        assertEquals(draft.lines().get(0).lineId(), lines.get(0).lineId());
        assertEquals(materialA, lines.get(0).materialReferenceId());
        assertEquals(0, lines.get(0).requiredQuantity().compareTo(new BigDecimal("50")));
        assertEquals(2, lines.get(0).suggestions().size());
        assertTrue(
                lines.get(0).suggestions().stream()
                        .allMatch(
                                s ->
                                        s.storageCellId().equals(cellA1)
                                                || s.storageCellId().equals(cellA2)));
        assertTrue(
                lines.get(0).suggestions().stream()
                        .noneMatch(s -> s.storageCellId().equals(destCell)));
        assertEquals(cellA1, lines.get(0).suggestions().get(0).storageCellId());
    }

    @Test
    void partialMultiLineWithZeroLine() {
        seedAvailable(materialA, cellA1, "100");
        seedAvailable(materialB, cellA1, "20");
        seedAvailable(materialC, cellA2, "5");
        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, new BigDecimal("100"), 1),
                                        new TransferDocumentLineInput(
                                                null, materialB, new BigDecimal("20"), 2),
                                        new TransferDocumentLineInput(
                                                null, materialC, new BigDecimal("5"), 3))));
        TransferDocumentSendResult sent =
                api.sendTransferDocument(
                        new SendTransferDocumentCommand(
                                created.documentId(),
                                documentVersion(created.documentId()),
                                created.payloadRevision(),
                                List.of(
                                        alloc(created, materialA, cellA1, "100"),
                                        alloc(created, materialB, cellA1, "20"),
                                        alloc(created, materialC, cellA2, "5"))));

        UUID lineA = lineId(created, materialA);
        UUID lineB = lineId(created, materialB);
        UUID lineC = lineId(created, materialC);

        session.set(sessionFor(userDestination));
        TransferDocumentReceiveResult received =
                receiveDoc(
                        sent.documentId(),
                        0L,
                        List.of(
                                destAlloc(lineA, cellB1, "98"),
                                destAlloc(lineB, cellB2, "20")));

        assertEquals(TransferSettlementState.RETURN_PENDING.name(), received.settlementState());
        assertNotNull(received.continuationDocumentId());

        assertEquals(0, lineQty(sent.documentId(), lineA).compareTo(new BigDecimal("100")));
        assertEquals(0, lineQty(sent.documentId(), lineB).compareTo(new BigDecimal("20")));
        assertEquals(0, lineQty(sent.documentId(), lineC).compareTo(new BigDecimal("5")));

        TransferDocumentView continuation =
                api.getTransferDocument(received.continuationDocumentId());
        assertEquals(2, continuation.lines().size());
        Map<UUID, BigDecimal> contByMaterial =
                continuation.lines().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        TransferDocumentLineView::materialReferenceId,
                                        TransferDocumentLineView::quantity));
        assertEquals(0, contByMaterial.get(materialA).compareTo(new BigDecimal("2")));
        assertEquals(0, contByMaterial.get(materialC).compareTo(new BigDecimal("5")));
        assertFalse(contByMaterial.containsKey(materialB));

        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                        .compareTo(new BigDecimal("2")));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialB, StockState.IN_TRANSIT)
                        .compareTo(BigDecimal.ZERO));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA2, materialC, StockState.IN_TRANSIT)
                        .compareTo(new BigDecimal("5")));
        BigDecimal outstanding =
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                        .add(stockQty(sourceWarehouseId, cellA2, materialC, StockState.IN_TRANSIT));
        assertEquals(0, outstanding.compareTo(new BigDecimal("7")));
    }

    @Test
    void partialMultiCellMapping() {
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

        // Fixed test Clock stamps both send allocations with the same created_at; production
        // ordering is (created_at, id). Stagger timestamps so cellA1 (60) precedes cellA2 (40),
        // matching WarehouseTransferReceiveMappingTest and the send command list order.
        Instant base = CLOCK.instant();
        jdbc.update(
                """
                UPDATE warehouse.transfer_document_send_allocation
                   SET created_at = ?
                 WHERE document_id = ?
                   AND source_storage_cell_id = ?
                """,
                Timestamp.from(base),
                sent.documentId(),
                cellA1);
        jdbc.update(
                """
                UPDATE warehouse.transfer_document_send_allocation
                   SET created_at = ?
                 WHERE document_id = ?
                   AND source_storage_cell_id = ?
                """,
                Timestamp.from(base.plusMillis(1)),
                sent.documentId(),
                cellA2);

        List<Map<String, Object>> sendAllocRows =
                jdbc.queryForList(
                        """
                        SELECT id, source_storage_cell_id, quantity, created_at
                          FROM warehouse.transfer_document_send_allocation
                         WHERE document_id = ?
                         ORDER BY created_at, id
                        """,
                        sent.documentId());
        assertEquals(2, sendAllocRows.size());
        UUID s1 = (UUID) sendAllocRows.get(0).get("id");
        UUID s2 = (UUID) sendAllocRows.get(1).get("id");
        assertEquals(cellA1, sendAllocRows.get(0).get("source_storage_cell_id"));
        assertEquals(cellA2, sendAllocRows.get(1).get("source_storage_cell_id"));
        assertEquals(
                0,
                ((BigDecimal) sendAllocRows.get(0).get("quantity")).compareTo(new BigDecimal("60")));
        assertEquals(
                0,
                ((BigDecimal) sendAllocRows.get(1).get("quantity")).compareTo(new BigDecimal("40")));

        session.set(sessionFor(userDestination));
        TransferDocumentReceiveResult received =
                receiveDoc(
                        sent.documentId(),
                        0L,
                        List.of(destAlloc(line, cellB1, "50"), destAlloc(line, cellB2, "25")));

        assertEquals(TransferSettlementState.RETURN_PENDING.name(), received.settlementState());
        assertNotNull(received.continuationDocumentId());
        assertEquals(
                0,
                stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE)
                        .add(
                                stockQty(
                                        destinationWarehouseId,
                                        cellB2,
                                        materialA,
                                        StockState.AVAILABLE))
                        .compareTo(new BigDecimal("75")));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                        .add(
                                stockQty(
                                        sourceWarehouseId, cellA2, materialA, StockState.IN_TRANSIT))
                        .compareTo(new BigDecimal("25")));

        BigDecimal acceptedS1 = acceptedForSendAllocation(s1);
        BigDecimal acceptedS2 = acceptedForSendAllocation(s2);
        assertEquals(0, acceptedS1.compareTo(new BigDecimal("60")));
        assertEquals(0, acceptedS2.compareTo(new BigDecimal("15")));
        assertEquals(0, acceptedS1.add(acceptedS2).compareTo(new BigDecimal("75")));
        assertTrue(acceptedS1.compareTo(new BigDecimal("60")) <= 0);
        assertTrue(acceptedS2.compareTo(new BigDecimal("40")) <= 0);

        TransferDocumentView continuation =
                api.getTransferDocument(received.continuationDocumentId());
        assertEquals(1, continuation.lines().size());
        assertEquals(0, continuation.lines().get(0).quantity().compareTo(new BigDecimal("25")));
    }

    @Test
    void lineZeroOtherAccepted() {
        seedAvailable(materialA, cellA1, "100");
        seedAvailable(materialB, cellA2, "20");
        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, new BigDecimal("100"), 1),
                                        new TransferDocumentLineInput(
                                                null, materialB, new BigDecimal("20"), 2))));
        TransferDocumentSendResult sent =
                api.sendTransferDocument(
                        new SendTransferDocumentCommand(
                                created.documentId(),
                                documentVersion(created.documentId()),
                                created.payloadRevision(),
                                List.of(
                                        alloc(created, materialA, cellA1, "100"),
                                        alloc(created, materialB, cellA2, "20"))));

        session.set(sessionFor(userDestination));
        TransferDocumentReceiveResult received =
                receiveDoc(
                        sent.documentId(),
                        0L,
                        List.of(destAlloc(lineId(created, materialA), cellB1, "100")));

        assertEquals(TransferSettlementState.RETURN_PENDING.name(), received.settlementState());
        assertNotNull(received.continuationDocumentId());
        TransferDocumentView continuation =
                api.getTransferDocument(received.continuationDocumentId());
        assertEquals(1, continuation.lines().size());
        assertEquals(materialB, continuation.lines().get(0).materialReferenceId());
        assertEquals(0, continuation.lines().get(0).quantity().compareTo(new BigDecimal("20")));
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA2, materialB, StockState.IN_TRANSIT)
                        .compareTo(new BigDecimal("20")));
        assertEquals(
                0,
                stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE)
                        .compareTo(new BigDecimal("100")));
    }

    @Test
    void destinationOnlyReceiverCreatesContinuationWithoutSourceResponsibility() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());

        session.set(sessionFor(userDestination));
        TransferDocumentReceiveResult received =
                receiveDoc(sent.documentId(), 0L, List.of(destAlloc(line, cellB1, "98")));
        assertNotNull(received.continuationDocumentId());
        assertEquals(TransferSettlementState.RETURN_PENDING.name(), received.settlementState());

        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.createTransferDocument(
                                new CreateTransferDocumentCommand(
                                        sourceWarehouseId,
                                        destinationWarehouseId,
                                        List.of(
                                                new TransferDocumentLineInput(
                                                        null,
                                                        materialA,
                                                        new BigDecimal("1"),
                                                        1)))));
    }

    @Test
    void returnMaterialsTaskProjectionAndTakeover() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());

        session.set(sessionFor(userDestination));
        receiveDoc(sent.documentId(), 0L, List.of(destAlloc(line, cellB1, "98")));

        session.set(sessionFor(userDestination));
        assertTrue(
                api.listMyWarehouseTasks(null).stream()
                        .noneMatch(t -> t.taskKind() == WarehouseTaskKind.RETURN_MATERIALS));
        session.set(sessionFor(userDest2));
        assertTrue(
                api.listMyWarehouseTasks(null).stream()
                        .noneMatch(t -> t.taskKind() == WarehouseTaskKind.RETURN_MATERIALS));

        session.set(sessionFor(userSource));
        List<WarehouseTaskView> sourceTasks = api.listMyWarehouseTasks(null);
        assertTrue(
                sourceTasks.stream()
                        .anyMatch(
                                t ->
                                        t.documentId().equals(sent.documentId())
                                                && t.taskKind()
                                                        == WarehouseTaskKind.RETURN_MATERIALS
                                                && t.taskState() == WarehouseTaskState.NEW));

        WarehouseTaskView taken = api.takeTransferTaskInWork(sent.documentId());
        assertEquals(WarehouseTaskKind.RETURN_MATERIALS, taken.taskKind());
        assertEquals(WarehouseTaskState.IN_WORK, taken.taskState());
        assertEquals(userSource, taken.workingUserId());

        session.set(sessionFor(userSourceOnly));
        WarehouseTaskView takeover = api.takeTransferTaskInWork(sent.documentId());
        assertEquals(userSourceOnly, takeover.workingUserId());
        assertEquals(WarehouseTaskState.IN_WORK, takeover.taskState());

        session.set(sessionFor(userDestination));
        assertThrows(
                AccessDeniedException.class, () -> api.takeTransferTaskInWork(sent.documentId()));
    }

    @Test
    void directCloseWhileReturnPendingRejected() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());

        session.set(sessionFor(userDestination));
        receiveDoc(sent.documentId(), 0L, List.of(destAlloc(line, cellB1, "98")));

        assertThrows(
                InvalidWarehouseStateException.class,
                () -> bundle.documentEngine().closeDocument(sent.documentId()));

        assertEquals(
                DocumentStatus.POSTED,
                bundle.documentEngine().findById(sent.documentId()).orElseThrow().status());
        assertEquals(
                TransferSettlementState.RETURN_PENDING.name(), settlementState(sent.documentId()));
    }

    @Test
    void furtherReceiveAfterReturnPendingRejected() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());

        session.set(sessionFor(userDestination));
        receiveDoc(sent.documentId(), 0L, List.of(destAlloc(line, cellB1, "98")));
        int receiptItemsAfterFirst = receiptItemCount(sent.documentId());

        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        receiveDoc(
                                sent.documentId(),
                                1L,
                                List.of(destAlloc(line, cellB2, "1"))));
        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        receiveDoc(
                                sent.documentId(),
                                0L,
                                List.of(destAlloc(line, cellB2, "1"))));

        assertEquals(receiptItemsAfterFirst, receiptItemCount(sent.documentId()));
        assertEquals(
                TransferSettlementState.RETURN_PENDING.name(), settlementState(sent.documentId()));
    }

    @Test
    void sendShortfallThenReceiveShortfallIndependent() {
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

        assertEquals(DocumentStatus.POSTED.name(), sent.documentStatus());
        assertNotNull(sent.continuationDocumentId());
        UUID shortfallContinuationId = sent.continuationDocumentId();
        TransferDocumentView shortfallContinuation =
                api.getTransferDocument(shortfallContinuationId);
        assertEquals(
                TransferContinuationReason.SHORTFALL.name(),
                shortfallContinuation.continuationReason());
        assertEquals(
                0, shortfallContinuation.lines().get(0).quantity().compareTo(new BigDecimal("2")));

        TransferDocumentView posted = api.getTransferDocument(sent.documentId());
        assertEquals(1, posted.lines().size());
        assertEquals(0, posted.lines().get(0).quantity().compareTo(new BigDecimal("98")));
        UUID postedLine = posted.lines().get(0).lineId();

        session.set(sessionFor(userDestination));
        TransferDocumentReceiveResult received =
                receiveDoc(
                        sent.documentId(),
                        0L,
                        List.of(destAlloc(postedLine, cellB1, "95")));

        assertEquals(TransferSettlementState.RETURN_PENDING.name(), received.settlementState());
        assertNotNull(received.continuationDocumentId());
        assertFalse(received.continuationDocumentId().equals(shortfallContinuationId));

        TransferDocumentView receiveContinuation =
                api.getTransferDocument(received.continuationDocumentId());
        assertEquals(
                TransferContinuationReason.RECEIVE_SHORTFALL.name(),
                receiveContinuation.continuationReason());
        assertEquals(
                0, receiveContinuation.lines().get(0).quantity().compareTo(new BigDecimal("3")));

        TransferDocumentView shortfallUnchanged = api.getTransferDocument(shortfallContinuationId);
        assertEquals(DocumentStatus.DRAFT.name(), shortfallUnchanged.documentStatus());
        assertEquals(
                TransferContinuationReason.SHORTFALL.name(),
                shortfallUnchanged.continuationReason());
        assertEquals(
                0, shortfallUnchanged.lines().get(0).quantity().compareTo(new BigDecimal("2")));
    }

    @Test
    void partialReceiveContinuationRollback() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());

        session.set(sessionFor(userDestination));
        api.takeTransferTaskInWork(sent.documentId());
        assertEquals(
                userDestination,
                bundle.taskStates().findByDocumentId(sent.documentId()).orElseThrow().workingUserId());

        long receiveOpsBefore = countReceiveOps();
        BigDecimal destBefore =
                stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE);
        BigDecimal transitBefore =
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT);
        int docsBefore = documentCount();

        TransferDocumentSettlementRepository failingSettlements =
                new FailingReturnPendingSettlementRepository(bundle.settlements());
        WarehouseTransferReceiveService failingReceive =
                new WarehouseTransferReceiveService(
                        bundle.documentEngine(),
                        bundle.transferDocuments(),
                        bundle.transferDocumentService(),
                        failingSettlements,
                        bundle.sendAllocations(),
                        bundle.receiptItems(),
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
                        failingReceive.receive(
                                new WarehouseTransferReceiveService.ReceiveCommand(
                                        sent.documentId(),
                                        0L,
                                        List.of(
                                                new WarehouseTransferReceiveService
                                                        .DestinationAllocationInput(
                                                        line, cellB1, new BigDecimal("98"))))));

        assertEquals(
                DocumentStatus.POSTED,
                bundle.documentEngine().findById(sent.documentId()).orElseThrow().status());
        assertEquals(
                TransferSettlementState.AWAITING_RECEIPT.name(), settlementState(sent.documentId()));
        assertEquals(0L, operationalRevision(sent.documentId()));
        assertEquals(0, receiptItemCount(sent.documentId()));
        assertEquals(receiveOpsBefore, countReceiveOps());
        assertEquals(
                0,
                stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                        .compareTo(transitBefore));
        assertEquals(
                0,
                stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE)
                        .compareTo(destBefore));
        assertEquals(docsBefore, documentCount());
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
        assertEquals(
                userDestination,
                bundle.taskStates().findByDocumentId(sent.documentId()).orElseThrow().workingUserId());
    }

    @Test
    void concurrentPartialAndFullReceive() throws Exception {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());

        ReceiveTransferDocumentCommand partial =
                new ReceiveTransferDocumentCommand(
                        sent.documentId(), 0L, List.of(destAlloc(line, cellB1, "98")));
        ReceiveTransferDocumentCommand full =
                new ReceiveTransferDocumentCommand(
                        sent.documentId(), 0L, List.of(destAlloc(line, cellB1, "100")));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        AtomicReference<TransferDocumentReceiveResult> successResult = new AtomicReference<>();
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
                                    successResult.compareAndSet(
                                            null, api.receiveTransferDocument(partial));
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
                                    successResult.compareAndSet(
                                            null, api.receiveTransferDocument(full));
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
        TransferDocumentReceiveResult winner = successResult.get();
        assertNotNull(winner);

        int receiveShortfallContinuations =
                jdbc.queryForObject(
                                """
                                SELECT COUNT(*) FROM warehouse.transfer_document_payload
                                 WHERE continuation_of_document_id = ?
                                   AND continuation_reason = 'RECEIVE_SHORTFALL'
                                """,
                                Integer.class,
                                sent.documentId())
                        .intValue();

        if (TransferSettlementState.RETURN_PENDING.name().equals(winner.settlementState())) {
            assertEquals(DocumentStatus.POSTED.name(), winner.documentStatus());
            assertEquals(1, receiveShortfallContinuations);
            assertNotNull(winner.continuationDocumentId());
            assertEquals(
                    0,
                    stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE)
                            .compareTo(new BigDecimal("98")));
            assertEquals(
                    0,
                    stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                            .compareTo(new BigDecimal("2")));
        } else {
            assertEquals(TransferSettlementState.SETTLED.name(), winner.settlementState());
            assertEquals(DocumentStatus.CLOSED.name(), winner.documentStatus());
            assertNull(winner.continuationDocumentId());
            assertEquals(0, receiveShortfallContinuations);
            assertEquals(
                    0,
                    stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE)
                            .compareTo(new BigDecimal("100")));
            assertEquals(
                    0,
                    stockQty(sourceWarehouseId, cellA1, materialA, StockState.IN_TRANSIT)
                            .compareTo(BigDecimal.ZERO));
        }

        assertTrue(
                stockQty(destinationWarehouseId, cellB1, materialA, StockState.AVAILABLE)
                                .compareTo(new BigDecimal("100"))
                        <= 0);
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
    }

    @Test
    void fullReceiveRegressionStillCloses() {
        TransferDocumentSendResult sent = sendPostedDocument(materialA, cellA1, "100");
        UUID line = singleLineId(sent.documentId());

        session.set(sessionFor(userDestination));
        TransferDocumentReceiveResult received =
                receiveDoc(sent.documentId(), 0L, List.of(destAlloc(line, cellB1, "100")));

        assertEquals(DocumentStatus.CLOSED.name(), received.documentStatus());
        assertEquals(TransferSettlementState.SETTLED.name(), received.settlementState());
        assertNull(received.continuationDocumentId());

        session.set(sessionFor(userSource));
        assertTrue(
                api.listMyWarehouseTasks(null).stream()
                        .noneMatch(
                                t ->
                                        t.documentId().equals(sent.documentId())
                                                && t.taskKind()
                                                        == WarehouseTaskKind.RETURN_MATERIALS));
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
        seedAvailableAt(sourceWarehouseId, cellId, materialId, qty);
    }

    private void seedAvailableAt(
            UUID warehouseId, UUID cellId, UUID materialId, String qty) {
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
                        warehouseId,
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

    private BigDecimal lineQty(UUID documentId, UUID lineId) {
        return jdbc.queryForObject(
                """
                SELECT quantity FROM warehouse.transfer_document_lines
                 WHERE document_id = ? AND id = ?
                """,
                BigDecimal.class,
                documentId,
                lineId);
    }

    private BigDecimal acceptedForSendAllocation(UUID sendAllocationId) {
        return jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(quantity), 0)
                  FROM warehouse.transfer_receipt_settlement_item
                 WHERE send_allocation_id = ?
                """,
                BigDecimal.class,
                sendAllocationId);
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

    private int documentCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM documents.documents", Integer.class)
                .intValue();
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

    /**
     * Delegates all settlement methods except {@code markAcceptedAndReturnPending}, which fails
     * after earlier receive steps have already mutated in the same TX.
     */
    private static final class FailingReturnPendingSettlementRepository
            implements TransferDocumentSettlementRepository {

        private final TransferDocumentSettlementRepository delegate;

        private FailingReturnPendingSettlementRepository(
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
            throw new RuntimeException("forced markAcceptedAndReturnPending failure");
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
            delegate.markReturnedAndSettled(documentId, expectedOperationalRevision, updated);
        }
    }
}
