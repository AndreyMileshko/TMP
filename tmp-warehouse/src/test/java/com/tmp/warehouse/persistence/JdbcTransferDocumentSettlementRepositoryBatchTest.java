package com.tmp.warehouse.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.domain.TransferDocumentSettlement;
import com.tmp.warehouse.domain.TransferSettlementState;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Stage 3.5.8.2: {@link JdbcTransferDocumentSettlementRepository#findByDocumentIds} batch IN
 * query behaviour.
 */
@Testcontainers
class JdbcTransferDocumentSettlementRepositoryBatchTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-09T07:00:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

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
    void wipe() {
        jdbc.update("DELETE FROM warehouse.transfer_receipt_settlement_item");
        jdbc.update("DELETE FROM warehouse.transfer_document_settlement");
        jdbc.update("DELETE FROM warehouse.transfer_document_send_allocation");
        jdbc.update("DELETE FROM warehouse.transfer_document_lines");
        jdbc.update("DELETE FROM warehouse.transfer_document_payload");
        jdbc.update("DELETE FROM warehouse.storage_cells");
        jdbc.update("DELETE FROM warehouse.warehouses");
    }

    @Test
    void emptyFindByDocumentIdsReturnsEmpty() {
        JdbcTransferDocumentSettlementRepository repo =
                new JdbcTransferDocumentSettlementRepository(jdbc);
        Map<UUID, TransferDocumentSettlement> result = repo.findByDocumentIds(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void findByDocumentIdsReturnsOnlyExisting() {
        UUID sourceId = insertWarehouse("SRC-BATCH");
        UUID destId = insertWarehouse("DST-BATCH");
        UUID existing1 = insertPayload(sourceId, destId);
        UUID existing2 = insertPayload(sourceId, destId);
        Instant now = CLOCK.instant();
        JdbcTransferDocumentSettlementRepository repo =
                new JdbcTransferDocumentSettlementRepository(jdbc);
        repo.insertAwaitingReceipt(TransferDocumentSettlement.awaitingReceipt(existing1, now));
        repo.insertAwaitingReceipt(TransferDocumentSettlement.awaitingReceipt(existing2, now));

        UUID missing = UUID.randomUUID();
        Map<UUID, TransferDocumentSettlement> found =
                repo.findByDocumentIds(List.of(existing1, missing, existing2));

        assertEquals(2, found.size());
        assertTrue(found.containsKey(existing1));
        assertTrue(found.containsKey(existing2));
        assertEquals(
                TransferSettlementState.AWAITING_RECEIPT, found.get(existing1).settlementState());
    }

    @Test
    void findByDocumentIdsUsesBoundedSelectCountNotPerId() {
        UUID sourceId = insertWarehouse("SRC-COUNT");
        UUID destId = insertWarehouse("DST-COUNT");
        Instant now = CLOCK.instant();
        List<UUID> ids = new ArrayList<>();
        JdbcTransferDocumentSettlementRepository insertRepo =
                new JdbcTransferDocumentSettlementRepository(jdbc);
        for (int i = 0; i < 20; i++) {
            UUID documentId = insertPayload(sourceId, destId);
            insertRepo.insertAwaitingReceipt(
                    TransferDocumentSettlement.awaitingReceipt(documentId, now));
            ids.add(documentId);
        }

        CountingJdbcTemplate counting = new CountingJdbcTemplate(dataSource);
        JdbcTransferDocumentSettlementRepository repo =
                new JdbcTransferDocumentSettlementRepository(counting);

        Map<UUID, TransferDocumentSettlement> found = repo.findByDocumentIds(ids);
        assertEquals(20, found.size());
        assertEquals(1, counting.settlementSelectCount.get());
    }

    private UUID insertWarehouse(String code) {
        UUID id = UUID.randomUUID();
        Instant now = CLOCK.instant();
        jdbc.update(
                """
                INSERT INTO warehouse.warehouses (
                    id, code, name, active, version, created_at, updated_at)
                VALUES (?, ?, ?, TRUE, 0, ?, ?)
                """,
                id,
                code,
                code,
                Timestamp.from(now),
                Timestamp.from(now));
        return id;
    }

    private UUID insertPayload(UUID sourceId, UUID destId) {
        UUID documentId = UUID.randomUUID();
        Instant now = CLOCK.instant();
        jdbc.update(
                """
                INSERT INTO warehouse.transfer_document_payload (
                    document_id, source_warehouse_id, destination_warehouse_id,
                    payload_schema_version, payload_revision, created_at, updated_at)
                VALUES (?, ?, ?, 1, 0, ?, ?)
                """,
                documentId,
                sourceId,
                destId,
                Timestamp.from(now),
                Timestamp.from(now));
        return documentId;
    }

    private static final class CountingJdbcTemplate extends JdbcTemplate {
        private final AtomicInteger settlementSelectCount = new AtomicInteger();

        private CountingJdbcTemplate(DataSource dataSource) {
            super(dataSource);
        }

        private void maybeCount(String sql) {
            if (sql != null
                    && sql.toLowerCase().contains("transfer_document_settlement")
                    && sql.trim().toLowerCase().startsWith("select")) {
                settlementSelectCount.incrementAndGet();
            }
        }

        @Override
        public void query(String sql, RowCallbackHandler rch, Object... args) {
            maybeCount(sql);
            super.query(sql, rch, args);
        }

        @Override
        public <T> T query(
                String sql,
                org.springframework.jdbc.core.ResultSetExtractor<T> rse,
                Object... args) {
            maybeCount(sql);
            return super.query(sql, rse, args);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            maybeCount(sql);
            return super.query(sql, rowMapper, args);
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            maybeCount(sql);
            return super.queryForObject(sql, rowMapper, args);
        }
    }
}
