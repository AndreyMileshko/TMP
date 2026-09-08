package com.tmp.warehouse.api;

import com.tmp.warehouse.api.WarehouseApi.ConsumptionCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateReservationLinkCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateStorageCellCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateTransferDraftCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand;
import com.tmp.warehouse.api.WarehouseApi.OperationResult;
import com.tmp.warehouse.api.WarehouseApi.ReceiptCommand;
import com.tmp.warehouse.api.WarehouseApi.ReservationLinkView;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentView;
import com.tmp.warehouse.api.WarehouseApi.TransferRequestView;
import com.tmp.warehouse.api.WarehouseApi.UpdateTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import java.util.UUID;

/**
 * Warehouse Application / Document command boundary — Warehouse-owned mutating operations
 * (Specification §17.2).
 */
public interface WarehouseCommandApi {

    WarehouseView createWarehouse(CreateWarehouseCommand command);

    StorageCellView createStorageCell(CreateStorageCellCommand command);

    /**
     * Assigns opaque Security user id as responsible for the warehouse (idempotent). Structure
     * administration — RBAC only; no responsibility guard on the catalogue itself.
     */
    void assignUserToWarehouse(UUID warehouseId, UUID userId);

    /** Removes responsibility assignment (idempotent). */
    void removeUserFromWarehouse(UUID warehouseId, UUID userId);

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

    /**
     * Creates a Warehouse-owned multi-line Transfer Document (Document Engine DRAFT + typed
     * payload). Does not mutate stock.
     */
    TransferDocumentView createTransferDocument(CreateTransferDocumentCommand command);

    /**
     * Replaces DRAFT Transfer Document warehouses and lines (payload optimistic lock). Does not
     * mutate stock.
     */
    TransferDocumentView updateTransferDocument(UpdateTransferDocumentCommand command);

    /** Deletes a DRAFT Transfer Document (Document Engine metadata + Warehouse payload). */
    void deleteTransferDocument(UUID documentId);

    /**
     * Informational «Взять в работу» / takeover for a DRAFT Transfer preparation task. Current
     * worker is resolved from the authenticated session (caller must not supply userId). Not an
     * exclusive lock — another responsible user may take over.
     */
    WarehouseTaskView takeTransferTaskInWork(UUID documentId);
}
