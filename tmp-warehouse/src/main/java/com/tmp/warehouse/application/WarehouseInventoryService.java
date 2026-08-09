package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Inventory reconciliation — records a physical count and applies an Adjustment when the system
 * balance differs (Specification §11).
 *
 * <p>Flow: Inventory Count → Difference → Adjustment Operation → Warehouse Movement → Stock
 * Position. Does not mutate stock directly and does not implement batch/FIFO/FEFO strategies.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected adjustment service and stock repository.")
public final class WarehouseInventoryService {

    private final WarehouseAdjustmentService adjustments;
    private final StockPositionRepository stockPositions;

    public WarehouseInventoryService(
            WarehouseAdjustmentService adjustments, StockPositionRepository stockPositions) {
        this.adjustments = Objects.requireNonNull(adjustments, "adjustments");
        this.stockPositions = Objects.requireNonNull(stockPositions, "stockPositions");
    }

    /**
     * Reconciles system stock to the counted quantity. When the difference is non-zero, executes an
     * {@link WarehouseAdjustmentService#adjust(AdjustmentRequest) Adjustment}. When counts match,
     * returns empty and leaves stock unchanged.
     *
     * @return completed ADJUSTMENT operation, or empty when counted quantity equals system balance
     */
    public Optional<WarehouseOperation> reconcile(InventoryCountRequest request) {
        Objects.requireNonNull(request, "request");
        BigDecimal current = currentAvailableQuantity(request);
        BigDecimal difference = request.countedQuantity().value().subtract(current);
        if (difference.signum() == 0) {
            return Optional.empty();
        }
        WarehouseOperation completed =
                adjustments.adjust(
                        new AdjustmentRequest(
                                request.material(),
                                difference,
                                request.warehouseId(),
                                request.storageCellId()));
        return Optional.of(completed);
    }

    private BigDecimal currentAvailableQuantity(InventoryCountRequest request) {
        return stockPositions
                .findByNaturalKey(
                        request.warehouseId(),
                        request.storageCellId(),
                        request.material(),
                        StockState.AVAILABLE)
                .map(StockPosition::quantity)
                .map(quantity -> quantity.value())
                .orElse(BigDecimal.ZERO);
    }
}
