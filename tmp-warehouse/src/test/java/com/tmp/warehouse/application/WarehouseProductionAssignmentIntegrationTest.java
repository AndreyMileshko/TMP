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
import com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.UpdateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import com.tmp.warehouse.api.WarehouseReferenceQueryApi;
import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.security.WarehousePermissions;
import com.tmp.warehouse.testsupport.WarehouseIntegrationTestSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

/** Stage 3.5.15 — Warehouse-managed production destination assignment. */
@Testcontainers
class WarehouseProductionAssignmentIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-14T10:00:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    private final AtomicReference<SessionSummary> session = new AtomicReference<>();
    private WarehouseApi api;
    private WarehouseReferenceQueryApi referenceApi;

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
        jdbc.update("DELETE FROM warehouse.stock_positions");
        jdbc.update("DELETE FROM warehouse.storage_cells");
        jdbc.update("DELETE FROM warehouse.warehouses");
        UUID userId = UUID.randomUUID();
        session.set(
                new SessionSummary(
                        SessionId.generate(),
                        UserId.of(userId),
                        Login.of("admin"),
                        Instant.parse("2026-09-14T10:00:00Z")));
        WarehouseIntegrationTestSupport.ApiBundle bundle =
                WarehouseIntegrationTestSupport.createApiBundle(
                        dataSource,
                        CLOCK,
                        fixedAuth(
                                Set.of(
                                        WarehousePermissions.WAREHOUSE_STRUCTURE_VIEW,
                                        WarehousePermissions.WAREHOUSE_STRUCTURE_CREATE,
                                        WarehousePermissions.WAREHOUSE_STRUCTURE_UPDATE)),
                        authentication(),
                        WarehouseIntegrationTestSupport.permitAllResponsibility());
        api = bundle.api();
        referenceApi =
                new DefaultWarehouseReferenceQueryApi(bundle.catalog(), bundle.materials());
    }

    private AuthenticationService authentication() {
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

    @Test
    void noProductionWarehouseReturnsEmpty() {
        api.createWarehouse(new CreateWarehouseCommand("A", "Alpha", true));
        assertTrue(api.findProductionWarehouse().isEmpty());
        assertTrue(referenceApi.findProductionWarehouse().isEmpty());
    }

    @Test
    void setActiveWarehouseMarksProduction() {
        WarehouseView a = api.createWarehouse(new CreateWarehouseCommand("A", "Alpha", true));
        WarehouseView marked = api.setProductionWarehouse(a.warehouseId());
        assertTrue(marked.productionWarehouse());
        assertEquals(a.warehouseId(), api.findProductionWarehouse().orElseThrow().warehouseId());
        assertEquals(
                a.warehouseId(),
                referenceApi.findProductionWarehouse().orElseThrow().warehouseId());
    }

    @Test
    void setBAfterASwitchesAssignmentAtomically() {
        WarehouseView a = api.createWarehouse(new CreateWarehouseCommand("A", "Alpha", true));
        WarehouseView b = api.createWarehouse(new CreateWarehouseCommand("B", "Beta", true));
        api.setProductionWarehouse(a.warehouseId());
        api.setProductionWarehouse(b.warehouseId());

        assertEquals(b.warehouseId(), api.findProductionWarehouse().orElseThrow().warehouseId());
        long productionCount =
                api.listWarehouses().stream().filter(WarehouseView::productionWarehouse).count();
        assertEquals(1, productionCount);
        assertFalse(
                api.listWarehouses().stream()
                        .filter(w -> w.warehouseId().equals(a.warehouseId()))
                        .findFirst()
                        .orElseThrow()
                        .productionWarehouse());
    }

    @Test
    void clearRemovesProductionAssignment() {
        WarehouseView a = api.createWarehouse(new CreateWarehouseCommand("A", "Alpha", true));
        api.setProductionWarehouse(a.warehouseId());
        api.clearProductionWarehouse(a.warehouseId());
        assertTrue(api.findProductionWarehouse().isEmpty());
    }

    @Test
    void inactiveWarehouseRejected() {
        WarehouseView inactive =
                api.createWarehouse(new CreateWarehouseCommand("INACT", "Inactive", false));
        InvalidWarehouseStateException ex =
                assertThrows(
                        InvalidWarehouseStateException.class,
                        () -> api.setProductionWarehouse(inactive.warehouseId()));
        assertTrue(ex.getMessage().contains("активн"));
    }

    @Test
    void deactivateCurrentProductionWarehouseRejected() {
        WarehouseView a = api.createWarehouse(new CreateWarehouseCommand("A", "Alpha", true));
        api.setProductionWarehouse(a.warehouseId());
        InvalidWarehouseStateException ex =
                assertThrows(
                        InvalidWarehouseStateException.class,
                        () ->
                                api.updateWarehouse(
                                        new UpdateWarehouseCommand(
                                                a.warehouseId(), "A", "Alpha", false)));
        assertTrue(ex.getMessage().contains("Нельзя деактивировать склад производства"));
    }

    @Test
    void renameRetainsProductionAssignment() {
        WarehouseView a = api.createWarehouse(new CreateWarehouseCommand("A", "Alpha", true));
        api.setProductionWarehouse(a.warehouseId());
        WarehouseView renamed =
                api.updateWarehouse(
                        new UpdateWarehouseCommand(a.warehouseId(), "A2", "Alpha Two", true));
        assertTrue(renamed.productionWarehouse());
        assertEquals("A2", renamed.code());
        assertEquals(a.warehouseId(), api.findProductionWarehouse().orElseThrow().warehouseId());
    }

    @Test
    void warehouseCodeIsIrrelevantForAssignment() {
        WarehouseView second =
                api.createWarehouse(new CreateWarehouseCommand("SECOND", "Второй склад", true));
        WarehouseView main =
                api.createWarehouse(new CreateWarehouseCommand("MAIN", "Основной склад", true));
        api.setProductionWarehouse(main.warehouseId());
        assertEquals(main.warehouseId(), api.findProductionWarehouse().orElseThrow().warehouseId());
        assertFalse(
                api.listWarehouses().stream()
                        .filter(w -> w.warehouseId().equals(second.warehouseId()))
                        .findFirst()
                        .orElseThrow()
                        .productionWarehouse());
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
