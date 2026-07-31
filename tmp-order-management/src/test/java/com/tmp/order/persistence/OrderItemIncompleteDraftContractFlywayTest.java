package com.tmp.order.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies {@code V11__order_item_incomplete_draft_contract.sql}. */
@Testcontainers
class OrderItemIncompleteDraftContractFlywayTest {

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
        jdbc = new JdbcTemplate(dataSource(POSTGRES));
    }

    @Test
    void cleanDatabaseMigratesToV11AndAllowsNullItemCommercialColumns() {
        assertTrue(isNullable("order_items", "product_code"));
        assertTrue(isNullable("order_items", "item_name"));
        assertTrue(isNullable("order_item_create_payload", "product_code"));
        assertTrue(isNullable("order_item_create_payload", "item_name"));
        assertTrue(isNullable("order_item_update_payload", "product_code"));
        assertTrue(isNullable("order_item_update_payload", "item_name"));

        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-31T03:00:00Z");
        insertOrder(orderId, now);
        jdbc.update(
                """
                INSERT INTO order_management.order_items
                  (order_item_id, order_id, product_code, item_name, comments,
                   external_position_number, status, active_revision_number, draft_revision_number,
                   version, created_at, updated_at)
                VALUES (?, ?, NULL, NULL, NULL, 'EXT-NULL', 'DRAFT', NULL, 1, 0, ?, ?)
                """,
                itemId,
                orderId,
                Timestamp.from(now),
                Timestamp.from(now));

        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT product_code, item_name, external_position_number FROM order_management.order_items WHERE order_item_id = ?",
                        itemId);
        assertNull(row.get("product_code"));
        assertNull(row.get("item_name"));
        assertEquals("EXT-NULL", row.get("external_position_number"));
    }

    @Test
    void v10DatabaseUpgradesToV11PreservingExistingItemData() {
        try (PostgreSQLContainer<?> upgradeContainer = new PostgreSQLContainer<>("postgres:16-alpine")) {
            upgradeContainer.start();
            JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource(upgradeContainer));

            Flyway.configure()
                    .dataSource(
                            upgradeContainer.getJdbcUrl(),
                            upgradeContainer.getUsername(),
                            upgradeContainer.getPassword())
                    .locations("classpath:db/migration")
                    .target("10")
                    .load()
                    .migrate();

            UUID orderId = UUID.randomUUID();
            UUID itemId = UUID.randomUUID();
            Instant now = Instant.parse("2026-07-31T04:00:00Z");
            insertOrder(upgradeJdbc, orderId, now);
            upgradeJdbc.update(
                    """
                    INSERT INTO order_management.order_items
                      (order_item_id, order_id, product_code, item_name, comments,
                       external_position_number, status, active_revision_number, draft_revision_number,
                       version, created_at, updated_at)
                    VALUES (?, ?, 'P-KEEP', 'Keep Name', NULL, 'EXT-KEEP', 'DRAFT', NULL, 1, 0, ?, ?)
                    """,
                    itemId,
                    orderId,
                    Timestamp.from(now),
                    Timestamp.from(now));

            Flyway.configure()
                    .dataSource(
                            upgradeContainer.getJdbcUrl(),
                            upgradeContainer.getUsername(),
                            upgradeContainer.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            Map<String, Object> row =
                    upgradeJdbc.queryForMap(
                            "SELECT product_code, item_name FROM order_management.order_items WHERE order_item_id = ?",
                            itemId);
            assertEquals("P-KEEP", row.get("product_code"));
            assertEquals("Keep Name", row.get("item_name"));
            assertTrue(isNullable(upgradeJdbc, "order_items", "product_code"));
            assertTrue(isNullable(upgradeJdbc, "order_items", "item_name"));
        }
    }

    private static void insertOrder(UUID orderId, Instant now) {
        insertOrder(jdbc, orderId, now);
    }

    private static void insertOrder(JdbcTemplate target, UUID orderId, Instant now) {
        target.update(
                """
                INSERT INTO order_management.orders
                  (order_id, order_number, status, version, created_at, updated_at)
                VALUES (?, ?, 'DRAFT', 0, ?, ?)
                """,
                orderId,
                "ORD-" + orderId.toString().substring(0, 8),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    private static boolean isNullable(String table, String column) {
        return isNullable(jdbc, table, column);
    }

    private static boolean isNullable(JdbcTemplate target, String table, String column) {
        Boolean nullable =
                target.queryForObject(
                        """
                        SELECT is_nullable = 'YES'
                        FROM information_schema.columns
                        WHERE table_schema = 'order_management'
                          AND table_name = ?
                          AND column_name = ?
                        """,
                        Boolean.class,
                        table,
                        column);
        return Boolean.TRUE.equals(nullable);
    }

    private static DataSource dataSource(PostgreSQLContainer<?> container) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(container.getJdbcUrl());
        dataSource.setUsername(container.getUsername());
        dataSource.setPassword(container.getPassword());
        return dataSource;
    }
}
