package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentLineInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentView;
import com.tmp.warehouse.api.WarehouseApi.UpdateTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskKind;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskState;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskView;
import com.tmp.warehouse.application.document.WarehouseTransferDocumentProcessor;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.TransferTaskAssignment;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import com.tmp.warehouse.domain.repository.TransferTaskStateRepository;
import com.tmp.warehouse.domain.repository.WarehouseTransferDocumentRepository;
import com.tmp.warehouse.security.WarehousePermissions;
import com.tmp.warehouse.testsupport.WarehouseIntegrationTestSupport;
import com.tmp.warehouse.testsupport.WarehouseJdbcTestSupport;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
 * Stage 3.5.5: Warehouse operational inbox / TRANSFER_PREPARATION projection over DRAFT transfer
 * documents; informational take-in-work; multi-responsible visibility; source-change clear.
 */
@Testcontainers
class WarehouseOperationalInboxIntegrationTest {

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
    private UUID ivanov;
    private UUID petrov;
    private UUID destinationOnly;
    private UUID unrelated;
    private UUID warehouseA;
    private UUID warehouseB;
    private UUID warehouseC;
    private UUID materialId;

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

        ivanov = UUID.randomUUID();
        petrov = UUID.randomUUID();
        destinationOnly = UUID.randomUUID();
        unrelated = UUID.randomUUID();
        session.set(sessionFor(ivanov));
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

        warehouseA = api.createWarehouse(new CreateWarehouseCommand("A", "Warehouse A", true))
                .warehouseId();
        warehouseB = api.createWarehouse(new CreateWarehouseCommand("B", "Warehouse B", true))
                .warehouseId();
        warehouseC = api.createWarehouse(new CreateWarehouseCommand("C", "Warehouse C", true))
                .warehouseId();
        api.assignUserToWarehouse(warehouseA, ivanov);
        api.assignUserToWarehouse(warehouseA, petrov);
        api.assignUserToWarehouse(warehouseB, destinationOnly);
        api.assignUserToWarehouse(warehouseB, unrelated);
        api.assignUserToWarehouse(warehouseC, petrov);

