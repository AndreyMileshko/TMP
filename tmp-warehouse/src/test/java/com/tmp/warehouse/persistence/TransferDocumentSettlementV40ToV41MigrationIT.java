package com.tmp.warehouse.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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

/**
 * Stage 3.5.8.2: isolated V40 → V41 settlement backfill migration IT.
 *
 * <p>Does not create V42. Uses a dedicated Testcontainers PostgreSQL instance.
 */
@Testcontainers
class TransferDocumentSettlementV40ToV41MigrationIT {

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
    void v41BackfillsPostedOnlyAndLeavesStockUnchanged() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("40")
                .load()
                .migrate();

        assertFalse(settlementTableExists());

        Instant now = Instant.parse("2026-09-09T08:00:00Z");
        UUID warehouseSrc = UUID.randomUUID();
        UUID warehouseDst = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.warehouses (
                    id, code, name, active, version, created_at, updated_at)
                VALUES (?, 'MIG-SRC', 'Migration Source', TRUE, 0, ?, ?),
                       (?, 'MIG-DST', 'Migration Destination', TRUE, 0, ?, ?)
                """,
                warehouseSrc,
                Timestamp.from(now),
                Timestamp.from(now),
                warehouseDst,
                Timestamp.from(now),
                Timestamp.from(now));

        UUID cellId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.storage_cells (
                    id, warehouse_id, code, active, version, created_at, updated_at)
                VALUES (?, ?, 'C1', TRUE, 0, ?, ?)
                """,
                cellId,
                warehouseSrc,
                Timestamp.from(now),
                Timestamp.from(now));

        UUID materialId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.material_references (
                    id, article, name, color, size, unit_of_measure, created_at, updated_at)
                VALUES (?, 'MIG-MAT', 'MIG-MAT', '', '', '', ?, ?)
                """,
                materialId,
                Timestamp.from(now),
                Timestamp.from(now));

        UUID stockId = UUID.randomUUID();
        BigDecimal seedQty = new BigDecimal("42.000000");
        jdbc.update(
                """
                INSERT INTO warehouse.stock_positions (
                    id, warehouse_id, storage_cell_id, material_reference_id,
                    quantity, stock_state, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'AVAILABLE', 0, ?, ?)
                """,
                stockId,
                warehouseSrc,
                cellId,
                materialId,
                seedQty,
                Timestamp.from(now),
                Timestamp.from(now));

        UUID draftDoc = UUID.randomUUID();
        UUID postedDoc = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO documents.documents (
                    id, document_type_id, document_number, title, status, version,
                    created_at, updated_at, posted_at, closed_at)
                VALUES (?, 'warehouse.transfer', 'MIG-DRAFT-001', 'Draft Transfer', 'DRAFT', 0,
                        ?, ?, NULL, NULL),
                       (?, 'warehouse.transfer', 'MIG-POSTED-001', 'Posted Transfer', 'POSTED', 1,
                        ?, ?, ?, NULL)
                """,
                draftDoc,
                Timestamp.from(now),
                Timestamp.from(now),
                postedDoc,
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now));

        jdbc.update(
                """
                INSERT INTO warehouse.transfer_document_payload (
                    document_id, source_warehouse_id, destination_warehouse_id,
                    payload_schema_version, payload_revision,
                    continuation_of_document_id, continuation_reason,
                    created_at, updated_at)
                VALUES (?, ?, ?, 1, 0, NULL, NULL, ?, ?),
                       (?, ?, ?, 1, 0, NULL, NULL, ?, ?)
                """,
                draftDoc,
                warehouseSrc,
                warehouseDst,
                Timestamp.from(now),
                Timestamp.from(now),
                postedDoc,
                warehouseSrc,
                warehouseDst,
                Timestamp.from(now),
                Timestamp.from(now));

        assertFalse(settlementTableExists());

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("41")
                .load()
                .migrate();

        assertTrue(settlementTableExists());

        List<Map<String, Object>> postedSettlements =
                jdbc.queryForList(
                        """
                        SELECT settlement_state, operational_revision, decision
                          FROM warehouse.transfer_document_settlement
                         WHERE document_id = ?
                        """,
                        postedDoc);
        assertEquals(1, postedSettlements.size());
        assertEquals("AWAITING_RECEIPT", postedSettlements.get(0).get("settlement_state"));
        assertEquals(0L, ((Number) postedSettlements.get(0).get("operational_revision")).longValue());
        assertEquals(null, postedSettlements.get(0).get("decision"));

        Integer draftCount =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM warehouse.transfer_document_settlement
                         WHERE document_id = ?
                        """,
                        Integer.class,
                        draftDoc);
        assertEquals(0, draftCount.intValue());

        BigDecimal qtyAfter =
                jdbc.queryForObject(
                        """
                        SELECT quantity FROM warehouse.stock_positions WHERE id = ?
                        """,
                        BigDecimal.class,
                        stockId);
        assertEquals(0, seedQty.compareTo(qtyAfter));
    }

    private boolean settlementTableExists() {
        Integer count =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.tables
                         WHERE table_schema = 'warehouse'
                           AND table_name = 'transfer_document_settlement'
                        """,
                        Integer.class);
        return count != null && count > 0;
    }
}
