package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.TransferOperationContext;
import com.tmp.warehouse.domain.WarehouseOperationId;
import java.util.Optional;

public interface TransferOperationContextRepository {

    void save(TransferOperationContext context);

    Optional<TransferOperationContext> findByOperationId(WarehouseOperationId operationId);
}
