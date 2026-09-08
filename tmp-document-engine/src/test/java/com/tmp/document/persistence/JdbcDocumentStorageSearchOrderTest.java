package com.tmp.document.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentQuery;
import com.tmp.document.api.DocumentStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
 * Stage 3.5.6 corrective: Document Engine search ordering is deterministic for equal created_at.
 */
@Testcontainers
class JdbcDocumentStorageSearchOrderTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static JdbcDocumentStorageAdapter storage;

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
        jdbc = new JdbcTemplate(ds);
        storage = new JdbcDocumentStorageAdapter(jdbc);
        jdbc.update(
                """
                INSERT INTO documents.document_types (id, display_name, description, registered_at, version)
                VALUES ('test.search.order', 'test.search.order', 'search order', CURRENT_TIMESTAMP, 0)
                ON CONFLICT (id) DO NOTHING
                """);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM documents.documents WHERE document_type_id = 'test.search.order'");
    }

    @Test
    void equalCreatedAtOrderedByIdAscendingWithinNewestFirst() {
        Instant same = Instant.parse("2026-09-08T12:00:00Z");
        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID id3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
        insert(id2, "N2", same);
        insert(id3, "N3", same);
        insert(id1, "N1", same);

        List<DocumentMetadata> page =
                storage.search(
                        new DocumentQuery(
                                Optional.of("test.search.order"),
                                Optional.of(DocumentStatus.DRAFT),
                                Optional.empty(),
                                10,
                                0));
        assertEquals(3, page.size());
        assertEquals(id1, page.get(0).id());
        assertEquals(id2, page.get(1).id());
        assertEquals(id3, page.get(2).id());

        List<DocumentMetadata> first =
                storage.search(
                        new DocumentQuery(
                                Optional.of("test.search.order"),
                                Optional.of(DocumentStatus.DRAFT),
                                Optional.empty(),
                                2,
                                0));
        List<DocumentMetadata> second =
                storage.search(
                        new DocumentQuery(
                                Optional.of("test.search.order"),
                                Optional.of(DocumentStatus.DRAFT),
                                Optional.empty(),
                                2,
                                2));
        assertEquals(2, first.size());
        assertEquals(1, second.size());
        assertEquals(id1, first.get(0).id());
        assertEquals(id2, first.get(1).id());
        assertEquals(id3, second.get(0).id());
        assertTrue(
                first.stream().map(DocumentMetadata::id).noneMatch(id -> id.equals(second.get(0).id())));
    }

    private void insert(UUID id, String number, Instant createdAt) {
        jdbc.update(
                """
                INSERT INTO documents.documents (
                    id, document_type_id, document_number, title, status, version,
                    created_at, updated_at, posted_at, closed_at)
                VALUES (?, 'test.search.order', ?, 't', 'DRAFT', 0, ?, ?, NULL, NULL)
                """,
                id,
                number,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
    }
}
