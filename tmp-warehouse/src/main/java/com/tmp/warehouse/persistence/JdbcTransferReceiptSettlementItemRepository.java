package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.TransferReceiptSettlementItem;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.repository.TransferReceiptSettlementItemRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** JDBC adapter for {@code warehouse.transfer_receipt_settlement_item}. */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate.")
public final class JdbcTransferReceiptSettlementItemRepository
        implements TransferReceiptSettlementItemRepository {

    private final JdbcTemplate jdbc;

    public JdbcTransferReceiptSettlementItemRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void insertAll(UUID documentId, List<TransferReceiptSettlementItem> items) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(items, "items");
        for (TransferReceiptSettlementItem item : items) {
            Objects.requireNonNull(item, "item");
            if (!documentId.equals(item.documentId())) {
                throw new IllegalArgumentException(
                        "Receipt item documentId mismatch: expected="
                                + documentId
                                + ", actual="
                                + item.documentId());
            }
            jdbc.update(
                    """
                    INSERT INTO warehouse.transfer_receipt_settlement_item (
                        id, document_id, send_allocation_id, destination_storage_cell_id,
                        quantity, receive_operation_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    item.id(),
                    item.documentId(),
                    item.sendAllocationId(),
                    item.destinationStorageCellId().value(),
                    item.quantity().value(),
                    item.receiveOperationId().value(),
                    Timestamp.from(item.createdAt()));
        }
    }

    @Override
    public List<TransferReceiptSettlementItem> findByDocumentId(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        return List.copyOf(
                jdbc.query(
                        """
                        SELECT id, document_id, send_allocation_id, destination_storage_cell_id,
                               quantity, receive_operation_id, created_at
                          FROM warehouse.transfer_receipt_settlement_item
                         WHERE document_id = ?
                         ORDER BY created_at, id
                        """,
                        (rs, rowNum) ->
                                TransferReceiptSettlementItem.of(
                                        (UUID) rs.getObject("id"),
                                        (UUID) rs.getObject("document_id"),
                                        (UUID) rs.getObject("send_allocation_id"),
                                        StorageCellId.of(
                                                (UUID) rs.getObject("destination_storage_cell_id")),
                                        StockQuantity.of(rs.getBigDecimal("quantity")),
                                        WarehouseOperationId.of(
                                                (UUID) rs.getObject("receive_operation_id")),
                                        rs.getTimestamp("created_at").toInstant()),
                        documentId));
    }

    @Override
    public List<TransferReceiptSettlementItem> findBySendAllocationIds(
            Collection<UUID> sendAllocationIds) {
        Objects.requireNonNull(sendAllocationIds, "sendAllocationIds");
        if (sendAllocationIds.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>(sendAllocationIds);
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        return List.copyOf(
                jdbc.query(
                        ("SELECT id, document_id, send_allocation_id, destination_storage_cell_id, "
                                        + "quantity, receive_operation_id, created_at "
                                        + "FROM warehouse.transfer_receipt_settlement_item "
                                        + "WHERE send_allocation_id IN (%s) "
                                        + "ORDER BY created_at, id")
                                .formatted(placeholders),
                        (rs, rowNum) ->
                                TransferReceiptSettlementItem.of(
                                        (UUID) rs.getObject("id"),
                                        (UUID) rs.getObject("document_id"),
                                        (UUID) rs.getObject("send_allocation_id"),
                                        StorageCellId.of(
                                                (UUID) rs.getObject("destination_storage_cell_id")),
                                        StockQuantity.of(rs.getBigDecimal("quantity")),
                                        WarehouseOperationId.of(
                                                (UUID) rs.getObject("receive_operation_id")),
                                        rs.getTimestamp("created_at").toInstant()),
                        ids.toArray()));
    }
}
