package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.StockPositionId;
import com.tmp.warehouse.domain.WarehouseMovement;
import java.util.List;

/**
 * Domain port for append-only {@link WarehouseMovement} persistence (Specification §9).
 *
 * <p>Supports creation and history read only. Update and delete are intentionally absent.
 */
public interface WarehouseMovementRepository {

    /**
     * Appends an immutable movement record.
     *
     * @return persisted movement
     */
    WarehouseMovement append(WarehouseMovement movement);

    /**
     * Returns movement history for a stock position ordered by {@code created_at}, then id.
     */
    List<WarehouseMovement> findHistoryByStockPosition(StockPositionId stockPositionId);
}
