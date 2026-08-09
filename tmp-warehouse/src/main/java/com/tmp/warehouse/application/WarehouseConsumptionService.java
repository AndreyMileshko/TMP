package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.WarehouseOperationType;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;

/**
 * Consumption operation — decreases stock via {@link WarehouseOperationEngine} (Specification §14).
 *
 * <p>Flow: Consumption Request → Warehouse Operation (CONSUMPTION) → Warehouse Movement (negative
 * quantityDelta) → Stock Position update. Production defines what and how much to consume; Warehouse
 * validates availability and records history. Does not implement Adjustment / Inventory /
 * Reservation.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected engine and stock repository.")
public final class WarehouseConsumptionService {

    private final WarehouseOperationEngine operationEngine;
    private final StockPositionRepository stockPositions;

    public WarehouseConsumptionService(
            WarehouseOperationEngine operationEngine, StockPositionRepository stockPositions) {
        this.operationEngine = Objects.requireNonNull(operationEngine, "operationEngine");
        this.stockPositions = Objects.requireNonNull(stockPositions, "stockPositions");
    }

    /**
     * Executes a consumption: creates a CONSUMPTION operation, records a negative movement delta,
     * and decreases the AVAILABLE stock position.
     *
     * @return completed warehouse operation
     * @throws InvalidWarehouseStateException if stock is missing or insufficient
     */
    public WarehouseOperation consume(ConsumptionRequest request) {
        Objects.requireNonNull(request, "request");
        StockPosition existing = requireAvailableStock(request);
        StockQuantity targetQuantity =
                StockQuantity.of(
                        existing.quantity().value().subtract(request.quantity().value()));
        WarehouseOperation draft =
                operationEngine.create(
                        WarehouseOperationType.CONSUMPTION,
                        request.material(),
                        request.warehouseId(),
                        request.storageCellId(),
                        StockState.AVAILABLE,
                        targetQuantity);
        return operationEngine.execute(draft.id());
    }

    private StockPosition requireAvailableStock(ConsumptionRequest request) {
        StockPosition available =
                stockPositions
                        .findByNaturalKey(
                                request.warehouseId(),
                                request.storageCellId(),
                                request.material(),
                                StockState.AVAILABLE)
                        .orElseThrow(
                                () ->
                                        new InvalidWarehouseStateException(
                                                "Insufficient stock for consumption: no AVAILABLE position at cell="
                                                        + request.storageCellId()));
        if (available.quantity().value().compareTo(request.quantity().value()) < 0) {
            throw new InvalidWarehouseStateException(
                    "Insufficient stock for consumption: available="
                            + available.quantity().value()
                            + ", requested="
                            + request.quantity().value());
        }
        return available;
    }
}
