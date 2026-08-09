package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockPositionId;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import java.util.List;
import java.util.Optional;

/**
 * Domain port for Stock Position persistence (Specification §6–8).
 *
 * <p>Supports create, read, quantity update and state update only. Warehouse Operation execution,
 * movements, reservation and business flows are out of scope for this port.
 */
public interface StockPositionRepository {

    /**
     * Inserts a new stock position.
     *
     * @return persisted position (version {@code 0})
     */
    StockPosition create(StockPosition position);

    Optional<StockPosition> findById(StockPositionId id);

    Optional<StockPosition> findByNaturalKey(
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            MaterialReference material,
            StockState stockState);

    /**
     * Returns all stock positions for the given material reference (read-only query).
     */
    List<StockPosition> findByMaterial(MaterialReference material);

    /**
     * Updates quantity for an existing position. Rejects negative quantities via
     * {@link StockQuantity}. Uses optimistic locking on {@code expectedVersion}.
     */
    StockPosition updateQuantity(StockPositionId id, StockQuantity quantity, long expectedVersion);

    /**
     * Updates stock state for an existing position. Allowed states are those of {@link StockState}
     * ({@code AVAILABLE}, {@code IN_TRANSIT}, {@code BLOCKED} — never {@code RESERVED}).
     */
    StockPosition updateState(StockPositionId id, StockState stockState, long expectedVersion);

    /**
     * Updates quantity and stock state in a single optimistic-lock version bump. Used by the
     * Warehouse Operation Engine write path.
     */
    StockPosition updateQuantityAndState(
            StockPositionId id,
            StockQuantity quantity,
            StockState stockState,
            long expectedVersion);
}
