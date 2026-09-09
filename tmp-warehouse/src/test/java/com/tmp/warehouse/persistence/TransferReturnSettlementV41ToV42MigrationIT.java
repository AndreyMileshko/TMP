package com.tmp.warehouse.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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
 * Stage 3.5.8.3: isolated V41 → V42 TRANSFER_RETURN + return settlement migration IT.
 *
 * <p>Does not migrate beyond 42. Uses a dedicated Testcontainers PostgreSQL instance.
 */
@Testcontainers
class TransferReturnSettlementV41ToV42MigrationIT {

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
    void v42AllowsTransferReturnAndCreatesReturnTableWithoutStockChange() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("41")
                .load()
                .migrate();

        assertFalse(returnTableExists());

        Instant now = Instant.parse("2026-09-09T09:00:00Z");
        UUID warehouseSrc = UUID.randomUUID();
        UUID warehouseDst = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.warehouses (
                    id, code, name, active, version, created_at, updated_at)
                VALUES (?, 'RET-SRC', 'Return Source', TRUE, 0, ?, ?),
                       (?, 'RET-DST', 'Return Destination', TRUE, 0, ?, ?)
                """,
                warehouseSrc,
                Timestamp.from(now),
                Timestamp.from(now),
                warehouseDst,
                Timestamp.from(now),
                Timestamp.from(now));

        UUID cellId = UUID.randomUUID();
        UUID destCellId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.storage_cells (
                    id, warehouse_id, code, active, version, created_at, updated_at)
                VALUES (?, ?, 'R1', TRUE, 0, ?, ?),
                       (?, ?, 'D1', TRUE, 0, ?, ?)
                """,
                cellId,
                warehouseSrc,
                Timestamp.from(now),
                Timestamp.from(now),
                destCellId,
                warehouseDst,
                Timestamp.from(now),
                Timestamp.from(now));

        UUID materialId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.material_references (
                    id, article, name, color, size, unit_of_measure, created_at, updated_at)
                VALUES (?, 'RET-MAT', 'RET-MAT', '', '', '', ?, ?)
                """,
                materialId,
                Timestamp.from(now),
                Timestamp.from(now));

        UUID stockId = UUID.randomUUID();
        BigDecimal seedQty = new BigDecimal("17.000000");
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

        UUID sendOp = UUID.randomUUID();
        UUID receiveOp = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.warehouse_operations (
                    id, operation_type, status, warehouse_id, storage_cell_id, material_reference_id,
                    quantity, stock_state, version, created_at, updated_at)
                VALUES (?, 'TRANSFER_SEND', 'COMPLETED', ?, ?, ?, 5, 'IN_TRANSIT', 0, ?, ?),
                       (?, 'TRANSFER_RECEIVE', 'COMPLETED', ?, ?, ?, 5, 'AVAILABLE', 0, ?, ?)
                """,
                sendOp,
                warehouseSrc,
                cellId,
                materialId,
                Timestamp.from(now),
                Timestamp.from(now),
                receiveOp,
                warehouseDst,
                destCellId,
                materialId,
                Timestamp.from(now),
                Timestamp.from(now));

        jdbc.update(
                """
                INSERT INTO warehouse.warehouse_movements
                    (id, stock_position_id, operation_type, quantity_delta, created_at)
                VALUES (?, ?, 'TRANSFER_SEND', -5, ?),
                       (?, ?, 'TRANSFER_RECEIVE', 5, ?)
                """,
                UUID.randomUUID(),
                stockId,
                Timestamp.from(now),
                UUID.randomUUID(),
                stockId,
                Timestamp.from(now));

        UUID returnOpForbidden = UUID.randomUUID();
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                """
                                INSERT INTO warehouse.warehouse_operations (
                                    id, operation_type, status, warehouse_id, storage_cell_id,
                                    material_reference_id, quantity, stock_state,
                                    version, created_at, updated_at)
                                VALUES (?, 'TRANSFER_RETURN', 'COMPLETED', ?, ?, ?, 1, 'AVAILABLE',
                                        0, ?, ?)
                                """,
                                returnOpForbidden,
                                warehouseSrc,
                                cellId,
                                materialId,
                                Timestamp.from(now),
                                Timestamp.from(now)));

        UUID postedDoc = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO documents.documents (
                    id, document_type_id, document_number, title, status, version,
                    created_at, updated_at, posted_at, closed_at)
                VALUES (?, 'warehouse.transfer', 'RET-POSTED-001', 'Posted Transfer', 'POSTED', 1,
                        ?, ?, ?, NULL)
                """,
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
                VALUES (?, ?, ?, 1, 0, NULL, NULL, ?, ?)
                """,
                postedDoc,
                warehouseSrc,
                warehouseDst,
                Timestamp.from(now),
                Timestamp.from(now));

        UUID lineId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.transfer_document_lines (
                    id, document_id, material_reference_id, quantity, line_order)
                VALUES (?, ?, ?, 5, 1)
                """,
                lineId,
                postedDoc,
                materialId);

        UUID sendAllocationId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.transfer_document_send_allocation (
                    id, document_id, line_id, source_storage_cell_id, quantity,
                    send_operation_id, created_at)
                VALUES (?, ?, ?, ?, 5, ?, ?)
                """,
                sendAllocationId,
                postedDoc,
                lineId,
                cellId,
                sendOp,
                Timestamp.from(now));

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("42")
                .load()
                .migrate();

        assertTrue(returnTableExists());

        UUID returnOp = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.warehouse_operations (
                    id, operation_type, status, warehouse_id, storage_cell_id, material_reference_id,
                    quantity, stock_state, version, created_at, updated_at)
                VALUES (?, 'TRANSFER_RETURN', 'COMPLETED', ?, ?, ?, 2, 'AVAILABLE', 0, ?, ?)
                """,
                returnOp,
                warehouseSrc,
                cellId,
                materialId,
                Timestamp.from(now),
                Timestamp.from(now));

        jdbc.update(
                """
                INSERT INTO warehouse.warehouse_operations (
                    id, operation_type, status, warehouse_id, storage_cell_id, material_reference_id,
                    quantity, stock_state, version, created_at, updated_at)
                VALUES (?, 'RECEIPT', 'COMPLETED', ?, ?, ?, 1, 'AVAILABLE', 0, ?, ?),
                       (?, 'TRANSFER_SEND', 'COMPLETED', ?, ?, ?, 1, 'IN_TRANSIT', 0, ?, ?)
                """,
                UUID.randomUUID(),
                warehouseSrc,
                cellId,
                materialId,
                Timestamp.from(now),
                Timestamp.from(now),
                UUID.randomUUID(),
                warehouseSrc,
                cellId,
                materialId,
                Timestamp.from(now),
                Timestamp.from(now));

        UUID returnItemId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.transfer_return_settlement_item (
                    id, document_id, send_allocation_id, return_storage_cell_id,
                    quantity, return_operation_id, created_at)
                VALUES (?, ?, ?, ?, 2, ?, ?)
                """,
                returnItemId,
                postedDoc,
                sendAllocationId,
                cellId,
                returnOp,
                Timestamp.from(now));

        UUID zeroQtyReturnOp = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.warehouse_operations (
                    id, operation_type, status, warehouse_id, storage_cell_id, material_reference_id,
                    quantity, stock_state, version, created_at, updated_at)
                VALUES (?, 'TRANSFER_RETURN', 'COMPLETED', ?, ?, ?, 1, 'AVAILABLE', 0, ?, ?)
                """,
                zeroQtyReturnOp,
                warehouseSrc,
                cellId,
                materialId,
                Timestamp.from(now),
                Timestamp.from(now));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                """
                                INSERT INTO warehouse.transfer_return_settlement_item (
                                    id, document_id, send_allocation_id, return_storage_cell_id,
                                    quantity, return_operation_id, created_at)
                                VALUES (?, ?, ?, ?, 0, ?, ?)
                                """,
                                UUID.randomUUID(),
                                postedDoc,
                                sendAllocationId,
                                cellId,
                                zeroQtyReturnOp,
                                Timestamp.from(now)));

        UUID otherDoc = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO documents.documents (
                    id, document_type_id, document_number, title, status, version,
                    created_at, updated_at, posted_at, closed_at)
                VALUES (?, 'warehouse.transfer', 'RET-OTHER-001', 'Other', 'POSTED', 1,
                        ?, ?, ?, NULL)
                """,
                otherDoc,
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
                VALUES (?, ?, ?, 1, 0, NULL, NULL, ?, ?)
                """,
                otherDoc,
                warehouseSrc,
                warehouseDst,
                Timestamp.from(now),
                Timestamp.from(now));

        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                """
                                INSERT INTO warehouse.transfer_return_settlement_item (
                                    id, document_id, send_allocation_id, return_storage_cell_id,
                                    quantity, return_operation_id, created_at)
                                VALUES (?, ?, ?, ?, 1, ?, ?)
                                """,
                                UUID.randomUUID(),
                                otherDoc,
                                sendAllocationId,
                                cellId,
                                UUID.randomUUID(),
                                Timestamp.from(now)));

        UUID secondReturnOp = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.warehouse_operations (
                    id, operation_type, status, warehouse_id, storage_cell_id, material_reference_id,
                    quantity, stock_state, version, created_at, updated_at)
                VALUES (?, 'TRANSFER_RETURN', 'COMPLETED', ?, ?, ?, 1, 'AVAILABLE', 0, ?, ?)
                """,
                secondReturnOp,
                warehouseSrc,
                cellId,
                materialId,
                Timestamp.from(now),
                Timestamp.from(now));

        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                """
                                INSERT INTO warehouse.transfer_return_settlement_item (
                                    id, document_id, send_allocation_id, return_storage_cell_id,
                                    quantity, return_operation_id, created_at)
                                VALUES (?, ?, ?, ?, 1, ?, ?)
                                """,
                                UUID.randomUUID(),
                                postedDoc,
                                sendAllocationId,
                                cellId,
                                returnOp,
                                Timestamp.from(now)));

        BigDecimal qtyAfter =
                jdbc.queryForObject(
                        """
                        SELECT quantity FROM warehouse.stock_positions WHERE id = ?
                        """,
                        BigDecimal.class,
                        stockId);
        assertEquals(0, seedQty.compareTo(qtyAfter));
    }

    private boolean returnTableExists() {
        Integer count =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.tables
                         WHERE table_schema = 'warehouse'
                           AND table_name = 'transfer_return_settlement_item'
                        """,
                        Integer.class);
        return count != null && count > 0;
    }
}
