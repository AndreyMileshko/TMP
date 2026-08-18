package com.tmp.warehouse.testsupport;

import com.tmp.warehouse.domain.TransferOperationContext;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.repository.TransferOperationContextRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryTransferOperationContextRepository
        implements TransferOperationContextRepository {

    private final Map<WarehouseOperationId, TransferOperationContext> store = new HashMap<>();

    @Override
    public synchronized void save(TransferOperationContext context) {
        store.put(context.operationId(), context);
    }

    @Override
    public synchronized Optional<TransferOperationContext> findByOperationId(
            WarehouseOperationId operationId) {
        return Optional.ofNullable(store.get(operationId));
    }

    @Override
    public synchronized Optional<TransferOperationContext> findByReceiveOperationId(
            WarehouseOperationId receiveOperationId) {
        return store.values().stream()
                .filter(context -> receiveOperationId.equals(context.receiveOperationId()))
                .findFirst();
    }

    @Override
    public synchronized Optional<TransferOperationContext> lockByOperationId(
            WarehouseOperationId operationId) {
        return findByOperationId(operationId);
    }

    @Override
    public synchronized boolean claimReceiveIfAbsent(
            WarehouseOperationId sendOperationId, WarehouseOperationId receiveOperationId) {
        TransferOperationContext current = store.get(sendOperationId);
        if (current == null || current.isReceived()) {
            return false;
        }
        store.put(sendOperationId, current.withReceiveOperationId(receiveOperationId));
        return true;
    }
}
