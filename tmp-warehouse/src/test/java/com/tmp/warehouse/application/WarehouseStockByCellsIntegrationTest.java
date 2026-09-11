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
import com.tmp.warehouse.api.WarehouseApi.WarehouseStockCellFilterOptionView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseStockCellLineView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseStockCellPage;
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
 * Stage 3.5.15 corrective: cell-centric AVAILABLE stock lines, cell filter, search, pagination.
 */
@Testcontainers
class WarehouseStockByCellsIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-11T08:00:00Z"), ZoneOffset.UTC);
    private static final UUID USER = UUID.fromString("33333333-3333-4333-8333-333333333333");

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
                        dataSource,
                        CLOCK,
                        allowAllAuth(),
                        fixedUser(USER),
                        WarehouseIntegrationTestSupport.permitAllResponsibility());
    }

    @Test
    void oneCellMaterialReturnsSingleRow() {
        WarehouseId warehouseId = persistWarehouse("MAIN", "Main");
        assign(warehouseId);
        StorageCellId cell = persistCell(warehouseId, "1-01");
        MaterialReference material = material("A100", "Профиль");
        seed(warehouseId, cell, material, StockState.AVAILABLE, "12");

        WarehouseStockCellPage page =
                bundle.api().listStockByCells(warehouseId.value(), null, null, 0, 50);
        assertEquals(1, page.content().size());
        WarehouseStockCellLineView row = page.content().get(0);
        assertEquals("1-01", row.storageCellCode());
        assertEquals("A100", row.article());
        assertEquals(0, new BigDecimal("12").compareTo(row.availableQuantity()));
    }

    @Test
    void sameMaterialInMultipleCellsProducesMultipleRows() {
        WarehouseId warehouseId = persistWarehouse("MAIN", "Main");
        assign(warehouseId);
        StorageCellId c1 = persistCell(warehouseId, "1-01");
        StorageCellId c2 = persistCell(warehouseId, "1-04");
        StorageCellId c3 = persistCell(warehouseId, "1-07");
        MaterialReference material = material("A100", "Профиль");
        seed(warehouseId, c1, material, StockState.AVAILABLE, "12");
        seed(warehouseId, c2, material, StockState.AVAILABLE, "28");
        seed(warehouseId, c3, material, StockState.AVAILABLE, "8");

        WarehouseStockCellPage page =
                bundle.api().listStockByCells(warehouseId.value(), null, null, 0, 50);
        assertEquals(3, page.content().size());
        assertEquals("1-01", page.content().get(0).storageCellCode());
        assertEquals(0, new BigDecimal("12").compareTo(page.content().get(0).availableQuantity()));
        assertEquals("1-04", page.content().get(1).storageCellCode());
        assertEquals(0, new BigDecimal("28").compareTo(page.content().get(1).availableQuantity()));
        assertEquals("1-07", page.content().get(2).storageCellCode());
        assertEquals(0, new BigDecimal("8").compareTo(page.content().get(2).availableQuantity()));
    }

    @Test
    void sameCellMultipleMaterialsProducesMultipleRows() {
        WarehouseId warehouseId = persistWarehouse("MAIN", "Main");
        assign(warehouseId);
        StorageCellId cell = persistCell(warehouseId, "1-01");
        MaterialReference a = material("A100", "Профиль");
        MaterialReference b = material("B200", "Уголок");
        seed(warehouseId, cell, a, StockState.AVAILABLE, "12");
        seed(warehouseId, cell, b, StockState.AVAILABLE, "5");

        WarehouseStockCellPage page =
                bundle.api().listStockByCells(warehouseId.value(), null, null, 0, 50);
        assertEquals(2, page.content().size());
        assertEquals("A100", page.content().get(0).article());
        assertEquals("B200", page.content().get(1).article());
    }

    @Test
    void onlyAvailableQuantityIsReturned() {
        WarehouseId warehouseId = persistWarehouse("MAIN", "Main");
        assign(warehouseId);
        StorageCellId cell = persistCell(warehouseId, "1-01");
        MaterialReference material = material("A100", "Профиль");
        seed(warehouseId, cell, material, StockState.AVAILABLE, "12");
        seed(warehouseId, cell, material, StockState.IN_TRANSIT, "8");
        seed(warehouseId, cell, material, StockState.BLOCKED, "3");

        WarehouseStockCellPage page =
                bundle.api().listStockByCells(warehouseId.value(), null, null, 0, 50);
        assertEquals(1, page.content().size());
        assertEquals(0, new BigDecimal("12").compareTo(page.content().get(0).availableQuantity()));
    }

    @Test
    void allResponsibleWarehousesUseSingleQueryAndOrderByWarehouseThenCell() {
        WarehouseId main = persistWarehouse("MAIN", "Main");
        WarehouseId second = persistWarehouse("SECOND", "Second");
        assign(main);
        assign(second);
        StorageCellId mainCell = persistCell(main, "1-01");
        StorageCellId secondCell = persistCell(second, "B-01");
        MaterialReference material = material("101.208", "Рама VEKA");
        seed(main, mainCell, material, StockState.AVAILABLE, "40");
        seed(second, secondCell, material, StockState.AVAILABLE, "4");

        WarehouseStockCellPage page = bundle.api().listStockByCells(null, null, "101.208", 0, 50);
        assertEquals(2, page.content().size());
        assertEquals("MAIN", page.content().get(0).warehouseCode());
        assertEquals("1-01", page.content().get(0).storageCellCode());
        assertEquals("SECOND", page.content().get(1).warehouseCode());
        assertEquals("B-01", page.content().get(1).storageCellCode());
    }

    @Test
    void cellFilterAndSearchCombine() {
        WarehouseId warehouseId = persistWarehouse("MAIN", "Main");
        assign(warehouseId);
        StorageCellId c1 = persistCell(warehouseId, "1-01");
        StorageCellId c2 = persistCell(warehouseId, "1-04");
        MaterialReference profile = material("A100", "Профиль");
        MaterialReference angle = material("B200", "Уголок");
        seed(warehouseId, c1, profile, StockState.AVAILABLE, "12");
        seed(warehouseId, c1, angle, StockState.AVAILABLE, "5");
        seed(warehouseId, c2, profile, StockState.AVAILABLE, "28");

        WarehouseStockCellPage page =
                bundle.api().listStockByCells(warehouseId.value(), c1.value(), "  a100  ", 0, 50);
        assertEquals(1, page.content().size());
        assertEquals("1-01", page.content().get(0).storageCellCode());
        assertEquals("A100", page.content().get(0).article());
    }

    @Test
    void searchIsCaseInsensitiveByArticleAndName() {
        WarehouseId warehouseId = persistWarehouse("MAIN", "Main");
        assign(warehouseId);
        StorageCellId cell = persistCell(warehouseId, "S-1");
        seed(warehouseId, cell, material("A100", "Профиль алюминиевый"), StockState.AVAILABLE, "5");
        seed(warehouseId, cell, material("B200", "Уголок"), StockState.AVAILABLE, "9");

        assertEquals(
                1,
                bundle.api()
                        .listStockByCells(warehouseId.value(), null, "ПРОФИЛЬ", 0, 50)
                        .content()
                        .size());
        assertEquals(
                2,
                bundle.api()
                        .listStockByCells(warehouseId.value(), null, "   ", 0, 50)
                        .content()
                        .size());
        assertTrue(
                bundle.api()
                        .listStockByCells(warehouseId.value(), null, "неттакого", 0, 50)
                        .content()
                        .isEmpty());
    }

    @Test
    void cellFilterOptionsDistinguishSameCodesAcrossWarehouses() {
        WarehouseId main = persistWarehouse("MAIN", "Main");
        WarehouseId second = persistWarehouse("SECOND", "Second");
        assign(main);
        assign(second);
        StorageCellId mainCell = persistCell(main, "1-01");
        StorageCellId secondCell = persistCell(second, "1-01");

        List<WarehouseStockCellFilterOptionView> all =
                bundle.api().listStockCellFilterOptions(null);
        assertEquals(2, all.size());
        assertEquals("MAIN", all.get(0).warehouseCode());
        assertEquals(mainCell.value(), all.get(0).storageCellId());
        assertEquals("SECOND", all.get(1).warehouseCode());
        assertEquals(secondCell.value(), all.get(1).storageCellId());

        List<WarehouseStockCellFilterOptionView> specific =
                bundle.api().listStockCellFilterOptions(main.value());
        assertEquals(1, specific.size());
        assertEquals(mainCell.value(), specific.get(0).storageCellId());
    }

    @Test
    void paginationIsStableByCellThenArticle() {
        WarehouseId warehouseId = persistWarehouse("MAIN", "Main");
        assign(warehouseId);
        StorageCellId cell = persistCell(warehouseId, "P-1");
        for (int i = 1; i <= 3; i++) {
            seed(
                    warehouseId,
                    cell,
                    material("M" + i, "Material " + i),
                    StockState.AVAILABLE,
                    String.valueOf(i));
        }

        WarehouseStockCellPage first =
                bundle.api().listStockByCells(warehouseId.value(), null, null, 0, 2);
        assertEquals(2, first.content().size());
        assertEquals(3, first.totalElements());
        assertEquals("M1", first.content().get(0).article());
        assertEquals("M2", first.content().get(1).article());

        WarehouseStockCellPage second =
                bundle.api().listStockByCells(warehouseId.value(), null, null, 1, 2);
        assertEquals(1, second.content().size());
        assertEquals("M3", second.content().get(0).article());
    }

    @Test
    void foreignWarehouseAndForeignCellAreDenied() {
        WarehouseId mine = persistWarehouse("MINE", "Mine");
        WarehouseId foreign = persistWarehouse("FRN", "Foreign");
        assign(mine);
        StorageCellId foreignCell = persistCell(foreign, "F-1");
        MaterialReference material = material("X1", "X");
        seed(foreign, foreignCell, material, StockState.AVAILABLE, "10");

        assertThrows(
                AccessDeniedException.class,
                () -> bundle.api().listStockByCells(foreign.value(), null, null, 0, 50));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        bundle.api()
                                .listStockByCells(
                                        mine.value(), foreignCell.value(), null, 0, 50));
        assertThrows(
                AccessDeniedException.class,
                () -> bundle.api().listStockCellFilterOptions(foreign.value()));
    }

    @Test
    void emptyResultDoesNotMutateFacts() {
        WarehouseId warehouseId = persistWarehouse("EMPTY", "Empty");
        assign(warehouseId);
        StorageCellId cell = persistCell(warehouseId, "E-1");
        Map<String, Object> before = snapshotFacts();

        WarehouseStockCellPage page =
                bundle.api().listStockByCells(warehouseId.value(), cell.value(), null, 0, 50);
        assertEquals(0, page.totalElements());
        assertTrue(page.content().isEmpty());
        assertEquals(before, snapshotFacts());
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

    private MaterialReference material(String article, String name) {
        return WarehouseJdbcTestSupport.persistMaterial(
                jdbc, CLOCK, MaterialReference.create(article, name, "", "", "шт."));
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

    private static AuthenticationService fixedUser(UUID userId) {
        SessionSummary session =
                new SessionSummary(
                        SessionId.generate(),
                        UserId.of(userId),
                        Login.of("stock-cells-user"),
                        Instant.parse("2026-09-11T08:00:00Z"));
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
