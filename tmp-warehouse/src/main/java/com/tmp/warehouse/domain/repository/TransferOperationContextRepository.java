package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.TransferOperationContext;
import com.tmp.warehouse.domain.WarehouseOperationId;
import java.util.Optional;

public interface TransferOperationContextRepository {

    void save(TransferOperationContext context);

    Optional<TransferOperationContext> findByOperationId(WarehouseOperationId operationId);

    Optional<TransferOperationContext> findByReceiveOperationId(WarehouseOperationId receiveOperationId);

    /**
     * Loads the context row with a transaction lock so concurrent receive of the same send is
     * serialized.
     */
    Optional<TransferOperationContext> lockByOperationId(WarehouseOperationId operationId);

    /**
     * Atomically records the receive operation and the actual destination cell when the send has
     * not yet been received.
     *
     * <p>For deferred contexts ({@code destination_storage_cell_id IS NULL}) the supplied cell is
     * stored. For legacy contexts the stored cell must equal {@code actualDestinationCellId}.
     *
     * @return {@code true} if this caller claimed the receive; {@code false} if already received or
     *     the destination cell conflicts with a preselected legacy cell
     */
    boolean claimReceiveIfAbsent(
            WarehouseOperationId sendOperationId,
            WarehouseOperationId receiveOperationId,
            StorageCellId actualDestinationCellId);
}
