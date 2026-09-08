package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.AvailabilityStatus;
import com.tmp.warehouse.api.WarehouseApi.CreateStorageCellCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateTransferDraftCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand;
import com.tmp.warehouse.api.WarehouseApi.OperationKind;
import com.tmp.warehouse.api.WarehouseApi.ReceiptCommand;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.persistence.JdbcWarehouseUserResponsibilityRepository;
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
 * Stage 3.5.1: User↔Warehouse responsibility relation, guards, transfer scope, and Production
 * availability compatibility.
 */
@Testcontainers
class WarehouseResponsibilityIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-08T10:00:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    private final AtomicReference<SessionSummary> session = new AtomicReference<>();
    private WarehouseIntegrationTestSupport.ApiBundle bundle;
    private DefaultWarehouseApi api;
    private UUID userA;
    private UUID userB;

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
        jdbc.update("DELETE FROM warehouse.warehouse_user_responsibility");
        jdbc.update("DELETE FROM warehouse.transfer_operation_context");
        jdbc.update("DELETE FROM warehouse.material_reservation_links");
        jdbc.update("DELETE FROM warehouse.warehouse_movements");
        jdbc.update("DELETE FROM warehouse.warehouse_operations");
        jdbc.update("DELETE FROM warehouse.stock_positions");
        jdbc.update("DELETE FROM warehouse.storage_cells");
        jdbc.update("DELETE FROM warehouse.warehouses");
        jdbc.update("DELETE FROM warehouse.material_references");

        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        session.set(null);
        bundle = createBundle(authorizationAllowAll(), realGuard());
        api = bundle.api();
    }

    @Test
    void listMyWarehousesReturnsOnlyAssignedActiveWarehouses() {
        session.set(sessionFor(userA));
        WarehouseView w1 = api.createWarehouse(new CreateWarehouseCommand("W1", "One", true));
        WarehouseView w2 = api.createWarehouse(new CreateWarehouseCommand("W2", "Two", true));
        WarehouseView inactive =
                api.createWarehouse(new CreateWarehouseCommand("W3", "Inactive", false));
        api.assignUserToWarehouse(w1.warehouseId(), userA);
        api.assignUserToWarehouse(w2.warehouseId(), userA);
        api.assignUserToWarehouse(inactive.warehouseId(), userA);

        List<UUID> mine =
                api.listMyWarehouses().stream().map(WarehouseView::warehouseId).toList();
        assertEquals(List.of(w1.warehouseId(), w2.warehouseId()), mine);
        assertFalse(mine.contains(inactive.warehouseId()));
    }

    @Test
    void warehouseCanHaveMultipleResponsibleUsers() {
        session.set(sessionFor(userA));
        WarehouseView w2 = api.createWarehouse(new CreateWarehouseCommand("W2", "Two", true));
        api.assignUserToWarehouse(w2.warehouseId(), userA);
        api.assignUserToWarehouse(w2.warehouseId(), userB);
        assertEquals(
                List.of(userA, userB).stream().sorted().toList(),
                api.listResponsibleUserIds(w2.warehouseId()).stream().sorted().toList());
    }

    @Test
    void assignIsIdempotentAndRemoveMissingIsSafe() {
        session.set(sessionFor(userA));
        WarehouseView w = api.createWarehouse(new CreateWarehouseCommand("WA", "A", true));
        api.assignUserToWarehouse(w.warehouseId(), userA);
        api.assignUserToWarehouse(w.warehouseId(), userA);
        assertEquals(1, api.listResponsibleUserIds(w.warehouseId()).size());
        api.removeUserFromWarehouse(w.warehouseId(), userA);
        assertTrue(api.listResponsibleUserIds(w.warehouseId()).isEmpty());
        api.removeUserFromWarehouse(w.warehouseId(), userA);
    }

    @Test
    void effectiveAuthorizationRequiresPermissionAndResponsibility() {
        WarehouseView wh = createWarehouseWithCell("AUTH");
        UUID cellId = firstCell(wh.warehouseId());
        MaterialReference material = WarehouseJdbcTestSupport.persistLegacyArticle(jdbc, CLOCK, "ALU");

        session.set(sessionFor(userA));
        api.assignUserToWarehouse(wh.warehouseId(), userA);

        DefaultWarehouseApi both =
                createBundle(
                                fixedAuth(Set.of(WarehousePermissions.WAREHOUSE_RECEIPT)),
                                realGuard())
                        .api();
        both.receive(
                new ReceiptCommand(
                        "ALU",
                        "ALU",
                        "",
                        "",
                        "",
                        BigDecimal.ONE,
                        wh.warehouseId(),
                        cellId));

        DefaultWarehouseApi permissionOnly =
                createBundle(
                                fixedAuth(Set.of(WarehousePermissions.WAREHOUSE_RECEIPT)),
                                realGuard())
                        .api();
        session.set(sessionFor(userB));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        permissionOnly.receive(
                                new ReceiptCommand(
                                        "ALU",
                                        "ALU",
                                        "",
                                        "",
                                        "",
                                        BigDecimal.ONE,
                                        wh.warehouseId(),
                                        cellId)));

        session.set(sessionFor(userA));
        DefaultWarehouseApi responsibilityOnly =
                createBundle(fixedAuth(Set.of()), realGuard()).api();
        assertThrows(
                AccessDeniedException.class,
                () ->
                        responsibilityOnly.receive(
                                new ReceiptCommand(
                                        "ALU",
                                        "ALU",
                                        "",
                                        "",
                                        "",
                                        BigDecimal.ONE,
                                        wh.warehouseId(),
                                        cellId)));

        DefaultWarehouseApi neither = createBundle(fixedAuth(Set.of()), realGuard()).api();
        session.set(sessionFor(userB));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        neither.receive(
                                new ReceiptCommand(
                                        "ALU",
                                        "ALU",
                                        "",
                                        "",
                                        "",
                                        BigDecimal.ONE,
                                        wh.warehouseId(),
                                        cellId)));
    }

    @Test
    void adminRoleWithoutResponsibilityDoesNotBypass() {
        WarehouseView wh = createWarehouseWithCell("ADMIN");
        UUID cellId = firstCell(wh.warehouseId());
        session.set(sessionFor(userA));
        DefaultWarehouseApi privileged =
                createBundle(
                                fixedAuth(
                                        Set.of(
                                                WarehousePermissions.WAREHOUSE_RECEIPT,
                                                WarehousePermissions.WAREHOUSE_STRUCTURE_UPDATE,
                                                WarehousePermissions.WAREHOUSE_STRUCTURE_CREATE,
                                                WarehousePermissions.WAREHOUSE_VIEW)),
                                realGuard())
                        .api();
        assertThrows(
                AccessDeniedException.class,
                () ->
                        privileged.receive(
                                new ReceiptCommand(
                                        "X",
                                        "X",
                                        "",
                                        "",
                                        "",
                                        BigDecimal.ONE,
                                        wh.warehouseId(),
                                        cellId)));
    }

    @Test
    void transferSendRequiresSourceResponsibilityOnly() {
        WarehouseView source = createWarehouseWithCell("SRC");
        WarehouseView dest = createWarehouseWithCell("DST");
        UUID sourceCell = firstCell(source.warehouseId());
        UUID destCell = firstCell(dest.warehouseId());
        MaterialReference material = WarehouseJdbcTestSupport.persistLegacyArticle(jdbc, CLOCK, "TR");

        session.set(sessionFor(userA));
        api.assignUserToWarehouse(source.warehouseId(), userA);
        seedAvailable(material, source.warehouseId(), sourceCell, "10");

        DefaultWarehouseApi sender =
                createBundle(
                                fixedAuth(Set.of(WarehousePermissions.WAREHOUSE_TRANSFER)),
                                realGuard())
                        .api();
        var draft =
                sender.createTransferDraft(
                        new CreateTransferDraftCommand(
                                material.id().value(),
                                new BigDecimal("2"),
                                source.warehouseId(),
                                sourceCell,
                                dest.warehouseId(),
                                destCell));
        sender.sendTransfer(draft.operationId());

        session.set(sessionFor(userB));
        api.assignUserToWarehouse(dest.warehouseId(), userB);
        DefaultWarehouseApi receiver =
                createBundle(
                                fixedAuth(Set.of(WarehousePermissions.WAREHOUSE_TRANSFER)),
                                realGuard())
                        .api();
        receiver.receiveTransfer(draft.operationId());

        session.set(sessionFor(userB));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        receiver.createTransferDraft(
                                new CreateTransferDraftCommand(
                                        material.id().value(),
                                        new BigDecimal("1"),
                                        source.warehouseId(),
                                        sourceCell,
                                        dest.warehouseId(),
                                        destCell)));

        session.set(sessionFor(userA));
        UUID otherSend =
                sender.createTransferDraft(
                                new CreateTransferDraftCommand(
                                        material.id().value(),
                                        new BigDecimal("1"),
                                        source.warehouseId(),
                                        sourceCell,
                                        dest.warehouseId(),
                                        destCell))
                        .operationId();
        sender.sendTransfer(otherSend);
        assertThrows(AccessDeniedException.class, () -> sender.receiveTransfer(otherSend));
    }

    @Test
    void executeWarehouseOperationCannotBypassResponsibility() {
        WarehouseView wh = createWarehouseWithCell("GEN");
        UUID cellId = firstCell(wh.warehouseId());
        session.set(sessionFor(userA));
        DefaultWarehouseApi gated =
                createBundle(
                                fixedAuth(
                                        Set.of(
                                                WarehousePermissions.WAREHOUSE_RECEIPT,
                                                WarehousePermissions.WAREHOUSE_MOVE,
                                                WarehousePermissions.WAREHOUSE_ADJUSTMENT)),
                                realGuard())
                        .api();
        assertThrows(
                AccessDeniedException.class,
                () ->
                        gated.executeWarehouseOperation(
                                ExecuteOperationCommand.receipt(
                                        "A",
                                        "A",
                                        "",
                                        "",
                                        "шт.",
                                        BigDecimal.ONE,
                                        wh.warehouseId(),
                                        cellId)));
        api.assignUserToWarehouse(wh.warehouseId(), userA);
        gated.executeWarehouseOperation(
                ExecuteOperationCommand.receipt(
                        "A",
                        "A",
                        "",
                        "",
                        "шт.",
                        BigDecimal.ONE,
                        wh.warehouseId(),
                        cellId));
    }

    @Test
    void checkAvailabilityDoesNotDependOnResponsibility() {
        WarehouseView wh = createWarehouseWithCell("AV");
        UUID cellId = firstCell(wh.warehouseId());
        MaterialReference material = WarehouseJdbcTestSupport.persistLegacyArticle(jdbc, CLOCK, "AVM");
        seedAvailable(material, wh.warehouseId(), cellId, "5");
        session.set(null);
        DefaultWarehouseApi viewer =
                createBundle(fixedAuth(Set.of(WarehousePermissions.WAREHOUSE_VIEW)), realGuard())
                        .api();
        var result =
                viewer.checkAvailability(material.id().value(), new BigDecimal("1"));
        assertEquals(AvailabilityStatus.AVAILABLE, result.status());
        assertEquals(0, new BigDecimal("5").compareTo(result.availableQuantity()));
    }

    @Test
    void structuralCreateWarehouseDoesNotRequireResponsibility() {
        session.set(sessionFor(userA));
        DefaultWarehouseApi admin =
                createBundle(
                                fixedAuth(Set.of(WarehousePermissions.WAREHOUSE_STRUCTURE_CREATE)),
                                realGuard())
                        .api();
        WarehouseView created =
                admin.createWarehouse(new CreateWarehouseCommand("NEW", "New", true));
        assertTrue(created.active());
    }

    @Test
    void v35MigrationCreatesTableUniqueAndBackfillPreservesStock() {
        Integer applied =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '35' AND success = TRUE
                        """,
                        Integer.class);
        assertEquals(1, applied);

        UUID warehouseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.warehouses
                    (id, code, name, active, version, created_at, updated_at)
                VALUES (?, 'BF-W', 'Backfill', TRUE, 0, NOW(), NOW())
                """,
                warehouseId);
        Integer stockBefore =
                jdbc.queryForObject("SELECT COUNT(*) FROM warehouse.stock_positions", Integer.class);

        jdbc.update(
                """
                INSERT INTO security.users
                    (id, login, display_name, password_hash, status, version, created_at, updated_at)
                VALUES (?, ?, 'BF', 'x', 'ACTIVE', 0, NOW(), NOW())
                """,
                userId,
                "bf-user-" + userId.toString().substring(0, 8));
        jdbc.update(
                """
                INSERT INTO security.permission_definitions
                    (permission_id, display_name, description, active, registered_at, version,
                     owner_capability_id)
                VALUES ('warehouse.receipt.create', 'Receipt', '', TRUE, NOW(), 0, 'warehouse')
                ON CONFLICT (permission_id) DO NOTHING
                """);
        UUID roleId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO security.roles (id, name, description, version, created_at, updated_at)
                VALUES (?, ?, '', 0, NOW(), NOW())
                """,
                roleId,
                "bf-role-" + roleId.toString().substring(0, 8));
        jdbc.update(
                """
                INSERT INTO security.role_permissions (role_id, permission_id, granted_at)
                VALUES (?, 'warehouse.receipt.create', NOW())
                ON CONFLICT DO NOTHING
                """,
                roleId);
        jdbc.update(
                """
                INSERT INTO security.user_roles (user_id, role_id, assigned_at)
                VALUES (?, ?, NOW())
                ON CONFLICT DO NOTHING
                """,
                userId,
                roleId);

        jdbc.update(
                """
                INSERT INTO warehouse.warehouse_user_responsibility (warehouse_id, user_id, assigned_at)
                SELECT w.id, u.id, CURRENT_TIMESTAMP
                FROM warehouse.warehouses w
                CROSS JOIN security.users u
                WHERE u.status = 'ACTIVE'
                  AND u.id = ?
                  AND w.id = ?
                  AND EXISTS (
                        SELECT 1
                        FROM security.permission_definitions pd
                        WHERE pd.permission_id LIKE 'warehouse.%'
                          AND pd.active = TRUE
                          AND NOT EXISTS (
                                SELECT 1 FROM security.user_permission_overrides o
                                WHERE o.user_id = u.id AND o.permission_id = pd.permission_id
                                  AND o.decision = 'REVOKE')
                          AND (
                                EXISTS (
                                    SELECT 1 FROM security.user_permission_overrides o
                                    WHERE o.user_id = u.id AND o.permission_id = pd.permission_id
                                      AND o.decision = 'GRANT')
                                OR EXISTS (
                                    SELECT 1
                                    FROM security.user_roles ur
                                    JOIN security.role_permissions rp ON rp.role_id = ur.role_id
                                    WHERE ur.user_id = u.id AND rp.permission_id = pd.permission_id)
                          )
                  )
                ON CONFLICT (warehouse_id, user_id) DO NOTHING
                """,
                userId,
                warehouseId);

        Integer rows =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM warehouse.warehouse_user_responsibility
                        WHERE warehouse_id = ? AND user_id = ?
                        """,
                        Integer.class,
                        warehouseId,
                        userId);
        assertEquals(1, rows);

        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                """
                                INSERT INTO warehouse.warehouse_user_responsibility
                                    (warehouse_id, user_id, assigned_at)
                                VALUES (?, ?, NOW())
                                """,
                                warehouseId,
                                userId));

        Integer stockAfter =
                jdbc.queryForObject("SELECT COUNT(*) FROM warehouse.stock_positions", Integer.class);
        assertEquals(stockBefore, stockAfter);
    }

    private WarehouseView createWarehouseWithCell(String code) {
        session.set(sessionFor(userA));
        DefaultWarehouseApi structure =
                createBundle(
                                fixedAuth(
                                        Set.of(
                                                WarehousePermissions.WAREHOUSE_STRUCTURE_CREATE,
                                                WarehousePermissions.STORAGE_CELL_CREATE,
                                                WarehousePermissions.WAREHOUSE_STRUCTURE_UPDATE,
                                                WarehousePermissions.WAREHOUSE_STRUCTURE_VIEW)),
                                WarehouseResponsibilityGuard.permitAll())
                        .api();
        WarehouseView wh =
                structure.createWarehouse(new CreateWarehouseCommand(code, code, true));
        structure.createStorageCell(new CreateStorageCellCommand(wh.warehouseId(), "C1", true));
        return wh;
    }

    private UUID firstCell(UUID warehouseId) {
        return bundle.catalog().findStorageCellsByWarehouse(WarehouseId.of(warehouseId)).stream()
                .findFirst()
                .orElseThrow()
                .id()
                .value();
    }

    private void seedAvailable(
            MaterialReference material, UUID warehouseId, UUID cellId, String qty) {
        jdbc.update(
                """
                INSERT INTO warehouse.stock_positions (
                    id, warehouse_id, storage_cell_id, material_reference_id, quantity,
                    stock_state, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'AVAILABLE', 0, NOW(), NOW())
                """,
                UUID.randomUUID(),
                warehouseId,
                cellId,
                material.id().value(),
                new BigDecimal(qty));
    }

    private WarehouseResponsibilityGuard realGuard() {
        return new DefaultWarehouseResponsibilityGuard(
                authentication(),
                new JdbcWarehouseUserResponsibilityRepository(jdbc, CLOCK));
    }

    private AuthenticationService authentication() {
        return new AuthenticationService() {
            @Override
            public SessionSummary login(Login login, char[] password) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SessionSummary completePasswordSetup(
                    Login login,
                    String activationCode,
                    char[] newPassword,
                    char[] confirmPassword) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void logout() {
                session.set(null);
            }

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

    private WarehouseIntegrationTestSupport.ApiBundle createBundle(
            AuthorizationService authorization, WarehouseResponsibilityGuard guard) {
        var base = WarehouseIntegrationTestSupport.createApiBundle(dataSource, CLOCK);
        DefaultWarehouseApi wired =
                new DefaultWarehouseApi(
                        authorization,
                        authentication(),
                        guard,
                        base.responsibilities(),
                        base.catalog(),
                        base.stockPositions(),
                        base.materials(),
                        new FixedMaterialReferenceDisplayPort(),
                        new WarehouseReservationLinkService(
                                new com.tmp.warehouse.persistence
                                        .JdbcMaterialReservationLinkRepository(jdbc),
                                CLOCK),
                        new WarehouseReceiptService(
                                engineFrom(base), base.stockPositions(), base.materials()),
                        new WarehouseMoveService(engineFrom(base)),
                        new WarehouseTransferService(
                                engineFrom(base),
                                base.operations(),
                                base.transferContexts(),
                                new org.springframework.transaction.support.TransactionTemplate(
                                        new org.springframework.jdbc.datasource
                                                .DataSourceTransactionManager(dataSource))),
                        new WarehouseConsumptionService(engineFrom(base), base.stockPositions()),
                        new WarehouseAdjustmentService(engineFrom(base), base.stockPositions()),
                        base.operations(),
                        base.transferContexts());
        return new WarehouseIntegrationTestSupport.ApiBundle(
                wired,
                base.jdbc(),
                base.operations(),
                base.transferContexts(),
                base.materials(),
                base.stockPositions(),
                base.catalog(),
                base.responsibilities());
    }

    private WarehouseOperationEngine engineFrom(WarehouseIntegrationTestSupport.ApiBundle base) {
        var movements = new com.tmp.warehouse.persistence.JdbcWarehouseMovementRepository(jdbc);
        return new WarehouseOperationEngine(
                base.operations(),
                base.stockPositions(),
                movements,
                new org.springframework.transaction.support.TransactionTemplate(
                        new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                                dataSource)),
                CLOCK);
    }

    private static SessionSummary sessionFor(UUID userId) {
        return new SessionSummary(
                SessionId.generate(),
                UserId.of(userId),
                Login.of("u-" + userId.toString().substring(0, 8)),
                Instant.parse("2026-09-08T10:00:00Z"));
    }

    private static AuthorizationService authorizationAllowAll() {
        return fixedAuth(
                Set.of(
                        WarehousePermissions.WAREHOUSE_VIEW,
                        WarehousePermissions.WAREHOUSE_RECEIPT,
                        WarehousePermissions.WAREHOUSE_MOVE,
                        WarehousePermissions.WAREHOUSE_TRANSFER,
                        WarehousePermissions.WAREHOUSE_CONSUMPTION,
                        WarehousePermissions.WAREHOUSE_ADJUSTMENT,
                        WarehousePermissions.WAREHOUSE_INVENTORY,
                        WarehousePermissions.WAREHOUSE_STRUCTURE_VIEW,
                        WarehousePermissions.WAREHOUSE_STRUCTURE_CREATE,
                        WarehousePermissions.WAREHOUSE_STRUCTURE_UPDATE,
                        WarehousePermissions.STORAGE_CELL_CREATE));
    }

    private static AuthorizationService fixedAuth(Set<PermissionId> granted) {
        Set<PermissionId> allowed = Set.copyOf(granted);
        return new AuthorizationService() {
            @Override
            public boolean hasPermission(PermissionId permissionId) {
                return allowed.contains(permissionId);
            }

            @Override
            public void requirePermission(PermissionId permissionId) {
                if (!hasPermission(permissionId)) {
                    throw new AccessDeniedException(
                            "Access denied for permission: " + permissionId.value());
                }
            }

            @Override
            public Set<PermissionId> effectivePermissions() {
                return allowed;
            }
        };
    }
}
