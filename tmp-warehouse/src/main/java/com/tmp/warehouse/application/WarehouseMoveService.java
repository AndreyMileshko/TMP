package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.WarehouseOperation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;

/**
 * Internal Move operation — relocates stock between cells via {@link WarehouseOperationEngine}
 * (Specification §13.1).
 *
 * <p>Flow: Move Request → Warehouse Operation (MOVE) → Warehouse Movements (source −qty, destination
 * +qty) → Stock Position updates. Does not change material, warehouse, or total quantity. Does not
 * implement Transfer / Consumption / Adjustment.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected operation engine.")
public final class WarehouseMoveService {

    private final WarehouseOperationEngine operationEngine;

    public WarehouseMoveService(WarehouseOperationEngine operationEngine) {
        this.operationEngine = Objects.requireNonNull(operationEngine, "operationEngine");
    }

    /**
     * Executes an internal move within a single warehouse.
     *
     * @return completed MOVE warehouse operation
     * @throws InvalidWarehouseStateException if warehouses differ, cells are identical, or stock is
     *     insufficient
     */
    public WarehouseOperation move(MoveRequest request) {
        Objects.requireNonNull(request, "request");
        if (!request.sourceWarehouseId().equals(request.destinationWarehouseId())) {
            throw new InvalidWarehouseStateException(
                    "Internal move requires the same warehouse: source="
                            + request.sourceWarehouseId()
                            + ", destination="
                            + request.destinationWarehouseId());
        }
        if (request.sourceCellId().equals(request.destinationCellId())) {
            throw new InvalidWarehouseStateException(
                    "Internal move requires distinct source and destination cells: cellId="
                            + request.sourceCellId());
        }
        return operationEngine.move(
                request.material(),
                request.sourceWarehouseId(),
                request.sourceCellId(),
                request.destinationCellId(),
                request.quantity());
    }
}
