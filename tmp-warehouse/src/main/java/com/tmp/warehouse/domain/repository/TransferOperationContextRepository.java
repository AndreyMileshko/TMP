package com.tmp.warehouse.domain.repository;

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
     * Atomically records the receive operation when the send has not yet been received.
     *
     * @return {@code true} if this caller claimed the receive; {@code false} if already received
     */
    boolean claimReceiveIfAbsent(
            WarehouseOperationId sendOperationId, WarehouseOperationId receiveOperationId);
}
