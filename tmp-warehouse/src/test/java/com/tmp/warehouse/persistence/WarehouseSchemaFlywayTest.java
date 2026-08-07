package com.tmp.warehouse.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Applies classpath Flyway migrations (platform baseline + V15 warehouse schema) and verifies
 * warehouse tables, referential integrity and non-negative quantity constraints.
 */
@Testcontainers
class WarehouseSchemaFlywayTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void v15CreatesWarehouseCoreTables() {
        List<String> tables = jdbc.queryForList(
                """
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'warehouse'
                ORDER BY table_name
                """,
                String.class);

        assertTrue(
                tables.containsAll(List.of(
                        "warehouses",
                        "storage_cells",
                        "stock_positions",
                        "warehouse_movements",
                        "warehouse_operations")),
                () -> "Missing warehouse tables: " + tables);

        Integer reservedState = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.check_constraints
                WHERE constraint_schema = 'warehouse'
                  AND check_clause ILIKE '%RESERVED%'
                """,
                Integer.class);
        assertEquals(0, reservedState, "RESERVED stock state must not exist in schema");

        Integer batchTables = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'warehouse'
                  AND (
                    table_name ILIKE '%batch%'
                    OR table_name ILIKE '%material_master%'
                    OR table_name ILIKE '%reservation%'
                  )
                """,
                Integer.class);
        assertEquals(0, batchTables);
    }

    @Test
    void flywayRecordsV15Migration() {
        Integer applied = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version = '15' AND success = TRUE
                """,
                Integer.class);
        assertEquals(1, applied);
    }

    @Test
    void warehouseCodeIsUniqueAndCellBelongsToWarehouse() {
        UUID warehouseId = UUID.randomUUID();
        UUID otherWarehouseId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();

        jdbc.update(
                """
                INSERT INTO warehouse.warehouses (id, code, name, active, version, created_at, updated_at)
                VALUES (?, 'WH-A', 'Alpha', TRUE, 0, NOW(), NOW())
                """,
                warehouseId);
        jdbc.update(
                """
                INSERT INTO warehouse.warehouses (id, code, name, active, version, created_at, updated_at)
                VALUES (?, 'WH-B', 'Beta', TRUE, 0, NOW(), NOW())
                """,
                otherWarehouseId);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(
                        """
                        INSERT INTO warehouse.warehouses
                            (id, code, name, active, version, created_at, updated_at)
                        VALUES (?, 'WH-A', 'Dup', TRUE, 0, NOW(), NOW())
                        """,
                        UUID.randomUUID()));

        jdbc.update(
                """
                INSERT INTO warehouse.storage_cells
                    (id, warehouse_id, code, active, version, created_at, updated_at)
                VALUES (?, ?, 'A-01', TRUE, 0, NOW(), NOW())
                """,
                cellId,
                warehouseId);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(
                        """
                        INSERT INTO warehouse.stock_positions (
                            id, warehouse_id, storage_cell_id, material_reference, quantity,
                            stock_state, version, created_at, updated_at)
                        VALUES (?, ?, ?, 'MAT-1', 1, 'AVAILABLE', 0, NOW(), NOW())
                        """,
                        UUID.randomUUID(),
                        otherWarehouseId,
                        cellId));
    }

    @Test
    void negativeQuantityRejectedAndReservedStateRejected() {
        UUID warehouseId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.warehouses (id, code, name, active, version, created_at, updated_at)
                VALUES (?, 'WH-Q', 'Qty', TRUE, 0, NOW(), NOW())
                """,
                warehouseId);
        jdbc.update(
                """
                INSERT INTO warehouse.storage_cells
                    (id, warehouse_id, code, active, version, created_at, updated_at)
                VALUES (?, ?, 'Q-01', TRUE, 0, NOW(), NOW())
                """,
                cellId,
                warehouseId);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(
                        """
                        INSERT INTO warehouse.stock_positions (
                            id, warehouse_id, storage_cell_id, material_reference, quantity,
                            stock_state, version, created_at, updated_at)
                        VALUES (?, ?, ?, 'MAT-NEG', -1, 'AVAILABLE', 0, NOW(), NOW())
                        """,
                        UUID.randomUUID(),
                        warehouseId,
                        cellId));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(
                        """
                        INSERT INTO warehouse.stock_positions (
                            id, warehouse_id, storage_cell_id, material_reference, quantity,
                            stock_state, version, created_at, updated_at)
                        VALUES (?, ?, ?, 'MAT-RES', 1, 'RESERVED', 0, NOW(), NOW())
                        """,
                        UUID.randomUUID(),
                        warehouseId,
                        cellId));
    }

    @Test
    void movementRequiresStockPositionAndAllowsQuantityDelta() {
        UUID warehouseId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.warehouses (id, code, name, active, version, created_at, updated_at)
                VALUES (?, 'WH-M', 'Move', TRUE, 0, NOW(), NOW())
                """,
                warehouseId);
        jdbc.update(
                """
                INSERT INTO warehouse.storage_cells
                    (id, warehouse_id, code, active, version, created_at, updated_at)
                VALUES (?, ?, 'M-01', TRUE, 0, NOW(), NOW())
                """,
                cellId,
                warehouseId);
        jdbc.update(
                """
                INSERT INTO warehouse.stock_positions (
                    id, warehouse_id, storage_cell_id, material_reference, quantity,
                    stock_state, version, created_at, updated_at)
                VALUES (?, ?, ?, 'MAT-M', 10, 'AVAILABLE', 0, NOW(), NOW())
                """,
                positionId,
                warehouseId,
                cellId);

        jdbc.update(
                """
                INSERT INTO warehouse.warehouse_movements
                    (id, stock_position_id, operation_type, quantity_delta, created_at)
                VALUES (?, ?, 'RECEIPT', ?, NOW())
                """,
                UUID.randomUUID(),
                positionId,
                new BigDecimal("5.000000"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(
                        """
                        INSERT INTO warehouse.warehouse_movements
                            (id, stock_position_id, operation_type, quantity_delta, created_at)
                        VALUES (?, ?, 'RECEIPT', 1, NOW())
                        """,
                        UUID.randomUUID(),
                        UUID.randomUUID()));
    }
}
