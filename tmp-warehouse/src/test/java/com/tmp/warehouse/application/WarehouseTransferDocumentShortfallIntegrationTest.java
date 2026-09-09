package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentEngineStatus;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.DocumentQuery;
import com.tmp.document.api.DocumentStatus;
import com.tmp.document.api.DocumentTypeDescriptor;
import com.tmp.document.api.UpdateDocumentCommand;
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
import com.tmp.warehouse.api.WarehouseApi.UpdateTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskState;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskView;
import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.TransferContinuationReason;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Stage 3.5.7: shortfall / automatic continuation — supply analysis, payload shrink, lineage,
 * rollback, concurrency, inbox projection, V40 schema.
 */
@Testcontainers
class WarehouseTransferDocumentShortfallIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-08T16:00:00Z"), ZoneOffset.UTC);

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
    private UUID sourceWarehouseId;
    private UUID destinationWarehouseId;
    private UUID materialA;
    private UUID materialB;
    private UUID materialC;
    private UUID cellA1;
    private UUID cellA2;
    private UUID cellA3;

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

        cellA1 = api.createStorageCell(new CreateStorageCellCommand(sourceWarehouseId, "A1", true))
                .storageCellId();
        cellA2 = api.createStorageCell(new CreateStorageCellCommand(sourceWarehouseId, "A2", true))
                .storageCellId();
        cellA3 = api.createStorageCell(new CreateStorageCellCommand(sourceWarehouseId, "A3", true))
                .storageCellId();
    }

    @Test
    void v40ContinuationLineageSchemaAndConstraints() {
        Integer cols =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                         WHERE table_schema = 'warehouse'
                           AND table_name = 'transfer_document_payload'
                           AND column_name IN ('continuation_of_document_id', 'continuation_reason')
                        """,
                        Integer.class);
        assertEquals(2, cols);

        UUID parent = UUID.randomUUID();
        Instant now = CLOCK.instant();
        jdbc.update(
                """
                INSERT INTO warehouse.transfer_document_payload (
                    document_id, source_warehouse_id, destination_warehouse_id,
                    payload_schema_version, payload_revision, created_at, updated_at)
                VALUES (?, ?, ?, 1, 0, ?, ?)
                """,
                parent,
                sourceWarehouseId,
                destinationWarehouseId,
                Timestamp.from(now),
                Timestamp.from(now));

        assertNull(
                jdbc.queryForObject(
                        """
                        SELECT continuation_of_document_id FROM warehouse.transfer_document_payload
                         WHERE document_id = ?
                        """,
                        UUID.class,
                        parent));

        UUID child = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.transfer_document_payload (
                    document_id, source_warehouse_id, destination_warehouse_id,
                    payload_schema_version, payload_revision,
                    continuation_of_document_id, continuation_reason,
                    created_at, updated_at)
                VALUES (?, ?, ?, 1, 0, ?, 'SHORTFALL', ?, ?)
                """,
                child,
                sourceWarehouseId,
                destinationWarehouseId,
                parent,
                Timestamp.from(now),
                Timestamp.from(now));

        assertThrows(
                Exception.class,
                () ->
                        jdbc.update(
                                """
                                INSERT INTO warehouse.transfer_document_payload (
                                    document_id, source_warehouse_id, destination_warehouse_id,
                                    payload_schema_version, payload_revision,
                                    continuation_of_document_id, continuation_reason,
                                    created_at, updated_at)
                                VALUES (?, ?, ?, 1, 0, ?, NULL, ?, ?)
                                """,
                                UUID.randomUUID(),
                                sourceWarehouseId,
                                destinationWarehouseId,
                                parent,
                                Timestamp.from(now),
                                Timestamp.from(now)));

        assertThrows(
                Exception.class,
                () ->
                        jdbc.update(
                                """
                                INSERT INTO warehouse.transfer_document_payload (
                                    document_id, source_warehouse_id, destination_warehouse_id,
                                    payload_schema_version, payload_revision,
                                    continuation_of_document_id, continuation_reason,
                                    created_at, updated_at)
                                VALUES (?, ?, ?, 1, 0, ?, 'SHORTFALL', ?, ?)
                                """,
                                UUID.randomUUID(),
                                sourceWarehouseId,
                                destinationWarehouseId,
                                UUID.randomUUID(),
                                Timestamp.from(now),
                                Timestamp.from(now)));

        assertThrows(
                Exception.class,
                () ->
                        jdbc.update(
                                "DELETE FROM warehouse.transfer_document_payload WHERE document_id = ?",
                                parent));
    }

    @Test
    void simpleShortfallCreatesContinuationAndMutatesOnlySentQuantity() {
        seedAvailable(materialA, cellA1, "200");
        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, new BigDecimal("100"), 1))));
        api.takeTransferTaskInWork(created.documentId());
        long revisionBefore = created.payloadRevision();
        long opsBefore = countOps();

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
        assertEquals(revisionBefore + 1, sent.payloadRevision());
        assertEquals(1, sent.sendOperationIds().size());
        assertEquals(opsBefore + 1, countOps());
        assertEquals(0, availableQty(materialA, cellA1).compareTo(new BigDecimal("102")));
        assertEquals(0, inTransitQty(materialA, cellA1).compareTo(new BigDecimal("98")));

        TransferDocumentView posted = api.getTransferDocument(created.documentId());
        assertEquals(DocumentStatus.POSTED.name(), posted.documentStatus());
        assertEquals(1, posted.lines().size());
        assertEquals(0, posted.lines().get(0).quantity().compareTo(new BigDecimal("98")));
        assertEquals(lineId(created, materialA), posted.lines().get(0).lineId());
        assertNull(posted.continuationOfDocumentId());

        TransferDocumentView continuation =
                api.getTransferDocument(sent.continuationDocumentId());
        assertEquals(DocumentStatus.DRAFT.name(), continuation.documentStatus());
        assertEquals(sourceWarehouseId, continuation.sourceWarehouseId());
        assertEquals(destinationWarehouseId, continuation.destinationWarehouseId());
        assertEquals(created.documentId(), continuation.continuationOfDocumentId());
        assertEquals(TransferContinuationReason.SHORTFALL.name(), continuation.continuationReason());
        assertEquals(1, continuation.lines().size());
        assertEquals(0, continuation.lines().get(0).quantity().compareTo(new BigDecimal("2")));
        assertNotEquals(lineId(created, materialA), continuation.lines().get(0).lineId());

        assertEquals(
                0,
                jdbc.queryForObject(
                                """
                                SELECT COUNT(*) FROM warehouse.transfer_document_send_allocation
                                 WHERE document_id = ?
                                """,
                                Integer.class,
                                continuation.documentId())
                        .intValue());
        assertTrue(bundle.taskStates().findByDocumentId(created.documentId()).isEmpty());

        List<WarehouseTaskView> tasks = api.listMyWarehouseTasks(null);
        assertEquals(1, tasks.size());
        assertEquals(continuation.documentId(), tasks.get(0).documentId());
        assertEquals(WarehouseTaskState.NEW, tasks.get(0).taskState());
        assertNull(tasks.get(0).workingUserId());
        assertEquals(created.documentId(), tasks.get(0).continuationOfDocumentId());
        assertEquals("SHORTFALL", tasks.get(0).continuationReason());
    }

    @Test
    void multiLineShortfallRemovesZeroLinesAndKeepsRelativeOrder() {
        seedAvailable(materialA, cellA1, "200");
        seedAvailable(materialB, cellA2, "50");
        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, new BigDecimal("100"), 1),
                                        new TransferDocumentLineInput(
                                                null, materialB, new BigDecimal("10"), 2),
                                        new TransferDocumentLineInput(
                                                null, materialC, new BigDecimal("5"), 3))));

        TransferDocumentSendResult sent =
                api.sendTransferDocument(
                        new SendTransferDocumentCommand(
                                created.documentId(),
                                documentVersion(created.documentId()),
                                created.payloadRevision(),
                                List.of(
                                        alloc(created, materialA, cellA1, "98"),
                                        alloc(created, materialB, cellA2, "10"))));

        TransferDocumentView posted = api.getTransferDocument(created.documentId());
        assertEquals(2, posted.lines().size());
        assertEquals(materialA, posted.lines().get(0).materialReferenceId());
        assertEquals(1, posted.lines().get(0).lineOrder());
        assertEquals(0, posted.lines().get(0).quantity().compareTo(new BigDecimal("98")));
        assertEquals(materialB, posted.lines().get(1).materialReferenceId());
        assertEquals(2, posted.lines().get(1).lineOrder());

        TransferDocumentView continuation =
                api.getTransferDocument(sent.continuationDocumentId());
        assertEquals(2, continuation.lines().size());
        assertEquals(materialA, continuation.lines().get(0).materialReferenceId());
        assertEquals(1, continuation.lines().get(0).lineOrder());
        assertEquals(0, continuation.lines().get(0).quantity().compareTo(new BigDecimal("2")));
        assertEquals(materialC, continuation.lines().get(1).materialReferenceId());
        assertEquals(3, continuation.lines().get(1).lineOrder());
        assertEquals(0, continuation.lines().get(1).quantity().compareTo(new BigDecimal("5")));
        assertEquals(2, sent.sendOperationIds().size());
        assertEquals(0, inTransitQty(materialA, cellA1).compareTo(new BigDecimal("98")));
        assertEquals(0, inTransitQty(materialB, cellA2).compareTo(new BigDecimal("10")));
        assertEquals(0, inTransitQty(materialC, cellA3).compareTo(BigDecimal.ZERO));
    }

    @Test
    void multiCellPartialLineShortfall() {
        seedAvailable(materialA, cellA1, "50");
        seedAvailable(materialA, cellA2, "30");
        seedAvailable(materialA, cellA3, "40");
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
                                        alloc(created, materialA, cellA1, "50"),
                                        alloc(created, materialA, cellA2, "30"),
                                        alloc(created, materialA, cellA3, "18"))));

        assertEquals(3, sent.sendOperationIds().size());
        assertEquals(
                0,
                api.getTransferDocument(created.documentId())
                        .lines()
                        .get(0)
                        .quantity()
                        .compareTo(new BigDecimal("98")));
        assertEquals(
                0,
                api.getTransferDocument(sent.continuationDocumentId())
                        .lines()
                        .get(0)
                        .quantity()
                        .compareTo(new BigDecimal("2")));
        assertEquals(0, inTransitQty(materialA, cellA1).compareTo(new BigDecimal("50")));
        assertEquals(0, inTransitQty(materialA, cellA2).compareTo(new BigDecimal("30")));
        assertEquals(0, inTransitQty(materialA, cellA3).compareTo(new BigDecimal("18")));
    }

    @Test
    void fullSendDoesNotCreateContinuationOrBumpRevision() {
        seedAvailable(materialA, cellA1, "100");
        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, new BigDecimal("100"), 1))));
        long revision = created.payloadRevision();
        TransferDocumentSendResult sent =
                api.sendTransferDocument(
                        new SendTransferDocumentCommand(
                                created.documentId(),
                                documentVersion(created.documentId()),
                                revision,
                                List.of(alloc(created, materialA, cellA1, "100"))));
        assertNull(sent.continuationDocumentId());
        assertEquals(revision, sent.payloadRevision());
        assertEquals(0, inTransitQty(materialA, cellA1).compareTo(new BigDecimal("100")));
        assertTrue(api.listMyWarehouseTasks(null).isEmpty());
    }

    @Test
    void overAllocationAndAllZeroRejectedWithoutSideEffects() {
        seedAvailable(materialA, cellA1, "200");
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
        long opsBefore = countOps();
        long revision = created.payloadRevision();

        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        api.sendTransferDocument(
                                new SendTransferDocumentCommand(
                                        created.documentId(),
                                        documentVersion(created.documentId()),
                                        revision,
                                        List.of(alloc(created, materialA, cellA1, "101")))));
        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        api.sendTransferDocument(
                                new SendTransferDocumentCommand(
                                        created.documentId(),
                                        documentVersion(created.documentId()),
                                        revision,
                                        List.of())));

        TransferDocumentView still = api.getTransferDocument(created.documentId());
        assertEquals(DocumentStatus.DRAFT.name(), still.documentStatus());
        assertEquals(revision, still.payloadRevision());
        assertEquals(2, still.lines().size());
        assertEquals(0, still.lines().get(0).quantity().compareTo(new BigDecimal("100")));
        assertEquals(opsBefore, countOps());
        assertEquals(0, countAllocations());
        assertEquals(1, api.listMyWarehouseTasks(null).size());
    }

    @Test
    void physicalFailureAfterShortfallPreparationRollsBackEverything() {
        seedAvailable(materialA, cellA1, "15");
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
                                                null, materialB, new BigDecimal("10"), 2))));
        api.takeTransferTaskInWork(created.documentId());
        long revision = created.payloadRevision();
        long docsBefore = countTransferPayloads();
        long opsBefore = countOps();
        long movesBefore = countMoves();
        long contextsBefore = countContexts();
        BigDecimal aBefore = availableQty(materialA, cellA1);

        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        api.sendTransferDocument(
                                new SendTransferDocumentCommand(
                                        created.documentId(),
                                        documentVersion(created.documentId()),
                                        revision,
                                        List.of(
                                                alloc(created, materialA, cellA1, "20"),
                                                alloc(created, materialB, cellA2, "10")))));

        TransferDocumentView still = api.getTransferDocument(created.documentId());
        assertEquals(DocumentStatus.DRAFT.name(), still.documentStatus());
        assertEquals(revision, still.payloadRevision());
        assertEquals(2, still.lines().size());
        assertEquals(0, still.lines().get(0).quantity().compareTo(new BigDecimal("30")));
        assertEquals(0, still.lines().get(1).quantity().compareTo(new BigDecimal("10")));
        assertEquals(docsBefore, countTransferPayloads());
        assertEquals(opsBefore, countOps());
        assertEquals(movesBefore, countMoves());
        assertEquals(contextsBefore, countContexts());
        assertEquals(0, countAllocations());
        assertEquals(0, aBefore.compareTo(availableQty(materialA, cellA1)));
        assertTrue(bundle.taskStates().findByDocumentId(created.documentId()).isPresent());
        assertEquals(userSource, api.listMyWarehouseTasks(null).get(0).workingUserId());
    }

    @Test
    void continuationCreateFailureRollsBackOriginalPayloadShrink() {
        seedAvailable(materialA, cellA1, "200");
        FailingSecondCreateDocumentEngine failingEngine =
                new FailingSecondCreateDocumentEngine(bundle.documentEngine());
        TransactionTemplate tx =
                new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        WarehouseTransferDocumentService transferDocuments =
                new WarehouseTransferDocumentService(
                        failingEngine,
                        bundle.transferDocuments(),
                        bundle.taskStates(),
                        bundle.catalog(),
                        bundle.materials(),
                        new DefaultWarehouseResponsibilityGuard(
                                authenticationFromSession(),
                                new com.tmp.warehouse.persistence
                                        .JdbcWarehouseUserResponsibilityRepository(jdbc, CLOCK)),
                        tx);
        WarehouseTransferSendService sendService =
                new WarehouseTransferSendService(
                        failingEngine,
                        bundle.transferDocuments(),
                        transferDocuments,
                        bundle.sendAllocations(),
                        bundle.taskStates(),
                        bundle.catalog(),
                        new DefaultWarehouseResponsibilityGuard(
                                authenticationFromSession(),
                                new com.tmp.warehouse.persistence
                                        .JdbcWarehouseUserResponsibilityRepository(jdbc, CLOCK)),
                        tx,
                        CLOCK);

        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, new BigDecimal("100"), 1))));
        long revision = created.payloadRevision();
        long docsBefore = countTransferPayloads();
        long opsBefore = countOps();

        assertThrows(
                IllegalStateException.class,
                () ->
                        sendService.send(
                                new WarehouseTransferSendService.SendCommand(
                                        created.documentId(),
                                        documentVersion(created.documentId()),
                                        revision,
                                        List.of(
                                                new WarehouseTransferSendService
                                                        .SourceAllocationInput(
                                                        lineId(created, materialA),
                                                        cellA1,
                                                        new BigDecimal("98"))))));

        TransferDocumentView still = api.getTransferDocument(created.documentId());
        assertEquals(DocumentStatus.DRAFT.name(), still.documentStatus());
        assertEquals(revision, still.payloadRevision());
        assertEquals(0, still.lines().get(0).quantity().compareTo(new BigDecimal("100")));
        assertEquals(docsBefore, countTransferPayloads());
        assertEquals(0, countAllocations());
        assertEquals(opsBefore, countOps());
    }

    @Test
    void concurrentShortfallSendExactlyOnce() throws Exception {
        seedAvailable(materialA, cellA1, "200");
        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, new BigDecimal("100"), 1))));
        long version = documentVersion(created.documentId());
        long sendOpsBefore = countTransferSendOps();
        SendTransferDocumentCommand command =
                new SendTransferDocumentCommand(
                        created.documentId(),
                        version,
                        created.payloadRevision(),
                        List.of(alloc(created, materialA, cellA1, "98")));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        AtomicReference<UUID> continuation = new AtomicReference<>();
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
                                                    WarehousePermissions.WAREHOUSE_VIEW,
                                                    WarehousePermissions.WAREHOUSE_RECEIPT));
                                    ready.countDown();
                                    start.await(30, TimeUnit.SECONDS);
                                    try {
                                        TransferDocumentSendResult result =
                                                api.sendTransferDocument(command);
                                        successes.incrementAndGet();
                                        continuation.set(result.continuationDocumentId());
                                    } catch (RuntimeException ex) {
                                        failures.incrementAndGet();
                                    }
                                    return null;
                                }));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, successes.get());
        assertEquals(1, failures.get());
        assertNotNull(continuation.get());
        assertEquals(1, countPostedTransferDocuments());
        assertEquals(2, countTransferPayloads());
        assertEquals(sendOpsBefore + 1, countTransferSendOps());
    }

    @Test
    void continuationOfContinuationUsesImmediateParent() {
        seedAvailable(materialA, cellA1, "200");
        TransferDocumentView original =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, new BigDecimal("100"), 1))));
        TransferDocumentSendResult first =
                api.sendTransferDocument(
                        new SendTransferDocumentCommand(
                                original.documentId(),
                                documentVersion(original.documentId()),
                                original.payloadRevision(),
                                List.of(alloc(original, materialA, cellA1, "98"))));
        TransferDocumentView cont1 = api.getTransferDocument(first.continuationDocumentId());
        TransferDocumentSendResult second =
                api.sendTransferDocument(
                        new SendTransferDocumentCommand(
                                cont1.documentId(),
                                documentVersion(cont1.documentId()),
                                cont1.payloadRevision(),
                                List.of(alloc(cont1, materialA, cellA1, "1"))));
        TransferDocumentView cont2 = api.getTransferDocument(second.continuationDocumentId());
        assertEquals(original.documentId(), cont1.continuationOfDocumentId());
        assertEquals(cont1.documentId(), cont2.continuationOfDocumentId());
        assertEquals(0, cont2.lines().get(0).quantity().compareTo(new BigDecimal("1")));
    }

    @Test
    void ordinaryDraftUpdatePreservesContinuationLineage() {
        seedAvailable(materialA, cellA1, "200");
        TransferDocumentView original =
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
                                original.documentId(),
                                documentVersion(original.documentId()),
                                original.payloadRevision(),
                                List.of(alloc(original, materialA, cellA1, "98"))));
        TransferDocumentView continuation =
                api.getTransferDocument(sent.continuationDocumentId());
        UUID continuationLineId = continuation.lines().get(0).lineId();

        TransferDocumentView updated =
                api.updateTransferDocument(
                        new UpdateTransferDocumentCommand(
                                continuation.documentId(),
                                continuation.payloadRevision(),
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                continuationLineId,
                                                materialA,
                                                new BigDecimal("2"),
                                                1))));
        assertEquals(original.documentId(), updated.continuationOfDocumentId());
        assertEquals("SHORTFALL", updated.continuationReason());
        assertEquals(
                original.documentId(),
                bundle.transferDocuments()
                        .findByDocumentId(continuation.documentId())
                        .orElseThrow()
                        .continuationOfDocumentId()
                        .orElseThrow());
    }

    @Test
    void ordinaryCreateHasNullLineage() {
        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId, destinationWarehouseId, List.of()));
        assertNull(created.continuationOfDocumentId());
        assertNull(created.continuationReason());
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

    private long countTransferSendOps() {
        return jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM warehouse.warehouse_operations
                         WHERE operation_type = 'TRANSFER_SEND'
                        """,
                        Long.class)
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

    private long countContexts() {
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.transfer_operation_context", Long.class)
                .longValue();
    }

    private long countTransferPayloads() {
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.transfer_document_payload", Long.class)
                .longValue();
    }

    private long countPostedTransferDocuments() {
        return jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM documents.documents
                         WHERE document_type_id = 'warehouse.transfer' AND status = 'POSTED'
                        """,
                        Long.class)
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
                    throw new com.tmp.security.api.AccessDeniedException("missing " + permissionId);
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

    /** Fails on {@code createDocument} (continuation create after original already exists). */
    private static final class FailingSecondCreateDocumentEngine implements DocumentEngine {
        private final DocumentEngine delegate;

        private FailingSecondCreateDocumentEngine(DocumentEngine delegate) {
            this.delegate = delegate;
        }

        @Override
        public DocumentProcessorRegistration registerProcessor(DocumentProcessor processor) {
            return delegate.registerProcessor(processor);
        }

        @Override
        public DocumentMetadata createDocument(CreateDocumentCommand command) {
            throw new IllegalStateException("forced continuation create failure");
        }

        @Override
        public DocumentMetadata updateDocument(UpdateDocumentCommand command) {
            return delegate.updateDocument(command);
        }

        @Override
        public DocumentMetadata postDocument(UUID documentId) {
            return delegate.postDocument(documentId);
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
}
