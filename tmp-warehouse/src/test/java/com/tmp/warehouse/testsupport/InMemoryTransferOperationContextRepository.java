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
    public void save(TransferOperationContext context) {
        store.put(context.operationId(), context);
    }

    @Override
    public Optional<TransferOperationContext> findByOperationId(WarehouseOperationId operationId) {
        return Optional.ofNullable(store.get(operationId));
    }
}
