package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.tmp.warehouse.api.WarehouseApi.WarehouseMaterialStockDetailsView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseStockPage;
import com.tmp.warehouse.api.WarehouseApi.WarehouseStockSummaryView;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.testsupport.WarehouseIntegrationTestSupport;
import com.tmp.warehouse.testsupport.WarehouseJdbcTestSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
 * Stage 3.5.11: AVAILABLE stock summaries / cell breakdown, search, pagination, responsibility.
 */
@Testcontainers
class WarehouseStockSummaryIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-11T02:00:00Z"), ZoneOffset.UTC);
    private static final UUID USER = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    private WarehouseIntegrationTestSupport.ApiBundle bundle;

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
        jdbc.update("DELETE FROM warehouse.transfer_operation_context");
        jdbc.update("DELETE FROM warehouse.warehouse_movements");
        jdbc.update("DELETE FROM warehouse.warehouse_operations");
        jdbc.update("DELETE FROM warehouse.stock_positions");
        jdbc.update("DELETE FROM warehouse.warehouse_user_responsibility");
        jdbc.update("DELETE FROM warehouse.storage_cells");
        jdbc.update("DELETE FROM warehouse.warehouses");
        jdbc.update("DELETE FROM warehouse.material_references");

        bundle =
                WarehouseIntegrationTestSupport.createApiBundle(
                        dataSource, CLOCK, allowAllAuth(), fixedUser(USER), permitMineOnly());
    }

    @Test
    void aggregatesAvailableAcrossCellsExcludingOtherStates() {
        WarehouseId warehouseId = persistWarehouse("WH-A", "Aluminium");
        assign(warehouseId);
        StorageCellId cell1 = persistCell(warehouseId, "1-01");
        StorageCellId cell2 = persistCell(warehouseId, "1-04");
        StorageCellId cell3 = persistCell(warehouseId, "1-07");
        MaterialReference material =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc,
                        CLOCK,
                        MaterialReference.create("A100", "Профиль", "белый", "6м", "м."));

        seed(warehouseId, cell1, material, StockState.AVAILABLE, "12");
        seed(warehouseId, cell2, material, StockState.AVAILABLE, "28");
        seed(warehouseId, cell3, material, StockState.AVAILABLE, "8");
        seed(warehouseId, cell1, material, StockState.IN_TRANSIT, "5");
        seed(warehouseId, cell2, material, StockState.BLOCKED, "3");

        Map<String, Object> before = snapshotFacts();

        WarehouseStockPage page =
                bundle.api().listStockSummaries(warehouseId.value(), null, 0, 50);
        assertEquals(1, page.content().size());
        WarehouseStockSummaryView summary = page.content().get(0);
        assertEquals(material.id().value(), summary.materialReferenceId());
        assertEquals("A100", summary.article());
        assertEquals("Профиль", summary.name());
        assertEquals("белый", summary.color());
        assertEquals("6м", summary.size());
        assertEquals("м.", summary.unitOfMeasure());
        assertEquals(0, new BigDecimal("48").compareTo(summary.availableQuantity()));

        WarehouseMaterialStockDetailsView details =
                bundle.api().getStockCellBreakdown(warehouseId.value(), material.id().value());
        assertEquals(0, new BigDecimal("48").compareTo(details.totalAvailable()));
        assertEquals(3, details.cells().size());
        assertEquals("1-01", details.cells().get(0).storageCellCode());
        assertEquals(0, new BigDecimal("12").compareTo(details.cells().get(0).availableQuantity()));
        assertEquals("1-04", details.cells().get(1).storageCellCode());
        assertEquals(0, new BigDecimal("28").compareTo(details.cells().get(1).availableQuantity()));
        assertEquals("1-07", details.cells().get(2).storageCellCode());
        assertEquals(0, new BigDecimal("8").compareTo(details.cells().get(2).availableQuantity()));

        assertEquals(before, snapshotFacts());
    }

    @Test
    void separateMaterialsRemainSeparateAndOrderByArticle() {
        WarehouseId warehouseId = persistWarehouse("WH-B", "Main");
        assign(warehouseId);
        StorageCellId cell = persistCell(warehouseId, "C-1");
        MaterialReference b =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc,
                        CLOCK,
                        MaterialReference.create("B200", "Уголок", "", "", "шт."));
        MaterialReference a =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc,
                        CLOCK,
                        MaterialReference.create("A100", "Профиль", "", "", "м."));
        seed(warehouseId, cell, b, StockState.AVAILABLE, "17");
        seed(warehouseId, cell, a, StockState.AVAILABLE, "10");

        WarehouseStockPage page =
                bundle.api().listStockSummaries(warehouseId.value(), null, 0, 50);
        assertEquals(2, page.content().size());
        assertEquals("A100", page.content().get(0).article());
        assertEquals("B200", page.content().get(1).article());
    }

    @Test
    void searchIsCaseInsensitiveTrimmedByArticleAndName() {
        WarehouseId warehouseId = persistWarehouse("WH-S", "Search");
        assign(warehouseId);
        StorageCellId cell = persistCell(warehouseId, "S-1");
        MaterialReference profile =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc,
                        CLOCK,
                        MaterialReference.create("A100", "Профиль алюминиевый", "", "", "м."));
        MaterialReference angle =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc,
                        CLOCK,
                        MaterialReference.create("B200", "Уголок", "", "", "шт."));
        seed(warehouseId, cell, profile, StockState.AVAILABLE, "5");
        seed(warehouseId, cell, angle, StockState.AVAILABLE, "9");

        assertEquals(
                1,
                bundle.api()
                        .listStockSummaries(warehouseId.value(), "  a100  ", 0, 50)
                        .content()
                        .size());
        assertEquals(
                1,
                bundle.api()
                        .listStockSummaries(warehouseId.value(), "ПРОФИЛЬ", 0, 50)
                        .content()
                        .size());
        assertEquals(
                2,
                bundle.api()
                        .listStockSummaries(warehouseId.value(), "   ", 0, 50)
                        .content()
                        .size());
        assertTrue(
                bundle.api()
                        .listStockSummaries(warehouseId.value(), "неттакого", 0, 50)
                        .content()
                        .isEmpty());
    }

    @Test
    void paginationIsStable() {
        WarehouseId warehouseId = persistWarehouse("WH-P", "Paged");
        assign(warehouseId);
        StorageCellId cell = persistCell(warehouseId, "P-1");
        for (int i = 1; i <= 3; i++) {
            MaterialReference material =
                    WarehouseJdbcTestSupport.persistMaterial(
                            jdbc,
                            CLOCK,
                            MaterialReference.create("M" + i, "Material " + i, "", "", "шт."));
            seed(warehouseId, cell, material, StockState.AVAILABLE, String.valueOf(i));
        }

        WarehouseStockPage first =
                bundle.api().listStockSummaries(warehouseId.value(), null, 0, 2);
        assertEquals(2, first.content().size());
        assertEquals(3, first.totalElements());
        assertEquals("M1", first.content().get(0).article());
        assertEquals("M2", first.content().get(1).article());

        WarehouseStockPage second =
                bundle.api().listStockSummaries(warehouseId.value(), null, 1, 2);
        assertEquals(1, second.content().size());
        assertEquals("M3", second.content().get(0).article());
    }

    @Test
    void foreignWarehouseIsBlocked() {
        WarehouseId mine = persistWarehouse("WH-MINE", "Mine");
        WarehouseId foreign = persistWarehouse("WH-FRN", "Foreign");
        assign(mine);
        StorageCellId cell = persistCell(foreign, "F-1");
        MaterialReference material =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc, CLOCK, MaterialReference.create("X1", "X", "", "", "шт."));
        seed(foreign, cell, material, StockState.AVAILABLE, "10");

        assertThrows(
                AccessDeniedException.class,
                () -> bundle.api().listStockSummaries(foreign.value(), null, 0, 50));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        bundle.api()
                                .getStockCellBreakdown(foreign.value(), material.id().value()));
    }

    @Test
    void emptyWarehouseReturnsEmptyPage() {
        WarehouseId warehouseId = persistWarehouse("WH-E", "Empty");
        assign(warehouseId);
        WarehouseStockPage page =
                bundle.api().listStockSummaries(warehouseId.value(), null, 0, 50);
        assertEquals(0, page.totalElements());
        assertTrue(page.content().isEmpty());
    }

    private void assign(WarehouseId warehouseId) {
        jdbc.update(
                """
                INSERT INTO warehouse.warehouse_user_responsibility
                    (warehouse_id, user_id, assigned_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                """,
                warehouseId.value(),
                USER);
    }

    private WarehouseId persistWarehouse(String code, String name) {
        WarehouseId id = WarehouseId.generate();
        bundle.catalog().save(Warehouse.create(id, code, name));
        return id;
    }

    private StorageCellId persistCell(WarehouseId warehouseId, String code) {
        StorageCellId id = StorageCellId.generate();
        bundle.catalog().save(StorageCell.create(id, warehouseId, code));
        return id;
    }

    private void seed(
            WarehouseId warehouseId,
            StorageCellId cellId,
            MaterialReference material,
            StockState state,
            String qty) {
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                warehouseId,
                                cellId,
                                material,
                                state,
                                StockQuantity.of(new BigDecimal(qty))));
    }

    private Map<String, Object> snapshotFacts() {
        return Map.of(
                "stock",
                jdbc.queryForList(
                        "SELECT id, quantity, stock_state FROM warehouse.stock_positions ORDER BY id"),
                "operations",
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.warehouse_operations", Long.class),
                "movements",
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.warehouse_movements", Long.class));
    }

    private WarehouseResponsibilityGuard permitMineOnly() {
        // Stock summaries use responsibility repository, not this guard; keep permissive.
        return WarehouseIntegrationTestSupport.permitAllResponsibility();
    }

    private static AuthenticationService fixedUser(UUID userId) {
        SessionSummary session =
                new SessionSummary(
                        SessionId.generate(),
                        UserId.of(userId),
                        Login.of("stock-user"),
                        Instant.parse("2026-09-11T02:00:00Z"));
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
            public void logout() {}

            @Override
            public Optional<SessionSummary> currentSession() {
                return Optional.of(session);
            }

            @Override
            public boolean isAuthenticated() {
                return true;
            }
        };
    }

    private static AuthorizationService allowAllAuth() {
        return new AuthorizationService() {
            @Override
            public boolean hasPermission(PermissionId permissionId) {
                return true;
            }

            @Override
            public void requirePermission(PermissionId permissionId) {}

            @Override
            public Set<PermissionId> effectivePermissions() {
                return Set.of();
            }
        };
    }
}
