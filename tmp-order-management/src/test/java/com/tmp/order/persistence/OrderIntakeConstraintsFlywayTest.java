package com.tmp.order.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies {@code V10__order_intake_constraints.sql}. */
@Testcontainers
class OrderIntakeConstraintsFlywayTest {

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
    void v10AddsWholeNumberConstraintsOnCleanDatabase() {
        assertTrue(constraintExists("order_item_revisions", "chk_order_item_revisions_quantity_whole"));
        assertTrue(
                constraintExists(
                        "order_item_create_payload", "chk_order_item_create_payload_qty_whole"));
        assertTrue(
                constraintExists(
                        "order_item_revision_update_payload",
                        "chk_order_item_revision_update_qty_whole"));
    }

    @Test
    void v10AlignsNumericPrecisionToNumeric196() {
        assertEquals("numeric", columnType("order_item_create_payload", "ordered_quantity"));
        assertNumericPrecisionScale("order_item_create_payload", "ordered_quantity", 19, 6);
        assertNumericPrecisionScale(
                "order_item_revision_update_payload", "ordered_quantity", 19, 6);
        assertNumericPrecisionScale(
                "order_item_revision_payload_line", "line_quantity", 19, 6);
        assertNumericPrecisionScale("order_item_revision_payload_line", "length_mm", 19, 6);
        assertNumericPrecisionScale("item_specification_lines", "line_quantity", 19, 6);
        assertNumericPrecisionScale("item_specification_lines", "length_mm", 19, 6);
    }

    @Test
    void positiveWholeOrderedQuantityIsAccepted() {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-30T10:00:00Z");
        insertDraftOrderItem(orderId, itemId, now);
        jdbc.update(
                """
                INSERT INTO order_management.order_item_revisions
                  (order_item_id, revision_number, revision_status, ordered_quantity)
                VALUES (?, 1, 'DRAFT', 8)
                """,
                itemId);
        assertEquals(
                0,
                new BigDecimal("8")
                        .compareTo(
                                jdbc.queryForObject(
                                        """
                                        SELECT ordered_quantity
                                        FROM order_management.order_item_revisions
                                        WHERE order_item_id = ?
                                        """,
                                        BigDecimal.class,
                                        itemId)));
    }

