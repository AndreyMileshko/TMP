package com.tmp.production.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
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
 * Isolated V43 → V44 Material Requirement Submit lifecycle migration.
 */
@Testcontainers
class MaterialRequirementSubmissionV43ToV44MigrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void connect() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUsername(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        dataSource = ds;
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void v44PreservesDraftAndAddsSubmissionConstraints() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("43")
                .load()
                .migrate();

        UUID requirementId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-10T09:00:00Z");
        jdbc.update(
                """
                INSERT INTO production.material_requirements (
                    id, source_order_id, destination_warehouse_id,
                    created_at, updated_at, version, status)
                VALUES (?, ?, ?, ?, ?, 0, 'DRAFT')
                """,
                requirementId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Timestamp.from(now),
                Timestamp.from(now));

        Integer warehouseOpsBefore = optionalCount("warehouse.warehouse_operations");
        Integer stockBefore = optionalCount("warehouse.stock_positions");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("44")
                .load()
                .migrate();

        Integer applied44 =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '44' AND success = TRUE
                        """,
                        Integer.class);
        assertEquals(1, applied44);

        String status =
                jdbc.queryForObject(
                        "SELECT status FROM production.material_requirements WHERE id = ?",
                        String.class,
                        requirementId);
        assertEquals("DRAFT", status);
        assertNull(
                jdbc.queryForObject(
                        "SELECT submitted_at FROM production.material_requirements WHERE id = ?",
                        Timestamp.class,
                        requirementId));
        assertNull(
                jdbc.queryForObject(
                        "SELECT submitted_by FROM production.material_requirements WHERE id = ?",
                        String.class,
                        requirementId));

        jdbc.update(
                """
                UPDATE production.material_requirements
                SET status = 'SUBMITTED', submitted_at = ?, submitted_by = 'user-1'
                WHERE id = ?
                """,
                Timestamp.from(now),
                requirementId);
        assertEquals(
                "SUBMITTED",
                jdbc.queryForObject(
                        "SELECT status FROM production.material_requirements WHERE id = ?",
                        String.class,
                        requirementId));

        UUID invalidId = UUID.randomUUID();
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                """
                                INSERT INTO production.material_requirements (
                                    id, source_order_id, destination_warehouse_id,
                                    created_at, updated_at, version, status, submitted_at, submitted_by)
                                VALUES (?, ?, ?, ?, ?, 0, 'SUBMITTED', NULL, NULL)
                                """,
                                invalidId,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                Timestamp.from(now),
                                Timestamp.from(now)));

        assertEquals(warehouseOpsBefore, optionalCount("warehouse.warehouse_operations"));
        assertEquals(stockBefore, optionalCount("warehouse.stock_positions"));
        assertTrue(tableExists("production.material_requirement_generated_documents"));
        assertTrue(tableExists("production.material_requirement_routing_snapshot"));
    }

    private static Integer optionalCount(String qualifiedTable) {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM " + qualifiedTable, Integer.class);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean tableExists(String qualified) {
        String[] parts = qualified.split("\\.");
        Integer n =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = ? AND table_name = ?
                        """,
                        Integer.class,
                        parts[0],
                        parts[1]);
        return n != null && n == 1;
    }
}
