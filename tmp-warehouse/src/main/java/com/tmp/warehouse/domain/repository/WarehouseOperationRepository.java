package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.WarehouseOperationStatus;
import com.tmp.warehouse.domain.WarehouseOperationType;
import java.util.List;
import java.util.Optional;

/**
 * Domain port for {@link WarehouseOperation} persistence (Specification §10).
 *
 * <p>Supports create, read and status/result update. Does not expose bypass stock mutation.
 */
public interface WarehouseOperationRepository {

    /**
     * Inserts a new operation. Expected status is {@code DRAFT}.
     *
     * @return persisted operation (version {@code 0})
     */
    WarehouseOperation create(WarehouseOperation operation);

    Optional<WarehouseOperation> findById(WarehouseOperationId id);

    /** Lists operations by type and lifecycle status (read-only catalogue query). */
    List<WarehouseOperation> findByTypeAndStatus(
            WarehouseOperationType type, WarehouseOperationStatus status);

    /**
     * Persists lifecycle result (COMPLETED / FAILED) with optimistic locking on version.
     */
    WarehouseOperation update(WarehouseOperation operation);
}
