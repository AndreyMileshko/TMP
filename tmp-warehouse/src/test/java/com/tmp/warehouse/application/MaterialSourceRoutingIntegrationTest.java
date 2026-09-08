package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import com.tmp.warehouse.api.WarehouseApi.MaterialDemand;
import com.tmp.warehouse.api.WarehouseApi.MaterialSourceRoutingOutcome;
import com.tmp.warehouse.api.WarehouseApi.MaterialSourceRoutingResult;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.persistence.JdbcAvailableStockAggregationQuery;
import com.tmp.warehouse.testsupport.WarehouseIntegrationTestSupport;
import com.tmp.warehouse.testsupport.WarehouseJdbcTestSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * JDBC integration: batch AVAILABLE aggregation, routing, no stock mutation, responsibility not
 * applied to candidates.
 */
@Testcontainers
class MaterialSourceRoutingIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-08T06:00:00Z"), ZoneOffset.UTC);
    private static final UUID MASTER = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    private WarehouseIntegrationTestSupport.ApiBundle bundle;
    private MaterialSourceRoutingService routing;
    private JdbcAvailableStockAggregationQuery aggregationQuery;

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
                        fixedUser(MASTER),
                        warehouseId -> {
                            throw new AssertionError(
                                    "routing must not invoke responsibility guard");
                        });
        aggregationQuery = new JdbcAvailableStockAggregationQuery(jdbc);
        routing = new MaterialSourceRoutingService(aggregationQuery);
    }

    @Test
    void jdbcAggregationIgnoresTransitBlockedInactiveAndSelectsAvailableSource() {
        WarehouseId dest = WarehouseId.generate();
        WarehouseId sourceA = WarehouseId.generate();
        WarehouseId sourceB = WarehouseId.generate();
        WarehouseId inactiveWh = WarehouseId.generate();
        bundle.catalog().save(Warehouse.create(dest, "DEST", "Destination"));
        bundle.catalog().save(Warehouse.create(sourceA, "SRC-A", "Source A"));
        bundle.catalog().save(Warehouse.create(sourceB, "SRC-B", "Source B"));
        bundle.catalog().save(Warehouse.of(inactiveWh, "INACT", "Inactive", false));

        StorageCellId cellA = StorageCellId.generate();
        StorageCellId cellB = StorageCellId.generate();
        StorageCellId cellInactive = StorageCellId.generate();
        StorageCellId cellInactiveWh = StorageCellId.generate();
        bundle.catalog().save(StorageCell.create(cellA, sourceA, "A-01"));
        bundle.catalog().save(StorageCell.create(cellB, sourceB, "B-01"));
        bundle.catalog().save(StorageCell.of(cellInactive, sourceA, "A-OFF", false));
        bundle.catalog().save(StorageCell.create(cellInactiveWh, inactiveWh, "I-01"));

        MaterialReference material =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc, CLOCK, MaterialReference.legacyArticle("ART-R1"));

        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                sourceA, cellA, material, StockState.AVAILABLE, StockQuantity.of(20)));
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                sourceA,
                                cellA,
                                material,
                                StockState.IN_TRANSIT,
                                StockQuantity.of(200)));
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                sourceA, cellA, material, StockState.BLOCKED, StockQuantity.of(500)));
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                sourceA,
                                cellInactive,
                                material,
                                StockState.AVAILABLE,
                                StockQuantity.of(999)));
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                inactiveWh,
                                cellInactiveWh,
                                material,
                                StockState.AVAILABLE,
                                StockQuantity.of(888)));
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                sourceB, cellB, material, StockState.AVAILABLE, StockQuantity.of(30)));

        List<MaterialSourceRoutingResult> results =
                routing.routeMaterials(
                        dest.value(),
                        List.of(
                                new MaterialDemand(
                                        "d1", material.id().value(), new BigDecimal("25"))));

        assertEquals(1, results.size());
        assertEquals(sourceB.value(), results.get(0).sourceWarehouseId());
        assertEquals(0, new BigDecimal("30").compareTo(results.get(0).availableAtSelectedSource()));

        MaterialReference material2 =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc, CLOCK, MaterialReference.legacyArticle("ART-R2"));
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                sourceA,
                                cellA,
                                material2,
                                StockState.AVAILABLE,
                                StockQuantity.of(15)));
        var rows =
                aggregationQuery.findAvailableByMaterials(
                        List.of(material.id().value(), material2.id().value()));
        assertEquals(3, rows.size());
    }

    @Test
    void publicApiRouteMaterialsDoesNotMutateWarehouseFacts() {
        WarehouseId dest = WarehouseId.generate();
        WarehouseId source = WarehouseId.generate();
        bundle.catalog().save(Warehouse.create(dest, "D", "Dest"));
        bundle.catalog().save(Warehouse.create(source, "S", "Source"));
        StorageCellId cell = StorageCellId.generate();
        bundle.catalog().save(StorageCell.create(cell, source, "S-01"));
        MaterialReference material =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc, CLOCK, MaterialReference.legacyArticle("ART-NM"));
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                source, cell, material, StockState.AVAILABLE, StockQuantity.of(40)));

        Map<String, Object> before = snapshotFacts();
        MaterialSourceRoutingResult result =
                bundle.api().routeMaterial(dest.value(), material.id().value(), new BigDecimal("10"));
        Map<String, Object> after = snapshotFacts();

        assertEquals(MaterialSourceRoutingOutcome.SOURCE_SELECTED, result.outcome());
        assertEquals(source.value(), result.sourceWarehouseId());
        assertEquals(before, after);
    }

    @Test
    void routingIsGlobalEvenWhenUserResponsibleOnlyForDestination() {
        WarehouseId dest = WarehouseId.generate();
        WarehouseId source = WarehouseId.generate();
        bundle.catalog().save(Warehouse.create(dest, "D", "Dest"));
        bundle.catalog().save(Warehouse.create(source, "S", "Source"));
        StorageCellId cell = StorageCellId.generate();
        bundle.catalog().save(StorageCell.create(cell, source, "S-01"));
        MaterialReference material =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc, CLOCK, MaterialReference.legacyArticle("ART-GL"));
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                source, cell, material, StockState.AVAILABLE, StockQuantity.of(50)));

        bundle.responsibilities().assign(MASTER, dest);

        MaterialSourceRoutingResult result =
                bundle.api().routeMaterial(dest.value(), material.id().value(), new BigDecimal("20"));

        assertEquals(source.value(), result.sourceWarehouseId());
        assertTrue(
                bundle.responsibilities().listWarehouseIdsForUser(MASTER).stream()
                        .noneMatch(id -> id.equals(source)));
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
                        "SELECT COUNT(*) FROM warehouse.warehouse_movements", Long.class),
                "transferCtx",
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.transfer_operation_context", Long.class));
    }

    private static AuthenticationService fixedUser(UUID userId) {
        SessionSummary session =
                new SessionSummary(
                        SessionId.generate(),
                        UserId.of(userId),
                        Login.of("master"),
                        Instant.parse("2026-09-08T06:00:00Z"));
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

    private static com.tmp.security.api.AuthorizationService allowAllAuth() {
        return new com.tmp.security.api.AuthorizationService() {
            @Override
            public boolean hasPermission(com.tmp.security.api.PermissionId permissionId) {
                return true;
            }

            @Override
            public void requirePermission(com.tmp.security.api.PermissionId permissionId) {}

            @Override
            public java.util.Set<com.tmp.security.api.PermissionId> effectivePermissions() {
                return java.util.Set.of();
            }
        };
    }
}
