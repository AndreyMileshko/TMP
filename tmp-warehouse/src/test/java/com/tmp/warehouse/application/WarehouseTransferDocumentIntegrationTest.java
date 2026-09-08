package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.tmp.warehouse.api.WarehouseApi.CreateTransferDraftCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.ReceiptCommand;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentLineInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentView;
import com.tmp.warehouse.api.WarehouseApi.UpdateTransferDocumentCommand;
import com.tmp.warehouse.application.document.WarehouseTransferDocumentProcessor;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.TransferDocumentOptimisticLockException;
import com.tmp.warehouse.domain.WarehouseId;
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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Stage 3.5.2: Warehouse Transfer Document foundation — Document Engine + typed payload, auth,
 * concurrency, delete cleanup, POST refusal, stock safety, and one-line transfer compatibility.
 */
@Testcontainers
class WarehouseTransferDocumentIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC);

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
        jdbc.update("DELETE FROM warehouse.transfer_document_lines");
        jdbc.update("DELETE FROM warehouse.transfer_document_payload");
        jdbc.update("DELETE FROM documents.document_lifecycle_journal");
        jdbc.update("DELETE FROM documents.document_versions");
        jdbc.update("DELETE FROM documents.documents");
        // keep warehouse.transfer document type from V36 / registrar
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

        // Ensure processor/type registered for this fresh DB (V36 seeds type; registrar upserts).
        if (bundle.documentEngine().registeredTypes().stream()
                .noneMatch(t -> WarehouseTransferDocumentProcessor.DOCUMENT_TYPE_ID.equals(t.typeId()))) {
            bundle.documentEngine()
                    .registerProcessor(
                            new WarehouseTransferDocumentProcessor(bundle.transferDocuments()));
        }

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
    }

    @Test
    void documentTypeAndProcessorRegistered() {
        assertTrue(
                bundle.documentEngine().registeredTypes().stream()
                        .anyMatch(
                                t ->
                                        WarehouseTransferDocumentProcessor.DOCUMENT_TYPE_ID.equals(
                                                t.typeId())));
        Integer typeRows =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM documents.document_types WHERE id = ?",
                        Integer.class,
                        WarehouseTransferDocumentProcessor.DOCUMENT_TYPE_ID);
        assertEquals(1, typeRows);
    }

    @Test
    void createReadUpdateDeleteLifecycle() {
        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, BigDecimal.TEN, 1),
                                        new TransferDocumentLineInput(
                                                null, materialB, BigDecimal.ONE, 2))));
        assertEquals(DocumentStatus.DRAFT.name(), created.documentStatus());
        assertEquals(0L, created.payloadRevision());
        assertEquals(2, created.lines().size());
        assertEquals(1, created.lines().get(0).lineOrder());

        TransferDocumentView read = api.getTransferDocument(created.documentId());
        assertEquals(created.documentId(), read.documentId());
        assertEquals(created.documentNumber(), read.documentNumber());

        TransferDocumentView updated =
                api.updateTransferDocument(
                        new UpdateTransferDocumentCommand(
                                created.documentId(),
                                0L,
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, new BigDecimal("5.000000"), 1))));
        assertEquals(1L, updated.payloadRevision());
        assertEquals(1, updated.lines().size());

        api.deleteTransferDocument(created.documentId());
        assertTrue(bundle.documentEngine().findById(created.documentId()).isEmpty());
        assertFalse(bundle.transferDocuments().existsByDocumentId(created.documentId()));
        Integer lineCount =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.transfer_document_lines WHERE document_id = ?",
                        Integer.class,
                        created.documentId());
        assertEquals(0, lineCount);
    }

    @Test
    void createIsAtomicWhenPayloadInsertFails() {
        UUID missingMaterial = UUID.randomUUID();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        api.createTransferDocument(
                                new CreateTransferDocumentCommand(
                                        sourceWarehouseId,
                                        destinationWarehouseId,
                                        List.of(
                                                new TransferDocumentLineInput(
                                                        null,
                                                        missingMaterial,
                                                        BigDecimal.ONE,
                                                        1)))));
        Integer docs =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM documents.documents WHERE document_type_id = ?",
                        Integer.class,
                        WarehouseTransferDocumentProcessor.DOCUMENT_TYPE_ID);
        assertEquals(0, docs);
        Integer payloads =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.transfer_document_payload", Integer.class);
        assertEquals(0, payloads);
    }

    @Test
    void createRollsBackDocumentWhenPayloadInsertFailsAfterDocumentEngineCreate() {
        WarehouseTransferDocumentService failingService =
                new WarehouseTransferDocumentService(
                        bundle.documentEngine(),
                        new FailingInsertTransferDocumentRepository(bundle.transferDocuments()),
                        bundle.catalog(),
                        bundle.materials(),
                        new DefaultWarehouseResponsibilityGuard(
                                authenticationFromSession(),
                                new com.tmp.warehouse.persistence
                                        .JdbcWarehouseUserResponsibilityRepository(jdbc, CLOCK)),
                        new org.springframework.transaction.support.TransactionTemplate(
                                new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                                        dataSource)));

        assertThrows(
                RuntimeException.class,
                () ->
                        failingService.create(
                                new WarehouseTransferDocumentService.CreateCommand(
                                        sourceWarehouseId,
                                        destinationWarehouseId,
                                        List.of(
                                                new WarehouseTransferDocumentService.LineInput(
                                                        null, materialA, BigDecimal.ONE, 1)))));

        Integer docs =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM documents.documents WHERE document_type_id = ?",
                        Integer.class,
                        WarehouseTransferDocumentProcessor.DOCUMENT_TYPE_ID);
        assertEquals(0, docs);
        Integer payloads =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.transfer_document_payload", Integer.class);
        assertEquals(0, payloads);
    }

    @Test
    void documentTitleIsStableWhenDraftRouteChanges() {
        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId, destinationWarehouseId, List.of()));
        assertEquals("Перемещение материалов", created.title());
        assertFalse(created.title().contains("SRC"));
        assertFalse(created.title().contains("DST"));

        var other = api.createWarehouse(new CreateWarehouseCommand("ALT", "Alternate", true));
        api.assignUserToWarehouse(other.warehouseId(), userSource);
        TransferDocumentView updated =
                api.updateTransferDocument(
                        new UpdateTransferDocumentCommand(
                                created.documentId(),
                                0L,
                                other.warehouseId(),
                                destinationWarehouseId,
                                List.of()));
        assertEquals("Перемещение материалов", updated.title());
        assertEquals(other.warehouseId(), updated.sourceWarehouseId());
        assertEquals(
                "Перемещение материалов",
                bundle.documentEngine().findById(created.documentId()).orElseThrow().title());
    }

    @Test
    void staleUpdateRejectedAndWinnerPreserved() {
        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, BigDecimal.TEN, 1))));
        api.updateTransferDocument(
                new UpdateTransferDocumentCommand(
                        created.documentId(),
                        0L,
                        sourceWarehouseId,
                        destinationWarehouseId,
                        List.of(
                                new TransferDocumentLineInput(
                                        null, materialA, new BigDecimal("7"), 1))));
        assertThrows(
                TransferDocumentOptimisticLockException.class,
                () ->
                        api.updateTransferDocument(
                                new UpdateTransferDocumentCommand(
                                        created.documentId(),
                                        0L,
                                        sourceWarehouseId,
                                        destinationWarehouseId,
                                        List.of(
                                                new TransferDocumentLineInput(
                                                        null, materialB, BigDecimal.ONE, 1)))));
        TransferDocumentView current = api.getTransferDocument(created.documentId());
        assertEquals(1L, current.payloadRevision());
        assertEquals(0, current.lines().getFirst().quantity().compareTo(new BigDecimal("7")));
        assertEquals(materialA, current.lines().getFirst().materialReferenceId());
    }

    @Test
    void postFailsAtomicallyAndRemainsDraft() {
        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId, destinationWarehouseId, List.of()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> bundle.documentEngine().postDocument(created.documentId()));
        assertEquals(
                DocumentStatus.DRAFT,
                bundle.documentEngine().findById(created.documentId()).orElseThrow().status());
        assertEquals(
                0L,
                bundle.transferDocuments()
                        .findByDocumentId(created.documentId())
                        .orElseThrow()
                        .payloadRevision());
    }

    @Test
    void authorizationCreateUpdateDeleteAndRead() {
        // create without responsibility
        session.set(sessionFor(userUnrelated));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.createTransferDocument(
                                new CreateTransferDocumentCommand(
                                        sourceWarehouseId, destinationWarehouseId, List.of())));

        // create without RBAC
        session.set(sessionFor(userSource));
        permissions.set(Set.of(WarehousePermissions.WAREHOUSE_VIEW));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.createTransferDocument(
                                new CreateTransferDocumentCommand(
                                        sourceWarehouseId, destinationWarehouseId, List.of())));

        permissions.set(
                Set.of(
                        WarehousePermissions.WAREHOUSE_TRANSFER,
                        WarehousePermissions.WAREHOUSE_VIEW));
        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId, destinationWarehouseId, List.of()));

        // destination can read
        session.set(sessionFor(userDestination));
        TransferDocumentView destRead = api.getTransferDocument(created.documentId());
        assertEquals(created.documentId(), destRead.documentId());

        // unrelated cannot read
        session.set(sessionFor(userUnrelated));
        assertThrows(
                AccessDeniedException.class, () -> api.getTransferDocument(created.documentId()));

        // destination cannot update
        session.set(sessionFor(userDestination));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.updateTransferDocument(
                                new UpdateTransferDocumentCommand(
                                        created.documentId(),
                                        0L,
                                        sourceWarehouseId,
                                        destinationWarehouseId,
                                        List.of())));
    }

    @Test
    void noStockMutationOnDocumentLifecycle() {
        long opsBefore = count("warehouse.warehouse_operations");
        long movesBefore = count("warehouse.warehouse_movements");
        long stockBefore = count("warehouse.stock_positions");

        TransferDocumentView created =
                api.createTransferDocument(
                        new CreateTransferDocumentCommand(
                                sourceWarehouseId,
                                destinationWarehouseId,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialA, BigDecimal.TEN, 1))));
        api.updateTransferDocument(
                new UpdateTransferDocumentCommand(
                        created.documentId(),
                        0L,
                        sourceWarehouseId,
                        destinationWarehouseId,
                        List.of()));
        api.deleteTransferDocument(created.documentId());

        assertEquals(opsBefore, count("warehouse.warehouse_operations"));
        assertEquals(movesBefore, count("warehouse.warehouse_movements"));
        assertEquals(stockBefore, count("warehouse.stock_positions"));
    }

    @Test
    void oneLineTransferApiStillWorks() {
        StorageCellId sourceCell = StorageCellId.generate();
        StorageCellId destCell = StorageCellId.generate();
        bundle.catalog()
                .save(
                        com.tmp.warehouse.domain.StorageCell.create(
                                sourceCell, WarehouseId.of(sourceWarehouseId), "S-01"));
        bundle.catalog()
                .save(
                        com.tmp.warehouse.domain.StorageCell.create(
                                destCell, WarehouseId.of(destinationWarehouseId), "D-01"));
        permissions.set(
                Set.of(
                        WarehousePermissions.WAREHOUSE_TRANSFER,
                        WarehousePermissions.WAREHOUSE_VIEW,
                        WarehousePermissions.WAREHOUSE_RECEIPT));
        session.set(sessionFor(userSource));
        api.receive(
                new ReceiptCommand(
                        "MAT-A",
                        "MAT-A",
                        "",
                        "",
                        "",
                        new BigDecimal("20"),
                        sourceWarehouseId,
                        sourceCell.value()));
        var draft =
                api.createTransferDraft(
                        new CreateTransferDraftCommand(
                                materialA,
                                BigDecimal.TEN,
                                sourceWarehouseId,
                                sourceCell.value(),
                                destinationWarehouseId,
                                destCell.value()));
        assertEquals("DRAFT", draft.status());
        var sent = api.sendTransfer(draft.operationId());
        assertEquals("COMPLETED", sent.status());
        session.set(sessionFor(userDestination));
        var received = api.receiveTransfer(draft.operationId());
        assertEquals("COMPLETED", received.status());
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0L : value;
    }

    private SessionSummary sessionFor(UUID userId) {
        return new SessionSummary(
                SessionId.of(UUID.randomUUID()),
                UserId.of(userId),
                Login.of("u-" + userId.toString().substring(0, 8)),
                Instant.now(CLOCK));
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
                    throw new AccessDeniedException("missing " + permissionId.value());
                }
            }

            @Override
            public Set<PermissionId> effectivePermissions() {
                return permissions.get();
            }
        };
    }

    /**
     * Test-only decorator: fails the first {@link #insert} after Document Engine create so the
     * surrounding transaction must roll back.
     */
    private static final class FailingInsertTransferDocumentRepository
            implements com.tmp.warehouse.domain.repository.WarehouseTransferDocumentRepository {

        private final com.tmp.warehouse.domain.repository.WarehouseTransferDocumentRepository
                delegate;

        private FailingInsertTransferDocumentRepository(
                com.tmp.warehouse.domain.repository.WarehouseTransferDocumentRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public void insert(com.tmp.warehouse.domain.WarehouseTransferDocument document) {
            throw new RuntimeException("forced payload insert failure after document create");
        }

        @Override
        public java.util.Optional<com.tmp.warehouse.domain.WarehouseTransferDocument>
                findByDocumentId(UUID documentId) {
            return delegate.findByDocumentId(documentId);
        }

        @Override
        public void update(
                com.tmp.warehouse.domain.WarehouseTransferDocument document,
                long expectedPayloadRevision) {
            delegate.update(document, expectedPayloadRevision);
        }

        @Override
        public void deleteByDocumentId(UUID documentId) {
            delegate.deleteByDocumentId(documentId);
        }

        @Override
        public boolean existsByDocumentId(UUID documentId) {
            return delegate.existsByDocumentId(documentId);
        }
    }
}
