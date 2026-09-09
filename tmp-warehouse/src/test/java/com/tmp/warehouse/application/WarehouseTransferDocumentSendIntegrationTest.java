package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.tmp.warehouse.api.WarehouseApi.SendTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentLineInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSendResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSourceAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentView;
import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.TransferDocumentOptimisticLockException;
import com.tmp.warehouse.domain.TransferOperationContext;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.WarehouseOperationStatus;
import com.tmp.warehouse.domain.WarehouseOperationType;
import com.tmp.warehouse.security.WarehousePermissions;
import com.tmp.warehouse.testsupport.WarehouseIntegrationTestSupport;
import com.tmp.warehouse.testsupport.WarehouseJdbcTestSupport;
import java.math.BigDecimal;
import java.sql.Timestamp;
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
 * Stage 3.5.6: atomic physical multi-line Transfer Document SEND — happy path, rollback,
 * validation, concurrency, traceability, V39 schema, direct-post protection.
 */
@Testcontainers
class WarehouseTransferDocumentSendIntegrationTest {

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
    private UUID userUnrelated;
    private UUID sourceWarehouseId;
    private UUID destinationWarehouseId;
    private UUID materialA;
    private UUID materialB;
    private UUID cellA1;
    private UUID cellA2;
    private UUID cellA3;
    private UUID cellForeign;

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
        sourceWarehouseId = source.warehouseId();
        destinationWarehouseId = destination.warehouseId();
        api.assignUserToWarehouse(sourceWarehouseId, userSource);
        api.assignUserToWarehouse(destinationWarehouseId, userDestination);

