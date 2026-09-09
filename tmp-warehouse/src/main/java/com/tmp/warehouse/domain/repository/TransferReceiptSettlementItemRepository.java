package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.TransferReceiptSettlementItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Warehouse-internal persistence for Transfer Document receipt settlement items (Stage 3.5.8.1).
 */
public interface TransferReceiptSettlementItemRepository {

    void insertAll(UUID documentId, List<TransferReceiptSettlementItem> items);

    List<TransferReceiptSettlementItem> findByDocumentId(UUID documentId);

    List<TransferReceiptSettlementItem> findBySendAllocationIds(Collection<UUID> sendAllocationIds);
}
