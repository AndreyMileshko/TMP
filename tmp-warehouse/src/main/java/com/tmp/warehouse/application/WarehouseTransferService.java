package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.TransferOperationContext;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.WarehouseOperationStatus;
import com.tmp.warehouse.domain.WarehouseOperationType;
import com.tmp.warehouse.domain.repository.TransferOperationContextRepository;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import com.tmp.warehouse.domain.repository.WarehouseOperationRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Inter-warehouse Transfer — draft request, send and receive via {@link WarehouseOperationEngine}
 * (Specification §13.2).
 *
 * <p>Supports legacy drafts with a preselected destination cell and deferred-destination drafts
 * where the receiving user chooses the cell at receive time (Stage 3.5.3 / ADR-037).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected operation engine, repositories and transaction template.")
public final class WarehouseTransferService {

    private final WarehouseOperationEngine operationEngine;
    private final WarehouseOperationRepository operations;
    private final TransferOperationContextRepository transferContexts;
    private final WarehouseCatalogRepository catalog;
    private final TransactionTemplate transactionTemplate;

    public WarehouseTransferService(
            WarehouseOperationEngine operationEngine,
            WarehouseOperationRepository operations,
            TransferOperationContextRepository transferContexts,
            WarehouseCatalogRepository catalog,
            TransactionTemplate transactionTemplate) {
        this.operationEngine = Objects.requireNonNull(operationEngine, "operationEngine");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.transferContexts = Objects.requireNonNull(transferContexts, "transferContexts");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.transactionTemplate =
                Objects.requireNonNull(transactionTemplate, "transactionTemplate");
    }

    /**
     * Creates a DRAFT transfer request with a preselected destination cell. Does not mutate stock
     * (Specification §13.2 / Production workflow).
     */
    public WarehouseOperation createDraft(TransferDraftRequest request) {
        Objects.requireNonNull(request, "request");
        requirePositiveQuantity(request.quantity());
        requireDistinctWarehouses(request.sourceWarehouseId(), request.destinationWarehouseId());
        WarehouseOperation draft =
                operationEngine.create(
                        WarehouseOperationType.TRANSFER_SEND,
                        request.material(),
                        request.sourceWarehouseId(),
                        request.sourceCellId(),
                        StockState.IN_TRANSIT,
                        request.quantity());
        transferContexts.save(
                new TransferOperationContext(
                        draft.id(),
                        request.destinationWarehouseId(),
                        request.destinationCellId()));
        return draft;
    }

    /**
     * Creates a DRAFT transfer request without a destination cell. Destination warehouse is
     * mandatory; the cell is selected at receive. Does not mutate stock.
     */
    public WarehouseOperation createDeferredDestinationDraft(
            DeferredDestinationDraftRequest request) {
        Objects.requireNonNull(request, "request");
        requirePositiveQuantity(request.quantity());
        requireDistinctWarehouses(request.sourceWarehouseId(), request.destinationWarehouseId());
        WarehouseOperation draft =
                operationEngine.create(
                        WarehouseOperationType.TRANSFER_SEND,
                        request.material(),
                        request.sourceWarehouseId(),
                        request.sourceCellId(),
                        StockState.IN_TRANSIT,
                        request.quantity());
        transferContexts.save(
                TransferOperationContext.deferredDestination(
                        draft.id(), request.destinationWarehouseId()));
        return draft;
    }

    /** Executes a DRAFT transfer send: AVAILABLE → IN_TRANSIT at source. */
    public WarehouseOperation sendDraft(WarehouseOperationId draftOperationId) {
        Objects.requireNonNull(draftOperationId, "draftOperationId");
        transferContexts
                .findByOperationId(draftOperationId)
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Transfer context not found: " + draftOperationId));
        return operationEngine.executeTransferSendDraft(draftOperationId);
    }

    /**
     * Receives stock from a completed TRANSFER_SEND into the destination cell stored on the
     * transfer context (legacy path).
     *
     * <p>Deferred-destination contexts (no stored cell) are rejected — the receiver must supply a
     * cell via {@link #receiveFromSend(WarehouseOperationId, StorageCellId)}.
     *
     * <p>Exactly one successful receive is allowed per send. The receive marker and stock change
     * commit in the same local transaction ({@code REQUIRED}).
     *
     * @param sendOperationId completed send operation id
     */
    public WarehouseOperation receiveFromSend(WarehouseOperationId sendOperationId) {
        Objects.requireNonNull(sendOperationId, "sendOperationId");
        return receiveInternal(sendOperationId, null);
    }

    /**
     * Receives stock from a completed TRANSFER_SEND into an explicitly chosen destination cell.
     *
     * <p>For deferred contexts the cell is validated against the context destination warehouse and
     * persisted on successful receive. For legacy contexts the supplied cell must equal the
     * preselected cell.
     *
     * @param sendOperationId completed send operation id
     * @param destinationStorageCellId cell selected at receive (must belong to destination
     *     warehouse)
     */
    public WarehouseOperation receiveFromSend(
            WarehouseOperationId sendOperationId, StorageCellId destinationStorageCellId) {
        Objects.requireNonNull(sendOperationId, "sendOperationId");
        Objects.requireNonNull(destinationStorageCellId, "destinationStorageCellId");
        return receiveInternal(sendOperationId, destinationStorageCellId);
    }

