package com.tmp.warehouse.api;

import com.tmp.warehouse.api.WarehouseApi.ConsumptionCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateReservationLinkCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateStorageCellCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateTransferDraftCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand;
import com.tmp.warehouse.api.WarehouseApi.OperationResult;
import com.tmp.warehouse.api.WarehouseApi.ReceiptCommand;
import com.tmp.warehouse.api.WarehouseApi.ReservationLinkView;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseApi.TransferRequestView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import java.util.UUID;

/**
 * Warehouse Application / Document command boundary — Warehouse-owned mutating operations
 * (Specification §17.2).
 */
public interface WarehouseCommandApi {

    WarehouseView createWarehouse(CreateWarehouseCommand command);

    StorageCellView createStorageCell(CreateStorageCellCommand command);

    ReservationLinkView createReservationLink(CreateReservationLinkCommand command);

    /** Stage 6 unified operation path (Warehouse UI). */
    OperationResult executeWarehouseOperation(ExecuteOperationCommand command);

    OperationResult receive(ReceiptCommand command);

    OperationResult consume(ConsumptionCommand command);

    /**
     * Creates a Warehouse-owned transfer request (DRAFT). Does not change stock until {@link
     * #sendTransfer(UUID)}.
     */
    TransferRequestView createTransferDraft(CreateTransferDraftCommand command);

    /** Sends a DRAFT transfer: AVAILABLE → IN_TRANSIT at source. */
    OperationResult sendTransfer(UUID transferDraftOperationId);

    /** Receives a completed send: IN_TRANSIT → AVAILABLE at destination. */
    OperationResult receiveTransfer(UUID sendOperationId);
}
