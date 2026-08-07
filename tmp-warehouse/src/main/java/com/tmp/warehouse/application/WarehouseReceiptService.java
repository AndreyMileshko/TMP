package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.WarehouseOperationType;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import java.util.Optional;

/**
 * Receipt operation — increases stock via {@link WarehouseOperationEngine} (Specification §12).
 *
 * <p>Flow: Receipt Request → Warehouse Operation (RECEIPT) → Warehouse Movement → Stock Position.
 * Does not own supplier/procurement/price data and does not mutate stock directly.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected engine and stock repository.")
public final class WarehouseReceiptService {

    private final WarehouseOperationEngine operationEngine;
    private final StockPositionRepository stockPositions;

    public WarehouseReceiptService(
            WarehouseOperationEngine operationEngine, StockPositionRepository stockPositions) {
        this.operationEngine = Objects.requireNonNull(operationEngine, "operationEngine");
        this.stockPositions = Objects.requireNonNull(stockPositions, "stockPositions");
    }

    /**
     * Executes a receipt: creates a RECEIPT operation, records a positive movement delta, and
     * creates or increases the AVAILABLE stock position.
     *
     * @return completed warehouse operation
     */
    public WarehouseOperation receive(ReceiptRequest request) {
        Objects.requireNonNull(request, "request");
        StockQuantity targetQuantity = resolveTargetQuantity(request);
        WarehouseOperation draft =
                operationEngine.create(
                        WarehouseOperationType.RECEIPT,
                        request.material(),
                        request.warehouseId(),
                        request.storageCellId(),
                        StockState.AVAILABLE,
                        targetQuantity);
        return operationEngine.execute(draft.id());
    }

    private StockQuantity resolveTargetQuantity(ReceiptRequest request) {
        Optional<StockPosition> existing =
                stockPositions.findByNaturalKey(
                        request.warehouseId(),
                        request.storageCellId(),
                        request.material(),
                        StockState.AVAILABLE);
        if (existing.isEmpty()) {
            return request.quantity();
        }
        return StockQuantity.of(
                existing.get().quantity().value().add(request.quantity().value()));
    }
}
