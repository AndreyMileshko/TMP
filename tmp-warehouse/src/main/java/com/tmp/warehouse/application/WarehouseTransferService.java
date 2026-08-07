package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseOperation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;

/**
 * Inter-warehouse Transfer — two-stage ship/receive via {@link WarehouseOperationEngine}
 * (Specification §13.2).
 *
 * <p>Send: {@code AVAILABLE → IN_TRANSIT} at source. Receive: {@code IN_TRANSIT → AVAILABLE} at
 * destination. Does not change material identity or total quantity. Rejects same-warehouse use
 * (internal Move is §13.1).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected operation engine.")
public final class WarehouseTransferService {

    private final WarehouseOperationEngine operationEngine;

    public WarehouseTransferService(WarehouseOperationEngine operationEngine) {
        this.operationEngine = Objects.requireNonNull(operationEngine, "operationEngine");
    }

    /**
     * Ships stock from source AVAILABLE into source IN_TRANSIT.
     *
     * @return completed TRANSFER_SEND operation
     */
    public WarehouseOperation send(TransferSendRequest request) {
        Objects.requireNonNull(request, "request");
        requireDistinctWarehouses(request.sourceWarehouseId(), request.destinationWarehouseId());
        return operationEngine.transferSend(
                request.material(),
                request.sourceWarehouseId(),
                request.sourceCellId(),
                request.quantity());
    }

    /**
     * Receives IN_TRANSIT stock at source into AVAILABLE at destination warehouse/cell.
     *
     * @return completed TRANSFER_RECEIVE operation
     */
    public WarehouseOperation receive(TransferReceiveRequest request) {
        Objects.requireNonNull(request, "request");
        requireDistinctWarehouses(request.sourceWarehouseId(), request.destinationWarehouseId());
        return operationEngine.transferReceive(
                request.material(),
                request.sourceWarehouseId(),
                request.sourceCellId(),
                request.destinationWarehouseId(),
                request.destinationCellId(),
                request.quantity());
    }

    private static void requireDistinctWarehouses(WarehouseId source, WarehouseId destination) {
        if (source.equals(destination)) {
            throw new InvalidWarehouseStateException(
                    "Transfer requires distinct warehouses: warehouseId=" + source);
        }
    }
}
