package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.TransferDocumentSendAllocation;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.WarehouseTransferLineId;
import com.tmp.warehouse.domain.repository.TransferDocumentSendAllocationRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** JDBC adapter for {@code warehouse.transfer_document_send_allocation}. */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate.")
public final class JdbcTransferDocumentSendAllocationRepository
        implements TransferDocumentSendAllocationRepository {

    private final JdbcTemplate jdbc;

    public JdbcTransferDocumentSendAllocationRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void insertAll(UUID documentId, List<TransferDocumentSendAllocation> allocations) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(allocations, "allocations");
        for (TransferDocumentSendAllocation allocation : allocations) {
            Objects.requireNonNull(allocation, "allocation");
            if (!documentId.equals(allocation.documentId())) {
                throw new IllegalArgumentException(
                        "Allocation documentId mismatch: expected="
                                + documentId
                                + ", actual="
                                + allocation.documentId());
            }
            jdbc.update(
                    """
                    INSERT INTO warehouse.transfer_document_send_allocation (
                        id, document_id, line_id, source_storage_cell_id,
                        quantity, send_operation_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    allocation.id(),
                    allocation.documentId(),
                    allocation.lineId().value(),
                    allocation.sourceStorageCellId().value(),
                    allocation.quantity().value(),
                    allocation.sendOperationIdOptional().map(WarehouseOperationId::value).orElse(null),
                    Timestamp.from(allocation.createdAt()));
        }
    }

    @Override
    public List<TransferDocumentSendAllocation> findByDocumentId(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        return List.copyOf(
                jdbc.query(
                        """
                        SELECT id, document_id, line_id, source_storage_cell_id,
                               quantity, send_operation_id, created_at
                          FROM warehouse.transfer_document_send_allocation
                         WHERE document_id = ?
                         ORDER BY created_at, id
                        """,
                        (rs, rowNum) -> {
                            UUID sendOp = (UUID) rs.getObject("send_operation_id");
                            return TransferDocumentSendAllocation.of(
                                    (UUID) rs.getObject("id"),
                                    (UUID) rs.getObject("document_id"),
                                    WarehouseTransferLineId.of((UUID) rs.getObject("line_id")),
                                    StorageCellId.of(
                                            (UUID) rs.getObject("source_storage_cell_id")),
                                    StockQuantity.of(rs.getBigDecimal("quantity")),
                                    sendOp == null ? null : WarehouseOperationId.of(sendOp),
                                    rs.getTimestamp("created_at").toInstant());
                        },
                        documentId));
    }

    @Override
    public void attachSendOperation(UUID allocationId, WarehouseOperationId sendOperationId) {
        Objects.requireNonNull(allocationId, "allocationId");
        Objects.requireNonNull(sendOperationId, "sendOperationId");
        int updated =
                jdbc.update(
                        """
                        UPDATE warehouse.transfer_document_send_allocation
                           SET send_operation_id = ?
                         WHERE id = ?
                           AND send_operation_id IS NULL
                        """,
                        sendOperationId.value(),
                        allocationId);
        if (updated != 1) {
            throw new InvalidWarehouseStateException(
                    "Cannot attach send operation to allocation: allocationId="
                            + allocationId
                            + ", sendOperationId="
                            + sendOperationId);
        }
    }
}
