package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.TransferDocumentSendAllocation;
import com.tmp.warehouse.domain.WarehouseOperationId;
import java.util.List;
import java.util.UUID;

/**
 * Warehouse-internal persistence for Transfer Document send execution links (Stage 3.5.6). Not
 * exposed publicly.
 */
public interface TransferDocumentSendAllocationRepository {

    void insertAll(UUID documentId, List<TransferDocumentSendAllocation> allocations);

    List<TransferDocumentSendAllocation> findByDocumentId(UUID documentId);

    /**
     * Sets {@code send_operation_id} once for the allocation. Fails if already linked or missing.
     */
    void attachSendOperation(UUID allocationId, WarehouseOperationId sendOperationId);
}
