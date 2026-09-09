package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.TransferReturnSettlementItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Warehouse-internal persistence for Transfer Document return settlement items (Stage 3.5.8.3).
 */
public interface TransferReturnSettlementItemRepository {

    void insertAll(UUID documentId, List<TransferReturnSettlementItem> items);

    List<TransferReturnSettlementItem> findByDocumentId(UUID documentId);

    List<TransferReturnSettlementItem> findBySendAllocationIds(Collection<UUID> sendAllocationIds);
}