    @Test
    void zeroNegativeAndFractionalOrderedQuantityAreRejected() {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-30T10:00:00Z");
        insertDraftOrderItem(orderId, itemId, now);
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                """
                                INSERT INTO order_management.order_item_revisions
                                  (order_item_id, revision_number, revision_status, ordered_quantity)
                                VALUES (?, 1, 'DRAFT', 0)
                                """,
                                itemId));
        UUID itemId2 = UUID.randomUUID();
        insertDraftOrderItem(UUID.randomUUID(), itemId2, now);
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                """
                                INSERT INTO order_management.order_item_revisions
                                  (order_item_id, revision_number, revision_status, ordered_quantity)
                                VALUES (?, 1, 'DRAFT', -1)
                                """,
                                itemId2));
        UUID itemId3 = UUID.randomUUID();
        insertDraftOrderItem(UUID.randomUUID(), itemId3, now);
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                """
                                INSERT INTO order_management.order_item_revisions
                                  (order_item_id, revision_number, revision_status, ordered_quantity)
                                VALUES (?, 1, 'DRAFT', 1.5)
                                """,
                                itemId3));
    }

    @Test
    void v10UpgradeFromV9PreservesValidData() throws Exception {
        try (PostgreSQLContainer<?> upgradeContainer = new PostgreSQLContainer<>("postgres:16-alpine")) {
            upgradeContainer.start();
            JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource(upgradeContainer));

            Flyway.configure()
                    .dataSource(
                            upgradeContainer.getJdbcUrl(),
                            upgradeContainer.getUsername(),
                            upgradeContainer.getPassword())
                    .locations("classpath:db/migration")
                    .target("9")
                    .load()
                    .migrate();

            UUID orderId = UUID.randomUUID();
            UUID itemId = UUID.randomUUID();
            Instant now = Instant.parse("2026-07-30T10:00:00Z");
            insertDraftOrderItem(upgradeJdbc, orderId, itemId, now);
            upgradeJdbc.update(
                    """
                    INSERT INTO order_management.order_item_revisions
                      (order_item_id, revision_number, revision_status, ordered_quantity)
                    VALUES (?, 1, 'DRAFT', 3)
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

            assertEquals(
                    0,
                    new BigDecimal("3")
                            .compareTo(
                                    upgradeJdbc.queryForObject(
                                            """
                                            SELECT ordered_quantity
                                            FROM order_management.order_item_revisions
                                            WHERE order_item_id = ?
                                            """,
                                            BigDecimal.class,
                                            itemId)));
        }
    }

    @Test
    void v10MigrationFailsWhenFractionalOrderedQuantityExists() throws Exception {
        try (PostgreSQLContainer<?> blockedContainer = new PostgreSQLContainer<>("postgres:16-alpine")) {
            blockedContainer.start();
            JdbcTemplate blockedJdbc = new JdbcTemplate(dataSource(blockedContainer));

            Flyway.configure()
                    .dataSource(
                            blockedContainer.getJdbcUrl(),
                            blockedContainer.getUsername(),
                            blockedContainer.getPassword())
                    .locations("classpath:db/migration")
                    .target("9")
                    .load()
                    .migrate();

            UUID orderId = UUID.randomUUID();
            UUID itemId = UUID.randomUUID();
            Instant now = Instant.parse("2026-07-30T10:00:00Z");
            insertDraftOrderItem(blockedJdbc, orderId, itemId, now);
            blockedJdbc.update(
                    """
                    INSERT INTO order_management.order_item_revisions
                      (order_item_id, revision_number, revision_status, ordered_quantity)
                    VALUES (?, 1, 'DRAFT', 2.5)
                    """,
                    itemId);

            FlywayException ex =
                    assertThrows(
                            FlywayException.class,
                            () ->
                                    Flyway.configure()
                                            .dataSource(
                                                    blockedContainer.getJdbcUrl(),
                                                    blockedContainer.getUsername(),
                                                    blockedContainer.getPassword())
                                            .locations("classpath:db/migration")
                                            .load()
                                            .migrate());
            String message =
                    ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage();
            assertTrue(message.contains("fractional ordered_quantity"));
        }
    }

    @Test
    void payloadLineNumericPrecisionPreservesScaleWithoutLoss() {
        UUID documentId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        jdbc.update(
                """
                INSERT INTO order_management.order_document_payload
                  (document_id, document_type_code, payload_schema_version, payload_revision,
                   created_at, updated_at)
                VALUES (?, 'ORDER_ITEM_REVISION_UPDATE', 1, 0, ?, ?)
                """,
                documentId,
                Timestamp.from(now),
                Timestamp.from(now));
        jdbc.update(
                """
                INSERT INTO order_management.order_item_revision_update_payload
                  (document_id, order_item_id, revision_number, ordered_quantity)
                VALUES (?, ?, 1, 4)
                """,
                documentId,
                UUID.randomUUID());
        jdbc.update(
                """
                INSERT INTO order_management.order_item_revision_payload_line
                  (document_id, line_number, material_code, material_name, color, length_mm,
                   line_quantity, unit_of_measure)
                VALUES (?, 1, 'M-1', 'Glass', 'White', 1200.123456, 16.500000, 'pcs')
                """,
                documentId);

        Map<String, Object> row =
                jdbc.queryForMap(
                        """
                        SELECT length_mm, line_quantity
                        FROM order_management.order_item_revision_payload_line
                        WHERE document_id = ?
                        """,
                        documentId);
        assertEquals(0, new BigDecimal("1200.123456").compareTo((BigDecimal) row.get("length_mm")));
        assertEquals(0, new BigDecimal("16.500000").compareTo((BigDecimal) row.get("line_quantity")));

        jdbc.update(
                "DELETE FROM order_management.order_document_payload WHERE document_id = ?",
                documentId);
    }

    private static boolean constraintExists(String table, String constraint) {
        Integer count =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.table_constraints
                        WHERE table_schema = 'order_management'
                          AND table_name = ?
                          AND constraint_name = ?
                        """,
                        Integer.class,
                        table,
                        constraint);
        return count != null && count > 0;
    }

    private static String columnType(String table, String column) {
        return jdbc.queryForObject(
                """
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'order_management'
                  AND table_name = ?
                  AND column_name = ?
                """,
                String.class,
                table,
                column);
    }

    private static void assertNumericPrecisionScale(
            String table, String column, int precision, int scale) {
        Map<String, Object> meta =
                jdbc.queryForMap(
                        """
                        SELECT numeric_precision, numeric_scale
                        FROM information_schema.columns
                        WHERE table_schema = 'order_management'
                          AND table_name = ?
                          AND column_name = ?
                        """,
                        table,
                        column);
        assertEquals(precision, ((Number) meta.get("numeric_precision")).intValue());
        assertEquals(scale, ((Number) meta.get("numeric_scale")).intValue());
    }

    private static void insertDraftOrderItem(UUID orderId, UUID itemId, Instant now) {
        insertDraftOrderItem(jdbc, orderId, itemId, now);
    }

    private static void insertDraftOrderItem(
            JdbcTemplate template, UUID orderId, UUID itemId, Instant now) {
        template.update(
                """
                INSERT INTO order_management.orders
                  (order_id, order_number, status, version, created_at, updated_at)
                VALUES (?, ?, 'DRAFT', 0, ?, ?)
                """,
                orderId,
                "ORD-" + orderId.toString().substring(0, 8),
                Timestamp.from(now),
                Timestamp.from(now));
        template.update(
                """
                INSERT INTO order_management.order_items
                  (order_item_id, order_id, product_code, item_name, status, version, created_at,
                   updated_at)
                VALUES (?, ?, 'P-1', 'Item', 'DRAFT', 0, ?, ?)
                """,
                itemId,
                orderId,
                Timestamp.from(now),
                Timestamp.from(now));
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
