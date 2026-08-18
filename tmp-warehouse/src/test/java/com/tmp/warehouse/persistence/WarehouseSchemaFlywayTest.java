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
                        "material_references",
                        "stock_positions",
                        "warehouse_movements",
                        "warehouse_operations",
                        "material_reservation_links")),
                () -> "Missing warehouse tables: " + tables);

        Integer reservedState = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.check_constraints
                WHERE constraint_schema = 'warehouse'
                  AND check_clause ILIKE '%RESERVED%'
                """,
                Integer.class);
        assertEquals(0, reservedState, "RESERVED stock state must not exist in schema");

        Integer forbiddenTables = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'warehouse'
                  AND (
                    table_name ILIKE '%batch%'
                    OR table_name ILIKE '%material_master%'
                  )
                """,
                Integer.class);
        assertEquals(0, forbiddenTables);
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
    void v22AddsOneTimeReceiveLinkAndUniqueIndex() {
        Integer applied =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '22' AND success = TRUE
                        """,
                        Integer.class);
        assertEquals(1, applied);

        Integer receiveColumn =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'warehouse'
                          AND table_name = 'transfer_operation_context'
                          AND column_name = 'receive_operation_id'
                        """,
                        Integer.class);
        assertEquals(1, receiveColumn);

        Integer uniqueIndex =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM pg_indexes
                        WHERE schemaname = 'warehouse'
                          AND indexname = 'uq_transfer_operation_context_receive_operation_id'
                        """,
                        Integer.class);
        assertEquals(1, uniqueIndex);
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

        UUID materialId = insertMaterialReference("MAT-1");
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(
                        """
                        INSERT INTO warehouse.stock_positions (
                            id, warehouse_id, storage_cell_id, material_reference_id, quantity,
                            stock_state, version, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 1, 'AVAILABLE', 0, NOW(), NOW())
                        """,
                        UUID.randomUUID(),
                        otherWarehouseId,
                        cellId,
                        materialId));
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

        UUID negativeMaterialId = insertMaterialReference("MAT-NEG");
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(
                        """
                        INSERT INTO warehouse.stock_positions (
                            id, warehouse_id, storage_cell_id, material_reference_id, quantity,
                            stock_state, version, created_at, updated_at)
                        VALUES (?, ?, ?, ?, -1, 'AVAILABLE', 0, NOW(), NOW())
                        """,
                        UUID.randomUUID(),
                        warehouseId,
                        cellId,
                        negativeMaterialId));

        UUID reservedMaterialId = insertMaterialReference("MAT-RES");
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(
                        """
                        INSERT INTO warehouse.stock_positions (
                            id, warehouse_id, storage_cell_id, material_reference_id, quantity,
                            stock_state, version, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 1, 'RESERVED', 0, NOW(), NOW())
                        """,
                        UUID.randomUUID(),
                        warehouseId,
                        cellId,
                        reservedMaterialId));
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
        UUID materialId = insertMaterialReference("MAT-M");
        jdbc.update(
                """
                INSERT INTO warehouse.stock_positions (
                    id, warehouse_id, storage_cell_id, material_reference_id, quantity,
                    stock_state, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 10, 'AVAILABLE', 0, NOW(), NOW())
                """,
                positionId,
                warehouseId,
                cellId,
                materialId);

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

    private UUID insertMaterialReference(String article) {
        UUID materialId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.material_references (
                    id, article, name, color, size, unit_of_measure, created_at, updated_at)
                VALUES (?, ?, ?, '', '', '', NOW(), NOW())
                """,
                materialId,
                article,
                article);
        return materialId;
    }
}
