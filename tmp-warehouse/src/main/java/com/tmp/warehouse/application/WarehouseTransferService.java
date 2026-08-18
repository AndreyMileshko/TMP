package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.TransferOperationContext;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.WarehouseOperationStatus;
import com.tmp.warehouse.domain.WarehouseOperationType;
import com.tmp.warehouse.domain.repository.TransferOperationContextRepository;
import com.tmp.warehouse.domain.repository.WarehouseOperationRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Inter-warehouse Transfer — draft request, send and receive via {@link WarehouseOperationEngine}
 * (Specification §13.2).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected operation engine, repositories and transaction template.")
public final class WarehouseTransferService {

    private final WarehouseOperationEngine operationEngine;
    private final WarehouseOperationRepository operations;
    private final TransferOperationContextRepository transferContexts;
    private final TransactionTemplate transactionTemplate;

    public WarehouseTransferService(
            WarehouseOperationEngine operationEngine,
            WarehouseOperationRepository operations,
            TransferOperationContextRepository transferContexts,
            TransactionTemplate transactionTemplate) {
        this.operationEngine = Objects.requireNonNull(operationEngine, "operationEngine");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.transferContexts = Objects.requireNonNull(transferContexts, "transferContexts");
        this.transactionTemplate =
                Objects.requireNonNull(transactionTemplate, "transactionTemplate");
    }

    /**
     * Creates a DRAFT transfer request. Does not mutate stock (Specification §13.2 / Production
     * workflow).
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
     * Receives stock from a completed TRANSFER_SEND into destination AVAILABLE.
     *
     * <p>Exactly one successful receive is allowed per send. The receive marker and stock change
     * commit in the same local transaction ({@code REQUIRED}).
     *
     * @param sendOperationId completed send operation id
     */
    public WarehouseOperation receiveFromSend(WarehouseOperationId sendOperationId) {
        Objects.requireNonNull(sendOperationId, "sendOperationId");
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
                            WarehouseOperation completedReceive =
                                    operationEngine.transferReceive(
                                            send.material(),
                                            send.warehouseId(),
                                            send.storageCellId(),
                                            context.destinationWarehouseId(),
                                            context.destinationStorageCellId(),
                                            send.quantity());
                            if (!transferContexts.claimReceiveIfAbsent(
                                    sendOperationId, completedReceive.id())) {
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
}