        materialId =
                WarehouseJdbcTestSupport.persistMaterial(
                                jdbc, CLOCK, MaterialReference.legacyArticle("MAT-INBOX"))
                        .id()
                        .value();
    }

    @Test
    void multipleResponsibleUsersSeeSamePreparationTask() {
        TransferDocumentView doc = createDraft(warehouseA, warehouseB);
        List<WarehouseTaskView> ivanovTasks = api.listMyWarehouseTasks(null);
        assertEquals(1, ivanovTasks.size());
        assertEquals(doc.documentId(), ivanovTasks.get(0).documentId());
        assertEquals(WarehouseTaskKind.TRANSFER_PREPARATION, ivanovTasks.get(0).taskKind());
        assertEquals(WarehouseTaskState.NEW, ivanovTasks.get(0).taskState());
        assertNull(ivanovTasks.get(0).workingUserId());

        session.set(sessionFor(petrov));
        List<WarehouseTaskView> petrovTasks = api.listMyWarehouseTasks(null);
        assertEquals(1, petrovTasks.size());
        assertEquals(doc.documentId(), petrovTasks.get(0).documentId());
    }

    @Test
    void takeInWorkSetsInWorkWithoutStockMutation() {
        PhysicalSnapshot before = snapshotPhysical();
        TransferDocumentView doc = createDraft(warehouseA, warehouseB);

        WarehouseTaskView taken = api.takeTransferTaskInWork(doc.documentId());
        assertEquals(WarehouseTaskState.IN_WORK, taken.taskState());
        assertEquals(ivanov, taken.workingUserId());
        assertNotNull(taken.workingSince());

        List<WarehouseTaskView> listed = api.listMyWarehouseTasks(null);
        assertEquals(WarehouseTaskState.IN_WORK, listed.get(0).taskState());
        assertEquals(ivanov, listed.get(0).workingUserId());
        assertEquals(before, snapshotPhysical());
    }

    @Test
    void taskRemainsVisibleToOtherResponsibleAfterTakeInWork() {
        TransferDocumentView doc = createDraft(warehouseA, warehouseB);
        api.takeTransferTaskInWork(doc.documentId());

        session.set(sessionFor(petrov));
        List<WarehouseTaskView> petrovTasks = api.listMyWarehouseTasks(null);
        assertEquals(1, petrovTasks.size());
        assertEquals(WarehouseTaskState.IN_WORK, petrovTasks.get(0).taskState());
        assertEquals(ivanov, petrovTasks.get(0).workingUserId());
    }

    @Test
    void takeoverAllowedWithoutConflict() {
        TransferDocumentView doc = createDraft(warehouseA, warehouseB);
        api.takeTransferTaskInWork(doc.documentId());

        session.set(sessionFor(petrov));
        WarehouseTaskView taken = api.takeTransferTaskInWork(doc.documentId());
        assertEquals(petrov, taken.workingUserId());
        assertEquals(WarehouseTaskState.IN_WORK, taken.taskState());

        session.set(sessionFor(ivanov));
        List<WarehouseTaskView> stillVisible = api.listMyWarehouseTasks(null);
        assertEquals(1, stillVisible.size());
        assertEquals(petrov, stillVisible.get(0).workingUserId());
    }

    @Test
    void unrelatedResponsibleWarehouseDoesNotSeeOrTakeTask() {
        TransferDocumentView doc = createDraft(warehouseA, warehouseB);
        session.set(sessionFor(unrelated));
        assertTrue(api.listMyWarehouseTasks(null).isEmpty());
        assertThrows(
                AccessDeniedException.class, () -> api.listMyWarehouseTasks(warehouseA));
        assertThrows(
                AccessDeniedException.class, () -> api.takeTransferTaskInWork(doc.documentId()));
    }

    @Test
    void destinationOnlyUserDoesNotSeeDraftPreparationTask() {
        createDraft(warehouseA, warehouseB);
        session.set(sessionFor(destinationOnly));
        assertTrue(api.listMyWarehouseTasks(null).isEmpty());
    }

    @Test
    void rbacReadAndTake() {
        TransferDocumentView doc = createDraft(warehouseA, warehouseB);

        permissions.set(Set.of()); // no view
        assertThrows(AccessDeniedException.class, () -> api.listMyWarehouseTasks(null));

        permissions.set(Set.of(WarehousePermissions.WAREHOUSE_VIEW));
        assertEquals(1, api.listMyWarehouseTasks(null).size());

        permissions.set(Set.of(WarehousePermissions.WAREHOUSE_VIEW)); // no transfer
        assertThrows(
                AccessDeniedException.class, () -> api.takeTransferTaskInWork(doc.documentId()));

        permissions.set(Set.of(WarehousePermissions.WAREHOUSE_TRANSFER));
        // transfer yes but still need auth session responsibility — ivanov is responsible
        WarehouseTaskView taken = api.takeTransferTaskInWork(doc.documentId());
        assertEquals(ivanov, taken.workingUserId());

        // transfer yes, responsibility no
        session.set(sessionFor(destinationOnly));
        grantFullWarehousePermissions();
        assertThrows(
                AccessDeniedException.class, () -> api.takeTransferTaskInWork(doc.documentId()));
    }

    @Test
    void sourceChangeClearsWorkerAndMovesVisibility() {
        TransferDocumentView doc = createDraft(warehouseA, warehouseB);
        api.takeTransferTaskInWork(doc.documentId());
        assertEquals(WarehouseTaskState.IN_WORK, api.listMyWarehouseTasks(null).get(0).taskState());

        // Petrov is responsible for C; reassign source A→C
        session.set(sessionFor(petrov));
        grantFullWarehousePermissions();
        api.assignUserToWarehouse(warehouseC, petrov);
        TransferDocumentView updated =
                api.updateTransferDocument(
                        new UpdateTransferDocumentCommand(
                                doc.documentId(),
                                doc.payloadRevision(),
                                warehouseC,
                                warehouseB,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialId, BigDecimal.TEN, 1))));

        session.set(sessionFor(ivanov));
        assertTrue(api.listMyWarehouseTasks(null).isEmpty());

        session.set(sessionFor(petrov));
        List<WarehouseTaskView> forC = api.listMyWarehouseTasks(warehouseC);
        assertEquals(1, forC.size());
        assertEquals(updated.documentId(), forC.get(0).documentId());
        assertEquals(WarehouseTaskState.NEW, forC.get(0).taskState());
        assertNull(forC.get(0).workingUserId());
    }

    @Test
    void destinationChangeAndLineEditKeepWorker() {
        TransferDocumentView doc = createDraft(warehouseA, warehouseB);
        api.takeTransferTaskInWork(doc.documentId());

        TransferDocumentView destChanged =
                api.updateTransferDocument(
                        new UpdateTransferDocumentCommand(
                                doc.documentId(),
                                doc.payloadRevision(),
                                warehouseA,
                                warehouseC,
                                List.of(
                                        new TransferDocumentLineInput(
                                                null, materialId, BigDecimal.TEN, 1))));
        assertEquals(
                ivanov, api.listMyWarehouseTasks(null).get(0).workingUserId());

        api.updateTransferDocument(
                new UpdateTransferDocumentCommand(
                        destChanged.documentId(),
                        destChanged.payloadRevision(),
                        warehouseA,
                        warehouseC,
                        List.of(
                                new TransferDocumentLineInput(
                                        null, materialId, BigDecimal.valueOf(3), 1))));
        assertEquals(
                ivanov, api.listMyWarehouseTasks(null).get(0).workingUserId());
    }

    @Test
    void deleteDraftCascadesTaskAssignment() {
        TransferDocumentView doc = createDraft(warehouseA, warehouseB);
        api.takeTransferTaskInWork(doc.documentId());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.transfer_task_state WHERE document_id = ?",
                        Integer.class,
                        doc.documentId()));

        api.deleteTransferDocument(doc.documentId());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.transfer_task_state WHERE document_id = ?",
                        Integer.class,
                        doc.documentId()));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.transfer_document_payload WHERE document_id = ?",
                        Integer.class,
                        doc.documentId()));
        assertTrue(bundle.documentEngine().findById(doc.documentId()).isEmpty());
    }

    @Test
    void nonDraftTransferMetadataIsNotListedAsTask() {
        TransferDocumentView doc = createDraft(warehouseA, warehouseB);
        jdbc.update(
                "UPDATE documents.documents SET status = ? WHERE id = ?",
                DocumentStatus.POSTED.name(),
                doc.documentId());
        assertTrue(api.listMyWarehouseTasks(null).isEmpty());
    }

    @Test
    void deterministicOrderingNewThenInWorkOldestFirst() {
        TransferDocumentView oldNew = createDraft(warehouseA, warehouseB);
        TransferDocumentView newerNew = createDraft(warehouseA, warehouseB);
        TransferDocumentView oldInWork = createDraft(warehouseA, warehouseB);
        TransferDocumentView newerInWork = createDraft(warehouseA, warehouseB);

        setDocumentCreatedAt(oldNew.documentId(), Instant.parse("2026-09-01T10:00:00Z"));
        setDocumentCreatedAt(newerNew.documentId(), Instant.parse("2026-09-02T10:00:00Z"));
        setDocumentCreatedAt(oldInWork.documentId(), Instant.parse("2026-09-01T11:00:00Z"));
        setDocumentCreatedAt(newerInWork.documentId(), Instant.parse("2026-09-02T11:00:00Z"));

        api.takeTransferTaskInWork(oldInWork.documentId());
        api.takeTransferTaskInWork(newerInWork.documentId());

        List<UUID> first = api.listMyWarehouseTasks(null).stream()
                .map(WarehouseTaskView::documentId)
                .toList();
        List<UUID> second = api.listMyWarehouseTasks(null).stream()
                .map(WarehouseTaskView::documentId)
                .toList();
        assertEquals(first, second);
        assertEquals(
                List.of(
                        oldNew.documentId(),
                        newerNew.documentId(),
                        oldInWork.documentId(),
                        newerInWork.documentId()),
                first);
    }

    @Test
    void listAndTakeDoNotMutatePhysicalWarehouseFacts() {
        TransferDocumentView doc = createDraft(warehouseA, warehouseB);
        PhysicalSnapshot before = snapshotPhysical();
        api.listMyWarehouseTasks(null);
        api.takeTransferTaskInWork(doc.documentId());
        session.set(sessionFor(petrov));
        api.takeTransferTaskInWork(doc.documentId());
        assertEquals(before, snapshotPhysical());
        assertNotEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.transfer_task_state", Integer.class));
    }

    @Test
    void inboxAssemblyUsesBatchPayloadAndAssignmentReads() {
        CountingTransferDocumentRepository payloads =
                new CountingTransferDocumentRepository(bundle.transferDocuments());
        CountingTransferTaskStateRepository assignments =
                new CountingTransferTaskStateRepository(bundle.taskStates());
        WarehouseOperationalInboxService inbox =
                new WarehouseOperationalInboxService(
                        bundle.documentEngine(),
                        payloads,
                        assignments,
                        bundle.responsibilities(),
                        bundle.catalog(),
                        new DefaultWarehouseResponsibilityGuard(
                                authenticationFromSession(), bundle.responsibilities()),
                        authenticationFromSession(),
                        new org.springframework.transaction.support.TransactionTemplate(
                                new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                                        dataSource)),
                        CLOCK);

        createDraft(warehouseA, warehouseB);
        createDraft(warehouseA, warehouseB);
        createDraft(warehouseA, warehouseB);

        List<WarehouseTaskView> tasks = inbox.listMyWarehouseTasks(null);
        assertEquals(3, tasks.size());
        assertEquals(1, payloads.batchFindCalls.get());
        assertEquals(1, assignments.batchFindCalls.get());
        assertEquals(0, payloads.singleFindCalls.get());
        assertEquals(0, assignments.singleFindCalls.get());
    }

    @Test
    void optionalWarehouseFilterScopesInbox() {
        createDraft(warehouseA, warehouseB);
        session.set(sessionFor(petrov));
        createDraft(warehouseC, warehouseB);

        session.set(sessionFor(petrov));
        assertEquals(2, api.listMyWarehouseTasks(null).size());
        assertEquals(1, api.listMyWarehouseTasks(warehouseA).size());
        assertEquals(1, api.listMyWarehouseTasks(warehouseC).size());
        assertThrows(AccessDeniedException.class, () -> api.listMyWarehouseTasks(warehouseB));
    }

    @Test
    void zeroResponsibleWarehousesReturnsEmptyEvenWithViewPermission() {
        UUID stranger = UUID.randomUUID();
        session.set(sessionFor(stranger));
        permissions.set(Set.of(WarehousePermissions.WAREHOUSE_VIEW));
        createDraftAsIvanov();
        assertTrue(api.listMyWarehouseTasks(null).isEmpty());
    }

    private void createDraftAsIvanov() {
        SessionSummary previous = session.get();
        session.set(sessionFor(ivanov));
        grantFullWarehousePermissions();
        createDraft(warehouseA, warehouseB);
        session.set(previous);
    }

    private TransferDocumentView createDraft(UUID source, UUID destination) {
        return api.createTransferDocument(
                new CreateTransferDocumentCommand(
                        source,
                        destination,
                        List.of(
                                new TransferDocumentLineInput(
                                        null, materialId, BigDecimal.TEN, 1))));
    }

    private void setDocumentCreatedAt(UUID documentId, Instant createdAt) {
        jdbc.update(
                "UPDATE documents.documents SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt),
                documentId);
    }

    private PhysicalSnapshot snapshotPhysical() {
        return new PhysicalSnapshot(
                count("warehouse.stock_positions"),
                count("warehouse.warehouse_operations"),
                count("warehouse.warehouse_movements"),
                count("warehouse.transfer_operation_context"),
                count("warehouse.material_reservation_links"));
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
                Instant.parse("2026-09-08T14:00:00Z"));
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

    private record PhysicalSnapshot(
            int stockPositions,
            int operations,
            int movements,
            int transferContexts,
            int reservations) {}

    private static final class CountingTransferDocumentRepository
            implements WarehouseTransferDocumentRepository {
        private final WarehouseTransferDocumentRepository delegate;
        private final AtomicInteger batchFindCalls = new AtomicInteger();
        private final AtomicInteger singleFindCalls = new AtomicInteger();

        private CountingTransferDocumentRepository(WarehouseTransferDocumentRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public void insert(WarehouseTransferDocument document) {
            delegate.insert(document);
        }

        @Override
        public Optional<WarehouseTransferDocument> findByDocumentId(UUID documentId) {
            singleFindCalls.incrementAndGet();
            return delegate.findByDocumentId(documentId);
        }

        @Override
        public Map<UUID, WarehouseTransferDocument> findByDocumentIds(Collection<UUID> documentIds) {
            batchFindCalls.incrementAndGet();
            return delegate.findByDocumentIds(documentIds);
        }

        @Override
        public void update(WarehouseTransferDocument document, long expectedPayloadRevision) {
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

    private static final class CountingTransferTaskStateRepository
            implements TransferTaskStateRepository {
        private final TransferTaskStateRepository delegate;
        private final AtomicInteger batchFindCalls = new AtomicInteger();
        private final AtomicInteger singleFindCalls = new AtomicInteger();

        private CountingTransferTaskStateRepository(TransferTaskStateRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<TransferTaskAssignment> findByDocumentId(UUID documentId) {
            singleFindCalls.incrementAndGet();
            return delegate.findByDocumentId(documentId);
        }

        @Override
        public Map<UUID, TransferTaskAssignment> findByDocumentIds(Collection<UUID> documentIds) {
            batchFindCalls.incrementAndGet();
            return delegate.findByDocumentIds(documentIds);
        }

        @Override
        public TransferTaskAssignment takeInWork(
                UUID documentId, UUID workingUserId, Instant workingSince) {
            return delegate.takeInWork(documentId, workingUserId, workingSince);
        }

        @Override
        public void clear(UUID documentId) {
            delegate.clear(documentId);
        }
    }
}
