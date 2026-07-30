package com.tmp.order.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Applies {@code V6__order_payload_schema.sql} (and prior classpath migrations) and verifies typed
 * payload tables, FK cascade delete, optimistic-lock column and absence of JSON/BLOB columns.
 */
@Testcontainers
class OrderPayloadSchemaFlywayTest {

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
    void v6CreatesTypedPayloadTablesWithoutJsonOrBlobColumns() {
        List<String> tables =
                jdbc.queryForList(
                        """
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'order_management'
                        ORDER BY table_name
                        """,
                        String.class);

        assertTrue(
                tables.containsAll(
                        List.of(
                                "order_document_payload",
                                "order_create_payload",
                                "order_update_payload",
                                "order_status_payload",
                                "order_item_create_payload",
                                "order_item_update_payload",
                                "order_item_status_payload",
                                "order_item_revision_create_payload",
                                "order_item_revision_update_payload",
                                "order_item_revision_approve_payload",
                                "order_item_revision_payload_line")),
                () -> "Missing payload tables: " + tables);

        assertTrue(
                tables.containsAll(
                        List.of(
                                "orders",
                                "order_items",
                                "order_item_revisions",
                                "item_specifications",
                                "item_specification_lines")),
                () -> "Missing aggregate tables from V8: " + tables);
        assertTrue(
                tables.contains("order_document_processing"),
                "V7 processing table must be present when full classpath migrations apply");

        Integer jsonOrBlob =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'order_management'
                          AND (
                            data_type IN ('json', 'jsonb', 'bytea')
                            OR udt_name IN ('json', 'jsonb', 'bytea')
                            OR column_name ILIKE '%json%'
                            OR column_name ILIKE '%blob%'
                            OR column_name ILIKE '%serializ%'
                          )
                        """,
                        Integer.class);
        assertEquals(0, jsonOrBlob);

        Integer payloadRevisionColumn =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'order_management'
                          AND table_name = 'order_document_payload'
                          AND column_name = 'payload_revision'
                        """,
                        Integer.class);
        assertEquals(1, payloadRevisionColumn);
    }

    @Test
    void foreignKeysCascadeDeleteDraftMetadataAndTypedRows() {
        UUID documentId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-25T15:00:00Z");

        jdbc.update(
                """
                INSERT INTO order_management.order_document_payload
                  (document_id, document_type_code, payload_schema_version, payload_revision,
                   created_at, updated_at)
                VALUES (?, 'ORDER_ITEM_REVISION_UPDATE', 1, 0, ?, ?)
                """,
                documentId,
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now));

        jdbc.update(
                """
                INSERT INTO order_management.order_item_revision_update_payload
                  (document_id, order_item_id, revision_number, ordered_quantity)
                VALUES (?, ?, 1, ?)
                """,
                documentId,
                UUID.randomUUID(),
                new BigDecimal("2.0000"));

        jdbc.update(
                """
                INSERT INTO order_management.order_item_revision_payload_line
                  (document_id, line_number, material_code, material_name, color, length_mm,
                   line_quantity, unit_of_measure)
                VALUES (?, 1, 'M-1', 'Material', NULL, NULL, ?, 'pcs')
                """,
                documentId,
                new BigDecimal("1.0000"));

        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM order_management.order_item_revision_payload_line WHERE document_id = ?",
                        Integer.class,
                        documentId));

        jdbc.update(
                "DELETE FROM order_management.order_document_payload WHERE document_id = ?",
                documentId);

        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM order_management.order_item_revision_update_payload WHERE document_id = ?",
                        Integer.class,
                        documentId));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM order_management.order_item_revision_payload_line WHERE document_id = ?",
                        Integer.class,
                        documentId));
    }

    @Test
    void typedTablesReferenceOrderDocumentPayloadByDocumentId() {
        Integer fkCount =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.table_constraints tc
                        JOIN information_schema.constraint_column_usage ccu
                          ON tc.constraint_name = ccu.constraint_name
                         AND tc.table_schema = ccu.table_schema
                        WHERE tc.table_schema = 'order_management'
                          AND tc.constraint_type = 'FOREIGN KEY'
                          AND ccu.table_name = 'order_document_payload'
                          AND ccu.column_name = 'document_id'
                        """,
                        Integer.class);
        assertTrue(fkCount != null && fkCount >= 10, "Expected FK to order_document_payload for typed tables");
    }

    @Test
    void flywayHistoryIncludesV6AndV8() {
        Integer v6 =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '6' AND success = TRUE
                        """,
                        Integer.class);
        assertEquals(1, v6);
        Integer v8 =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '8' AND success = TRUE
                        """,
                        Integer.class);
        assertEquals(1, v8);
    }
}
