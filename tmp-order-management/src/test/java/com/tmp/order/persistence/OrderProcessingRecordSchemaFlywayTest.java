package com.tmp.order.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies {@code V7__order_processing_record.sql}: unique {@code document_id + operation},
 * required columns, nullable {@code result_reference}, no JSON/BLOB.
 */
@Testcontainers
class OrderProcessingRecordSchemaFlywayTest {

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
    void v7CreatesProcessingTableWithUniqueDocumentIdAndOperation() {
        Integer v7 =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '7' AND success = TRUE
                        """,
                        Integer.class);
        assertEquals(1, v7);

        List<String> columns =
                jdbc.queryForList(
                        """
                        SELECT column_name FROM information_schema.columns
                        WHERE table_schema = 'order_management'
                          AND table_name = 'order_document_processing'
                        ORDER BY ordinal_position
                        """,
                        String.class);
        assertTrue(
                columns.containsAll(
                        List.of(
                                "document_id",
                                "document_type_code",
                                "operation",
                                "processing_status",
                                "payload_revision",
                                "processed_at",
                                "result_reference")));

        Integer jsonOrBlob =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'order_management'
                          AND table_name = 'order_document_processing'
                          AND (
                            data_type IN ('json', 'jsonb', 'bytea')
                            OR udt_name IN ('json', 'jsonb', 'bytea')
                          )
                        """,
                        Integer.class);
        assertEquals(0, jsonOrBlob);

        Boolean resultNullable =
                jdbc.queryForObject(
                        """
                        SELECT is_nullable = 'YES' FROM information_schema.columns
                        WHERE table_schema = 'order_management'
                          AND table_name = 'order_document_processing'
                          AND column_name = 'result_reference'
                        """,
                        Boolean.class);
        assertEquals(Boolean.TRUE, resultNullable);
    }

    @Test
    void duplicateDocumentIdAndPostIsRejected() {
        UUID documentId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-25T16:30:00Z");
        insert(documentId, now, null);
        assertThrows(DuplicateKeyException.class, () -> insert(documentId, now, "ref-2"));
    }

    @Test
    void resultReferenceMayBeNullAndPostOperationIsAccepted() {
        UUID documentId = UUID.randomUUID();
        insert(documentId, Instant.parse("2026-07-25T16:31:00Z"), null);
        Integer count =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM order_management.order_document_processing
                        WHERE document_id = ? AND operation = 'POST' AND result_reference IS NULL
                        """,
                        Integer.class,
                        documentId);
        assertEquals(1, count);
    }

    private static void insert(UUID documentId, Instant processedAt, String resultReference) {
        jdbc.update(
                """
                INSERT INTO order_management.order_document_processing
                  (document_id, document_type_code, operation, processing_status,
                   payload_revision, processed_at, result_reference)
                VALUES (?, 'ORDER_CREATE', 'POST', 'COMPLETED', 0, ?, ?)
                """,
                documentId,
                java.sql.Timestamp.from(processedAt),
                resultReference);
    }
}
