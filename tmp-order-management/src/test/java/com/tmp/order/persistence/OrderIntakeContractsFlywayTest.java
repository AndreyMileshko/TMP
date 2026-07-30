package com.tmp.order.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies {@code V9__order_intake_contracts.sql}: clean install and upgrade from V8-shaped data.
 */
@Testcontainers
class OrderIntakeContractsFlywayTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateClean() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource());
    }

    @Test
    void v9AddsIntakeColumnsOnCleanDatabase() {
        List<String> itemColumns = columnNames("order_items");
        assertTrue(itemColumns.contains("external_position_number"));

        List<String> lineColumns = columnNames("item_specification_lines");
        assertTrue(lineColumns.contains("line_quantity"));
        assertTrue(lineColumns.contains("color"));
        assertTrue(lineColumns.contains("length_mm"));
        assertTrue(!lineColumns.contains("consumption_norm"));
        assertTrue(!lineColumns.contains("quantity"));

        List<String> payloadLineColumns = columnNames("order_item_revision_payload_line");
        assertTrue(payloadLineColumns.contains("line_quantity"));
        assertTrue(!payloadLineColumns.contains("consumption_norm"));
    }

    @Test
    void v9UpgradePreservesExistingV8SpecificationRows() throws Exception {
        try (PostgreSQLContainer<?> upgradeContainer = new PostgreSQLContainer<>("postgres:16-alpine")) {
            upgradeContainer.start();
            JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource(upgradeContainer));

            Flyway.configure()
                    .dataSource(
                            upgradeContainer.getJdbcUrl(),
                            upgradeContainer.getUsername(),
                            upgradeContainer.getPassword())
                    .locations("classpath:db/migration")
                    .target("8")
                    .load()
                    .migrate();

            UUID orderId = UUID.randomUUID();
            UUID itemId = UUID.randomUUID();
            Instant now = Instant.parse("2026-07-25T12:00:00Z");

            upgradeJdbc.update(
                    """
                    INSERT INTO order_management.orders
                      (order_id, order_number, customer_name, direction, currency, status, version,
                       created_at, updated_at)
                    VALUES (?, 'ORD-V8', 'Customer', 'PRIVATE', 'USD', 'DRAFT', 0, ?, ?)
                    """,
                    orderId,
                    java.sql.Timestamp.from(now),
                    java.sql.Timestamp.from(now));

            upgradeJdbc.update(
                    """
                    INSERT INTO order_management.order_items
                      (order_item_id, order_id, product_code, item_name, status, version, created_at,
                       updated_at)
                    VALUES (?, ?, 'P-1', 'Item', 'DRAFT', 0, ?, ?)
                    """,
                    itemId,
                    orderId,
                    java.sql.Timestamp.from(now),
                    java.sql.Timestamp.from(now));

            upgradeJdbc.update(
                    """
                    INSERT INTO order_management.order_item_revisions
                      (order_item_id, revision_number, revision_status, ordered_quantity)
                    VALUES (?, 1, 'DRAFT', 2)
                    """,
                    itemId);

            upgradeJdbc.update(
                    """
                    INSERT INTO order_management.item_specifications
                      (order_item_id, revision_number, immutable)
                    VALUES (?, 1, FALSE)
                    """,
                    itemId);

            upgradeJdbc.update(
                    """
                    INSERT INTO order_management.item_specification_lines
                      (order_item_id, revision_number, line_number, material_code, material_name,
                       quantity, unit_of_measure, consumption_norm)
                    VALUES (?, 1, 1, 'M-1', 'Glass', 3, 'pcs', 1.2)
                    """,
                    itemId);

            Flyway.configure()
                    .dataSource(
                            upgradeContainer.getJdbcUrl(),
                            upgradeContainer.getUsername(),
                            upgradeContainer.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            BigDecimal lineQty =
                    upgradeJdbc.queryForObject(
                            """
                            SELECT line_quantity FROM order_management.item_specification_lines
                            WHERE order_item_id = ?
                            """,
                            BigDecimal.class,
                            itemId);
            assertNotNull(lineQty);
            assertEquals(0, new BigDecimal("3").compareTo(lineQty));
        }
    }

    private static List<String> columnNames(String table) {
        return jdbc.queryForList(
                """
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'order_management' AND table_name = ?
                ORDER BY column_name
                """,
                String.class,
                table);
    }

    private static DriverManagerDataSource dataSource() {
        return dataSource(POSTGRES);
    }

    private static DriverManagerDataSource dataSource(PostgreSQLContainer<?> container) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(container.getJdbcUrl());
        dataSource.setUsername(container.getUsername());
        dataSource.setPassword(container.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        return dataSource;
    }
}