    private WarehouseOperation receiveInternal(
            WarehouseOperationId sendOperationId, StorageCellId suppliedDestinationCellId) {
        WarehouseOperation received =
                transactionTemplate.execute(
                        status -> {
                            TransferOperationContext context =
                                    transferContexts
                                            .lockByOperationId(sendOperationId)
                                            .orElseThrow(
                                                    () ->
                                                            new NoSuchElementException(
                                                                    "Transfer context not found: "
                                                                            + sendOperationId));
                            if (context.isReceived()) {
                                throw alreadyReceived(sendOperationId);
                            }
                            WarehouseOperation send =
                                    operations
                                            .findById(sendOperationId)
                                            .orElseThrow(
                                                    () ->
                                                            new NoSuchElementException(
                                                                    "Warehouse operation not found: "
                                                                            + sendOperationId));
                            if (send.type() != WarehouseOperationType.TRANSFER_SEND
                                    || send.status() != WarehouseOperationStatus.COMPLETED) {
                                throw new InvalidWarehouseStateException(
                                        "Transfer receive requires completed TRANSFER_SEND: operationId="
                                                + sendOperationId);
                            }

                            StorageCellId destinationCellId =
                                    resolveDestinationCell(context, suppliedDestinationCellId);
                            // Catalogue membership is enforced when the cell is chosen at receive.
                            // Legacy receive with a preselected stored cell keeps prior behavior.
                            if (suppliedDestinationCellId != null) {
                                validateDestinationCellForWarehouse(
                                        destinationCellId, context.destinationWarehouseId());
                            }

                            WarehouseOperation completedReceive =
                                    operationEngine.transferReceive(
                                            send.material(),
                                            send.warehouseId(),
                                            send.storageCellId(),
                                            context.destinationWarehouseId(),
                                            destinationCellId,
                                            send.quantity());
                            if (!transferContexts.claimReceiveIfAbsent(
                                    sendOperationId,
                                    completedReceive.id(),
                                    destinationCellId)) {
                                throw alreadyReceived(sendOperationId);
                            }
                            return completedReceive;
                        });
        if (received == null) {
            throw new InvalidWarehouseStateException(
                    "Transfer receive returned null: sendOperationId=" + sendOperationId);
        }
        return received;
    }

    private static StorageCellId resolveDestinationCell(
            TransferOperationContext context, StorageCellId suppliedDestinationCellId) {
        if (suppliedDestinationCellId == null) {
            return context
                    .destinationStorageCellIdOptional()
                    .orElseThrow(
                            () ->
                                    new InvalidWarehouseStateException(
                                            "destination cell must be selected at receive: operationId="
                                                    + context.operationId()));
        }
        if (context.hasDestinationStorageCell()
                && !suppliedDestinationCellId.equals(context.destinationStorageCellId())) {
            throw new InvalidWarehouseStateException(
                    "Cannot change preselected destination cell for transfer: operationId="
                            + context.operationId()
                            + ", stored="
                            + context.destinationStorageCellId()
                            + ", supplied="
                            + suppliedDestinationCellId);
        }
        return suppliedDestinationCellId;
    }

    private void validateDestinationCellForWarehouse(
            StorageCellId destinationCellId, WarehouseId destinationWarehouseId) {
        StorageCell cell =
                catalog.findStorageCellsByWarehouse(destinationWarehouseId).stream()
                        .filter(candidate -> candidate.id().equals(destinationCellId))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new InvalidWarehouseStateException(
                                                "Destination storage cell does not belong to destination warehouse: cellId="
                                                        + destinationCellId
                                                        + ", warehouseId="
                                                        + destinationWarehouseId));
        if (!cell.active()) {
            throw new InvalidWarehouseStateException(
                    "Destination storage cell is inactive: cellId=" + destinationCellId);
        }
    }

    /** Immediate send (Stage 6 UI path). */
    public WarehouseOperation send(TransferSendRequest request) {
        Objects.requireNonNull(request, "request");
        requireDistinctWarehouses(request.sourceWarehouseId(), request.destinationWarehouseId());
        return operationEngine.transferSend(
                request.material(),
                request.sourceWarehouseId(),
                request.sourceCellId(),
                request.quantity());
    }

    /** Immediate receive with explicit destination (Stage 6 UI path). */
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

    private static InvalidWarehouseStateException alreadyReceived(WarehouseOperationId sendId) {
        return new InvalidWarehouseStateException(
                "Transfer send already received: operationId=" + sendId);
    }

    private static void requirePositiveQuantity(StockQuantity quantity) {
        if (quantity.value().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Transfer quantity must be positive: " + quantity.value());
        }
    }

    private static void requireDistinctWarehouses(WarehouseId source, WarehouseId destination) {
        if (source.equals(destination)) {
            throw new InvalidWarehouseStateException(
                    "Transfer requires distinct warehouses: warehouseId=" + source);
        }
    }

    public record TransferDraftRequest(
            MaterialReference material,
            StockQuantity quantity,
            WarehouseId sourceWarehouseId,
            StorageCellId sourceCellId,
            WarehouseId destinationWarehouseId,
            StorageCellId destinationCellId) {

        public TransferDraftRequest {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
            Objects.requireNonNull(sourceCellId, "sourceCellId");
            Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
            Objects.requireNonNull(destinationCellId, "destinationCellId");
            if (quantity.value().signum() <= 0) {
                throw new IllegalArgumentException(
                        "Transfer quantity must be positive: " + quantity.value());
            }
        }
    }

    public record DeferredDestinationDraftRequest(
            MaterialReference material,
            StockQuantity quantity,
            WarehouseId sourceWarehouseId,
            StorageCellId sourceCellId,
            WarehouseId destinationWarehouseId) {

        public DeferredDestinationDraftRequest {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
            Objects.requireNonNull(sourceCellId, "sourceCellId");
            Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
            if (quantity.value().signum() <= 0) {
                throw new IllegalArgumentException(
                        "Transfer quantity must be positive: " + quantity.value());
            }
        }
    }
}
