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
import com.tmp.warehouse.api.WarehouseApi.ReceiveTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.RejectTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.ReturnTransferMaterialsCommand;
import com.tmp.warehouse.api.WarehouseApi.SendTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReceiveResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentRejectResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReturnResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSendResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentView;
import com.tmp.warehouse.api.WarehouseApi.TransferRequestView;
import com.tmp.warehouse.api.WarehouseApi.UpdateStorageCellCommand;
import com.tmp.warehouse.api.WarehouseApi.UpdateTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.UpdateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import java.util.UUID;

/**
 * Warehouse Application / Document command boundary — Warehouse-owned mutating operations
 * (Specification §17.2).
 */
public interface WarehouseCommandApi {

    WarehouseView createWarehouse(CreateWarehouseCommand command);

    default WarehouseView updateWarehouse(UpdateWarehouseCommand command) {
        throw new UnsupportedOperationException("updateWarehouse is not available");
    }

    StorageCellView createStorageCell(CreateStorageCellCommand command);

    default StorageCellView updateStorageCell(UpdateStorageCellCommand command) {
        throw new UnsupportedOperationException("updateStorageCell is not available");
    }

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
     * Atomically physically sends a DRAFT multi-line Transfer Document (Stage 3.5.6): stages
     * source-cell allocations, POSTs the document, creates one TRANSFER_SEND per allocation with
     * deferred destination context. Destination stock is unchanged.
     */
    TransferDocumentSendResult sendTransferDocument(SendTransferDocumentCommand command);

    /**
     * Document-level acceptance of a POSTED Transfer Document (Stage 3.5.8.1 / 3.5.8.2). Supports
     * full or partial acceptance: total accepted must be {@code > 0}; per-line accepted must be
     * {@code <=} sent. Full reject uses {@link #rejectTransferDocument}. Requires destination
     * warehouse responsibility.
     */
    TransferDocumentReceiveResult receiveTransferDocument(ReceiveTransferDocumentCommand command);

    /**
     * Whole-document reject of a POSTED Transfer Document awaiting receipt (Stage 3.5.8.3). No stock
     * mutation and no continuation. Requires destination warehouse responsibility. RejectedBy is
     * resolved from the authenticated session.
     */
    TransferDocumentRejectResult rejectTransferDocument(RejectTransferDocumentCommand command);

    /**
     * Physically returns outstanding Transfer Document materials to the source warehouse (Stage
     * 3.5.8.3). Settles and closes the document atomically. Requires source warehouse
     * responsibility.
     */
    TransferDocumentReturnResult returnTransferMaterials(ReturnTransferMaterialsCommand command);

    /**
     * Informational «Взять в работу» / takeover for a Transfer preparation, receipt, or return
     * materials task. Current worker is resolved from the authenticated session (caller must not
     * supply userId). Not an exclusive lock — another responsible user may take over.
     */
    WarehouseTaskView takeTransferTaskInWork(UUID documentId);
}
