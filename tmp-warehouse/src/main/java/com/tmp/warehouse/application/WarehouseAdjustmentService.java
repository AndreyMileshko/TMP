package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.WarehouseOperationType;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Adjustment operation — corrects stock via {@link WarehouseOperationEngine} (Specification §11).
 *
 * <p>Flow: Adjustment Request → Warehouse Operation (ADJUSTMENT) → Warehouse Movement → Stock
 * Position update. Used for confirmed discrepancies and as the write path for Inventory
 * reconciliation. Does not implement Reservation / Public API / UI.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected engine and stock repository.")
public final class WarehouseAdjustmentService {

    private final WarehouseOperationEngine operationEngine;
    private final StockPositionRepository stockPositions;

    public WarehouseAdjustmentService(
            WarehouseOperationEngine operationEngine, StockPositionRepository stockPositions) {
        this.operationEngine = Objects.requireNonNull(operationEngine, "operationEngine");
        this.stockPositions = Objects.requireNonNull(stockPositions, "stockPositions");
    }

    /**
     * Executes an adjustment: creates an ADJUSTMENT operation with the resulting target quantity,
     * records a movement, and updates the AVAILABLE stock position.
     *
     * @return completed warehouse operation
     * @throws InvalidWarehouseStateException if the resulting quantity would be negative or stock is
     *     missing for a decrease
     */
    public WarehouseOperation adjust(AdjustmentRequest request) {
        Objects.requireNonNull(request, "request");
        StockQuantity targetQuantity = resolveTargetQuantity(request);
        WarehouseOperation draft =
                operationEngine.create(
                        WarehouseOperationType.ADJUSTMENT,
                        request.material(),
                        request.warehouseId(),
                        request.storageCellId(),
                        StockState.AVAILABLE,
                        targetQuantity);
        return operationEngine.execute(draft.id());
    }

    private StockQuantity resolveTargetQuantity(AdjustmentRequest request) {
        Optional<StockPosition> existing =
                stockPositions.findByNaturalKey(
                        request.warehouseId(),
                        request.storageCellId(),
                        request.material(),
                        StockState.AVAILABLE);
        BigDecimal current =
                existing.map(position -> position.quantity().value()).orElse(BigDecimal.ZERO);
        BigDecimal target = current.add(request.quantityDelta());
        if (target.signum() < 0) {
            throw new InvalidWarehouseStateException(
                    "Adjustment would result in negative stock: current="
                            + current
                            + ", quantityDelta="
                            + request.quantityDelta());
        }
        if (existing.isEmpty() && request.quantityDelta().signum() < 0) {
            throw new InvalidWarehouseStateException(
                    "Insufficient stock for adjustment: no AVAILABLE position at cell="
                            + request.storageCellId());
        }
        return StockQuantity.of(target);
    }
}