        MaterialReference matA =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc, CLOCK, MaterialReference.legacyArticle("MAT-A"));
        MaterialReference matB =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc, CLOCK, MaterialReference.legacyArticle("MAT-B"));
        materialA = matA.id().value();
        materialB = matB.id().value();

        cellA1 = api.createStorageCell(new CreateStorageCellCommand(sourceWarehouseId, "A1", true))
                .storageCellId();
        cellA2 = api.createStorageCell(new CreateStorageCellCommand(sourceWarehouseId, "A2", true))
                .storageCellId();
        cellA3 = api.createStorageCell(new CreateStorageCellCommand(sourceWarehouseId, "A3", true))
                .storageCellId();
        cellForeign =
                api.createStorageCell(
                                new CreateStorageCellCommand(destinationWarehouseId, "B1", true))
                        .storageCellId();
    }

    @Test
    void v39SchemaExistsWithConstraints() {
        Integer tables =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.tables
                         WHERE table_schema = 'warehouse'
                           AND table_name = 'transfer_document_send_allocation'
                        """,
                        Integer.class);
        assertEquals(1, tables);

        UUID docId = UUID.randomUUID();
        Instant now = CLOCK.instant();
        jdbc.update(
                """
                INSERT INTO warehouse.transfer_document_payload (
                    document_id, source_warehouse_id, destination_warehouse_id,
                    payload_schema_version, payload_revision, created_at, updated_at)
                VALUES (?, ?, ?, 1, 0, ?, ?)
                """,
                docId,
                sourceWarehouseId,
                destinationWarehouseId,
                Timestamp.from(now),
                Timestamp.from(now));
        UUID lineId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.transfer_document_lines (
                    id, document_id, material_reference_id, quantity, line_order)
                VALUES (?, ?, ?, 10, 1)
                """,
                lineId,
                docId,
                materialA);

        assertThrows(
                Exception.class,
                () ->
                        jdbc.update(
                                """
                                INSERT INTO warehouse.transfer_document_send_allocation (
                                    id, document_id, line_id, source_storage_cell_id,
                                    quantity, send_operation_id, created_at)
                                VALUES (?, ?, ?, ?, 0, NULL, ?)
                                """,
                                UUID.randomUUID(),
                                docId,
                                lineId,
                                cellA1,
                                Timestamp.from(now)));

        UUID otherDoc = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.transfer_document_payload (
                    document_id, source_warehouse_id, destination_warehouse_id,
                    payload_schema_version, payload_revision, created_at, updated_at)
                VALUES (?, ?, ?, 1, 0, ?, ?)
                """,
                otherDoc,
                sourceWarehouseId,
                destinationWarehouseId,
                Timestamp.from(now),
                Timestamp.from(now));
        UUID otherLine = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.transfer_document_lines (
                    id, document_id, material_reference_id, quantity, line_order)
                VALUES (?, ?, ?, 5, 1)
                """,
                otherLine,
                otherDoc,
                materialB);
        assertThrows(
                Exception.class,
                () ->
                        jdbc.update(
                                """
                                INSERT INTO warehouse.transfer_document_send_allocation (
                                    id, document_id, line_id, source_storage_cell_id,
                                    quantity, send_operation_id, created_at)
                                VALUES (?, ?, ?, ?, 5, NULL, ?)
                                """,
                                UUID.randomUUID(),
                                docId,
                                otherLine,
                                cellA1,
                                Timestamp.from(now)));

        jdbc.update("DELETE FROM warehouse.transfer_document_payload WHERE document_id = ?", docId);
        Integer remaining =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM warehouse.transfer_document_send_allocation
                         WHERE document_id = ?
                        """,
                        Integer.class,
                        docId);
        assertEquals(0, remaining);
    }

    @Test
    void happyPathMultiLineMultiCellSend() {
        seedAvailable(materialA, cellA1, "20");
        seedAvailable(materialA, cellA2, "10");
        seedAvailable(materialB, cellA3, "8");

        TransferDocumentView created = createTwoLineDocument();
        api.takeTransferTaskInWork(created.documentId());
        assertTrue(bundle.taskStates().findByDocumentId(created.documentId()).isPresent());

        long opsBefore = countOps();
        long movesBefore = countMoves();
        long destStockBefore = destinationStockRows();

        TransferDocumentSendResult sent =
                api.sendTransferDocument(
                        new SendTransferDocumentCommand(
                                created.documentId(),
                                documentVersion(created.documentId()),
                                created.payloadRevision(),
                                List.of(
                                        alloc(created, materialA, cellA1, "20"),
                                        alloc(created, materialA, cellA2, "10"),
                                        alloc(created, materialB, cellA3, "8"))));

        assertEquals(DocumentStatus.POSTED.name(), sent.documentStatus());
        assertEquals(3, sent.sendOperationIds().size());
        assertEquals(created.payloadRevision(), sent.payloadRevision());
        assertEquals(null, sent.continuationDocumentId());
        assertEquals(
                DocumentStatus.POSTED,
                bundle.documentEngine().findById(created.documentId()).orElseThrow().status());
        assertEquals(opsBefore + 3, countOps());
        assertTrue(countMoves() > movesBefore);
        assertEquals(destStockBefore, destinationStockRows());
        assertEquals(
                0,
                availableQty(materialA, cellA1).compareTo(BigDecimal.ZERO));
        assertEquals(
                0,
                availableQty(materialA, cellA2).compareTo(BigDecimal.ZERO));
        assertEquals(
                0,
                availableQty(materialB, cellA3).compareTo(BigDecimal.ZERO));
        assertEquals(0, inTransitQty(materialA, cellA1).compareTo(new BigDecimal("20")));
        assertEquals(0, inTransitQty(materialA, cellA2).compareTo(new BigDecimal("10")));
        assertEquals(0, inTransitQty(materialB, cellA3).compareTo(new BigDecimal("8")));

        assertEquals(
                3,
                jdbc.queryForObject(
                                """
                                SELECT COUNT(*) FROM warehouse.transfer_document_send_allocation
                                 WHERE document_id = ? AND send_operation_id IS NOT NULL
                                """,
                                Integer.class,
                                created.documentId())
                        .intValue());
        assertTrue(bundle.taskStates().findByDocumentId(created.documentId()).isEmpty());
        assertTrue(api.listMyWarehouseTasks(null).isEmpty());

        for (UUID opId : sent.sendOperationIds()) {
            TransferOperationContext ctx =
                    bundle.transferContexts()
                            .findByOperationId(WarehouseOperationId.of(opId))
                            .orElseThrow();
            assertEquals(destinationWarehouseId, ctx.destinationWarehouseId().value());
            assertFalse(ctx.hasDestinationStorageCell());
            assertFalse(ctx.isReceived());
            assertEquals(
                    WarehouseOperationStatus.COMPLETED,
                    bundle.operations().findById(WarehouseOperationId.of(opId)).orElseThrow().status());
            assertEquals(
                    WarehouseOperationType.TRANSFER_SEND,
                    bundle.operations().findById(WarehouseOperationId.of(opId)).orElseThrow().type());
        }
    }

    @Test
    void insufficientStockRollsBackEntireSend() {
        seedAvailable(materialA, cellA1, "20");
        seedAvailable(materialA, cellA2, "10");
        // M2 needs 8 but only 1 available
        seedAvailable(materialB, cellA3, "1");

        TransferDocumentView created = createTwoLineDocument();
        api.takeTransferTaskInWork(created.documentId());
        long opsBefore = countOps();
        long movesBefore = countMoves();
        long allocBefore = countAllocations();
        BigDecimal a1Before = availableQty(materialA, cellA1);

        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        api.sendTransferDocument(
                                new SendTransferDocumentCommand(
                                        created.documentId(),
                                        documentVersion(created.documentId()),
                                        created.payloadRevision(),
                                        List.of(
                                                alloc(created, materialA, cellA1, "20"),
                                                alloc(created, materialA, cellA2, "10"),
                                                alloc(created, materialB, cellA3, "8")))));

        assertEquals(
                DocumentStatus.DRAFT,
                bundle.documentEngine().findById(created.documentId()).orElseThrow().status());
        assertEquals(opsBefore, countOps());
        assertEquals(movesBefore, countMoves());
        assertEquals(allocBefore, countAllocations());
        assertEquals(0, a1Before.compareTo(availableQty(materialA, cellA1)));
        assertTrue(bundle.taskStates().findByDocumentId(created.documentId()).isPresent());
        assertFalse(api.listMyWarehouseTasks(null).isEmpty());
    }

    @Test
    void structuralValidationRejectsWithoutPhysicalFacts() {
        seedAvailable(materialA, cellA1, "30");
        seedAvailable(materialB, cellA3, "8");
        TransferDocumentView created = createTwoLineDocument();
        long opsBefore = countOps();

        UUID lineA = lineId(created, materialA);
        UUID lineB = lineId(created, materialB);

        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        api.sendTransferDocument(
                                new SendTransferDocumentCommand(
                                        created.documentId(),
                                        documentVersion(created.documentId()),
                                        created.payloadRevision(),
                                        List.of())));

        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        api.sendTransferDocument(
                                new SendTransferDocumentCommand(
                                        created.documentId(),
                                        documentVersion(created.documentId()),
                                        created.payloadRevision(),
                                        List.of(
                                                new TransferDocumentSourceAllocationInput(
                                                        lineA, cellA1, new BigDecimal("40")),
                                                new TransferDocumentSourceAllocationInput(
                                                        lineB, cellA3, new BigDecimal("8"))))));

        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        api.sendTransferDocument(
                                new SendTransferDocumentCommand(
                                        created.documentId(),
                                        documentVersion(created.documentId()),
                                        created.payloadRevision(),
                                        List.of(
                                                new TransferDocumentSourceAllocationInput(
                                                        lineA, cellA1, new BigDecimal("30")),
                                                new TransferDocumentSourceAllocationInput(
                                                        lineB, cellA3, new BigDecimal("8")),
                                                new TransferDocumentSourceAllocationInput(
                                                        UUID.randomUUID(),
                                                        cellA1,
                                                        BigDecimal.ONE)))));

        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        api.sendTransferDocument(
                                new SendTransferDocumentCommand(
                                        created.documentId(),
                                        documentVersion(created.documentId()),
                                        created.payloadRevision(),
                                        List.of(
                                                new TransferDocumentSourceAllocationInput(
                                                        lineA, cellA1, BigDecimal.ZERO),
                                                new TransferDocumentSourceAllocationInput(
                                                        lineB, cellA3, new BigDecimal("8"))))));

        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        api.sendTransferDocument(
                                new SendTransferDocumentCommand(
                                        created.documentId(),
                                        documentVersion(created.documentId()),
                                        created.payloadRevision(),
                                        List.of(
                                                new TransferDocumentSourceAllocationInput(
                                                        lineA, cellForeign, new BigDecimal("30")),
                                                new TransferDocumentSourceAllocationInput(
                                                        lineB, cellA3, new BigDecimal("8"))))));

        assertThrows(
                TransferDocumentOptimisticLockException.class,
                () ->
                        api.sendTransferDocument(
                                new SendTransferDocumentCommand(
                                        created.documentId(),
                                        documentVersion(created.documentId()),
                                        99L,
                                        List.of(
                                                new TransferDocumentSourceAllocationInput(
                                                        lineA, cellA1, new BigDecimal("30")),
                                                new TransferDocumentSourceAllocationInput(
                                                        lineB, cellA3, new BigDecimal("8"))))));

        assertThrows(
                IllegalStateException.class,
                () ->
                        api.sendTransferDocument(
                                new SendTransferDocumentCommand(
                                        created.documentId(),
                                        99L,
                                        created.payloadRevision(),
                                        List.of(
                                                new TransferDocumentSourceAllocationInput(
                                                        lineA, cellA1, new BigDecimal("30")),
                                                new TransferDocumentSourceAllocationInput(
                                                        lineB, cellA3, new BigDecimal("8"))))));

        session.set(sessionFor(userUnrelated));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.sendTransferDocument(
                                new SendTransferDocumentCommand(
                                        created.documentId(),
                                        documentVersion(created.documentId()),
                                        created.payloadRevision(),
                                        List.of(
                                                new TransferDocumentSourceAllocationInput(
                                                        lineA, cellA1, new BigDecimal("30")),
                                                new TransferDocumentSourceAllocationInput(
                                                        lineB, cellA3, new BigDecimal("8"))))));

        session.set(sessionFor(userSource));
        permissions.set(Set.of(WarehousePermissions.WAREHOUSE_VIEW));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.sendTransferDocument(
                                new SendTransferDocumentCommand(
                                        created.documentId(),
                                        documentVersion(created.documentId()),
                                        created.payloadRevision(),
                                        List.of(
                                                new TransferDocumentSourceAllocationInput(
                                                        lineA, cellA1, new BigDecimal("30")),
                                                new TransferDocumentSourceAllocationInput(
                                                        lineB, cellA3, new BigDecimal("8"))))));

        assertEquals(opsBefore, countOps());
        assertEquals(
                DocumentStatus.DRAFT,
                bundle.documentEngine().findById(created.documentId()).orElseThrow().status());
    }

    @Test
    void destinationResponsibilityNotRequiredForSender() {
        seedAvailable(materialA, cellA1, "30");
        seedAvailable(materialB, cellA3, "8");
        TransferDocumentView created = createTwoLineDocument();
        // userSource is only responsible for source, not destination
        TransferDocumentSendResult sent =
                api.sendTransferDocument(
                        new SendTransferDocumentCommand(
                                created.documentId(),
                                documentVersion(created.documentId()),
                                created.payloadRevision(),
                                List.of(
                                        alloc(created, materialA, cellA1, "30"),
                                        alloc(created, materialB, cellA3, "8"))));
        assertEquals(DocumentStatus.POSTED.name(), sent.documentStatus());
    }

    @Test
    void concurrentSendExactlyOnce() throws Exception {
        seedAvailable(materialA, cellA1, "30");
        seedAvailable(materialB, cellA3, "8");
        TransferDocumentView created = createTwoLineDocument();
        long version = documentVersion(created.documentId());
        SendTransferDocumentCommand command =
                new SendTransferDocumentCommand(
                        created.documentId(),
                        version,
                        created.payloadRevision(),
                        List.of(
                                alloc(created, materialA, cellA1, "30"),
                                alloc(created, materialB, cellA3, "8")));

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
                                        api.sendTransferDocument(command);
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
                DocumentStatus.POSTED,
                bundle.documentEngine().findById(created.documentId()).orElseThrow().status());
        assertEquals(
                2,
                jdbc.queryForObject(
                                """
                                SELECT COUNT(*) FROM warehouse.warehouse_operations
                                 WHERE operation_type = 'TRANSFER_SEND'
                                   AND status = 'COMPLETED'
                                """,
                                Integer.class)
                        .intValue());
    }

    @Test
    void directPostWithoutAllocationsRejected() {
        TransferDocumentView created = createTwoLineDocument();
        long opsBefore = countOps();
        assertThrows(
                InvalidWarehouseStateException.class,
                () -> bundle.documentEngine().postDocument(created.documentId()));
        assertEquals(
                DocumentStatus.DRAFT,
                bundle.documentEngine().findById(created.documentId()).orElseThrow().status());
        assertEquals(opsBefore, countOps());
    }

    @Test
    void postedDocumentRejectsUpdateAndDelete() {
        seedAvailable(materialA, cellA1, "30");
        seedAvailable(materialB, cellA3, "8");
        TransferDocumentView created = createTwoLineDocument();
        api.sendTransferDocument(
                new SendTransferDocumentCommand(
                        created.documentId(),
                        documentVersion(created.documentId()),
                        created.payloadRevision(),
                        List.of(
                                alloc(created, materialA, cellA1, "30"),
                                alloc(created, materialB, cellA3, "8"))));
        assertThrows(
                IllegalStateException.class,
                () ->
                        api.updateTransferDocument(
                                new com.tmp.warehouse.api.WarehouseApi.UpdateTransferDocumentCommand(
                                        created.documentId(),
                                        created.payloadRevision(),
                                        sourceWarehouseId,
                                        destinationWarehouseId,
                                        List.of())));
        assertThrows(
                IllegalStateException.class,
                () -> api.deleteTransferDocument(created.documentId()));
    }

    @Test
    void inboxDiscoversMoreThanTwoThousandDraftTasks() {
        Instant createdAt = Instant.parse("2026-09-08T10:00:00Z");
        int total = 2105;
        for (int i = 0; i < total; i++) {
            UUID documentId = UUID.randomUUID();
            jdbc.update(
                    """
                    INSERT INTO documents.documents (
                        id, document_type_id, document_number, title, status, version,
                        created_at, updated_at, posted_at, closed_at)
                    VALUES (?, 'warehouse.transfer', ?, 'Перемещение материалов', 'DRAFT', 0, ?, ?, NULL, NULL)
                    """,
                    documentId,
                    "WT-" + String.format("%05d", i),
                    Timestamp.from(createdAt),
                    Timestamp.from(createdAt));
            jdbc.update(
                    """
                    INSERT INTO warehouse.transfer_document_payload (
                        document_id, source_warehouse_id, destination_warehouse_id,
                        payload_schema_version, payload_revision, created_at, updated_at)
                    VALUES (?, ?, ?, 1, 0, ?, ?)
                    """,
                    documentId,
                    sourceWarehouseId,
                    destinationWarehouseId,
                    Timestamp.from(createdAt),
                    Timestamp.from(createdAt));
        }
        assertEquals(total, api.listMyWarehouseTasks(null).size());
    }

    private TransferDocumentView createTwoLineDocument() {
        return api.createTransferDocument(
                new CreateTransferDocumentCommand(
                        sourceWarehouseId,
                        destinationWarehouseId,
                        List.of(
                                new TransferDocumentLineInput(
                                        null, materialA, new BigDecimal("30"), 1),
                                new TransferDocumentLineInput(
                                        null, materialB, new BigDecimal("8"), 2))));
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

    private BigDecimal availableQty(UUID materialId, UUID cellId) {
        return stockQty(materialId, cellId, StockState.AVAILABLE);
    }

    private BigDecimal inTransitQty(UUID materialId, UUID cellId) {
        return stockQty(materialId, cellId, StockState.IN_TRANSIT);
    }

    private BigDecimal stockQty(UUID materialId, UUID cellId, StockState state) {
        BigDecimal qty =
                jdbc.query(
                        """
                        SELECT quantity FROM warehouse.stock_positions
                         WHERE material_reference_id = ?
                           AND storage_cell_id = ?
                           AND stock_state = ?
                        """,
                        rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO,
                        materialId,
                        cellId,
                        state.name());
        return qty == null ? BigDecimal.ZERO : qty;
    }

    private long countOps() {
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.warehouse_operations", Long.class)
                .longValue();
    }

    private long countMoves() {
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.warehouse_movements", Long.class)
                .longValue();
    }

    private long countAllocations() {
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.transfer_document_send_allocation",
                        Long.class)
                .longValue();
    }

    private long destinationStockRows() {
        return jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM warehouse.stock_positions
                         WHERE warehouse_id = ?
                        """,
                        Long.class,
                        destinationWarehouseId)
                .longValue();
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
}
