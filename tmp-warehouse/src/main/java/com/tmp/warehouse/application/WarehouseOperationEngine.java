package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseMovement;
import com.tmp.warehouse.domain.WarehouseMovementId;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.WarehouseOperationType;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import com.tmp.warehouse.domain.repository.WarehouseMovementRepository;
import com.tmp.warehouse.domain.repository.WarehouseOperationRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Warehouse Operation Engine — sole orchestration write path for stock changes (Specification §10).
 *
 * <p>Creates DRAFT operations, executes them atomically (Movement + Stock Position), and records
 * COMPLETED or FAILED. Business operations such as Receipt, Consumption, Adjustment, Move and
 * Transfer use this engine; they do not bypass it. Receipt, Consumption and Adjustment use {@link
 * #create} + {@link #execute}; internal Move uses {@link #move}; Transfer uses {@link
 * #transferSend} / {@link #transferReceive}.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected repositories, transaction template and clock.")
public final class WarehouseOperationEngine {

    private final WarehouseOperationRepository operations;
    private final StockPositionRepository stockPositions;
    private final WarehouseMovementRepository movements;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public WarehouseOperationEngine(
            WarehouseOperationRepository operations,
            StockPositionRepository stockPositions,
            WarehouseMovementRepository movements,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.stockPositions = Objects.requireNonNull(stockPositions, "stockPositions");
        this.movements = Objects.requireNonNull(movements, "movements");
        this.transactionTemplate =
                Objects.requireNonNull(transactionTemplate, "transactionTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Creates a DRAFT warehouse operation without mutating stock.
     */
    public WarehouseOperation create(
            WarehouseOperationType type,
            MaterialReference material,
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            StockState stockState,
            StockQuantity quantity) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(storageCellId, "storageCellId");
        Objects.requireNonNull(stockState, "stockState");
        Objects.requireNonNull(quantity, "quantity");
        WarehouseOperation draft =
                WarehouseOperation.draft(
                        WarehouseOperationId.generate(),
                        type,
                        material,
                        warehouseId,
                        storageCellId,
                        stockState,
                        quantity);
        return operations.create(draft);
    }

    public Optional<WarehouseOperation> findById(WarehouseOperationId id) {
        Objects.requireNonNull(id, "id");
        return operations.findById(id);
    }

    /**
     * Checks whether the operation exists and is still in {@code DRAFT}.
     */
    public boolean canExecute(WarehouseOperationId id) {
        return findById(id).map(WarehouseOperation::isExecutable).orElse(false);
    }

    /**
     * Executes a DRAFT operation atomically: updates Stock Position via the operation write path,
     * appends Warehouse Movement, and marks the operation COMPLETED. On failure marks FAILED and
     * leaves stock/movement unchanged (transaction rollback).
     *
     * <p>MOVE and Transfer stage drafts must not use this path; use {@link #move}, {@link
     * #transferSend} or {@link #transferReceive} instead.
     */
    public WarehouseOperation execute(WarehouseOperationId id) {
        Objects.requireNonNull(id, "id");
        WarehouseOperation current =
                operations
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Warehouse operation not found: " + id));
        current.ensureDraft();
        rejectSpecializedExecute(current);

        try {
            return requireTransactionResult(
                    transactionTemplate.execute(
                            status -> {
                                WarehouseOperation locked =
                                        operations
                                                .findById(id)
                                                .orElseThrow(
                                                        () ->
                                                                new NoSuchElementException(
                                                                        "Warehouse operation not found: "
                                                                                + id));
                                locked.ensureDraft();
                                rejectSpecializedExecute(locked);
                                applyStockAndMovement(locked);
                                return operations.update(locked.complete());
                            }),
                    "Warehouse operation execution returned null: operationId=" + id);
        } catch (RuntimeException ex) {
            markFailedBestEffort(id);
            if (ex instanceof InvalidWarehouseStateException
                    || ex instanceof IllegalArgumentException
                    || ex instanceof NoSuchElementException) {
                throw ex;
            }
            throw new InvalidWarehouseStateException(
                    "Warehouse operation execution failed: operationId=" + id, ex);
        }
    }

    /**
     * Transfer send: decreases source AVAILABLE and increases source IN_TRANSIT by the same
     * quantity (Specification §13.2 stage 1).
     */
    public WarehouseOperation transferSend(
            MaterialReference material,
            WarehouseId sourceWarehouseId,
            StorageCellId sourceCellId,
            StockQuantity quantity) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
        Objects.requireNonNull(sourceCellId, "sourceCellId");
        Objects.requireNonNull(quantity, "quantity");
        requirePositiveQuantity(quantity, "Transfer send");

        WarehouseOperationId operationId = WarehouseOperationId.generate();
        try {
            return requireTransactionResult(
                    transactionTemplate.execute(
                            status -> {
                                StockPosition available =
                                        requireAvailableStock(
                                                sourceWarehouseId,
                                                sourceCellId,
                                                material,
                                                quantity,
                                                "transfer send");

                                WarehouseOperation draft =
                                        operations.create(
                                                WarehouseOperation.draft(
                                                        operationId,
                                                        WarehouseOperationType.TRANSFER_SEND,
                                                        material,
                                                        sourceWarehouseId,
                                                        sourceCellId,
                                                        StockState.IN_TRANSIT,
                                                        quantity));

                                applyQuantityDelta(
                                        available,
                                        StockState.AVAILABLE,
                                        quantity.value().negate(),
                                        WarehouseOperationType.TRANSFER_SEND);
                                applyInTransitIncrease(
                                        sourceWarehouseId,
                                        sourceCellId,
                                        material,
                                        quantity,
                                        WarehouseOperationType.TRANSFER_SEND);
                                return operations.update(draft.complete());
                            }),
                    "Warehouse transfer send returned null: operationId=" + operationId);
        } catch (RuntimeException ex) {
            markFailedBestEffort(operationId);
            rethrowOperationFailure(ex, "Warehouse transfer send failed: operationId=" + operationId);
            throw new AssertionError("unreachable");
        }
    }

    /**
     * Transfer receive: decreases source IN_TRANSIT and increases destination AVAILABLE by the same
     * quantity (Specification §13.2 stage 2).
     */
    public WarehouseOperation transferReceive(
            MaterialReference material,
            WarehouseId sourceWarehouseId,
            StorageCellId sourceCellId,
            WarehouseId destinationWarehouseId,
            StorageCellId destinationCellId,
            StockQuantity quantity) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
        Objects.requireNonNull(sourceCellId, "sourceCellId");
        Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
        Objects.requireNonNull(destinationCellId, "destinationCellId");
        Objects.requireNonNull(quantity, "quantity");
        requirePositiveQuantity(quantity, "Transfer receive");
        if (sourceWarehouseId.equals(destinationWarehouseId)) {
            throw new InvalidWarehouseStateException(
                    "Transfer receive requires distinct warehouses: warehouseId="
                            + sourceWarehouseId);
        }

        WarehouseOperationId operationId = WarehouseOperationId.generate();
        try {
            return requireTransactionResult(
                    transactionTemplate.execute(
                            status -> {
                                StockPosition inTransit =
                                        stockPositions
                                                .findByNaturalKey(
                                                        sourceWarehouseId,
                                                        sourceCellId,
                                                        material,
                                                        StockState.IN_TRANSIT)
                                                .orElseThrow(
                                                        () ->
                                                                new InvalidWarehouseStateException(
                                                                        "Insufficient IN_TRANSIT stock for transfer receive: cell="
                                                                                + sourceCellId));
                                if (inTransit.quantity().value().compareTo(quantity.value()) < 0) {
                                    throw new InvalidWarehouseStateException(
                                            "Insufficient IN_TRANSIT stock for transfer receive: available="
                                                    + inTransit.quantity().value()
                                                    + ", requested="
                                                    + quantity.value());
                                }

                                WarehouseOperation draft =
                                        operations.create(
                                                WarehouseOperation.draft(
                                                        operationId,
                                                        WarehouseOperationType.TRANSFER_RECEIVE,
                                                        material,
                                                        destinationWarehouseId,
                                                        destinationCellId,
                                                        StockState.AVAILABLE,
                                                        quantity));

                                applyQuantityDelta(
                                        inTransit,
                                        StockState.IN_TRANSIT,
                                        quantity.value().negate(),
                                        WarehouseOperationType.TRANSFER_RECEIVE);
                                applyDestinationAvailableIncrease(
                                        destinationWarehouseId,
                                        destinationCellId,
                                        material,
                                        quantity,
                                        WarehouseOperationType.TRANSFER_RECEIVE);
                                return operations.update(draft.complete());
                            }),
                    "Warehouse transfer receive returned null: operationId=" + operationId);
        } catch (RuntimeException ex) {
            markFailedBestEffort(operationId);
            rethrowOperationFailure(
                    ex, "Warehouse transfer receive failed: operationId=" + operationId);
            throw new AssertionError("unreachable");
        }
    }

    /**
     * Executes an internal Move: one MOVE operation, source (−qty) and destination (+qty)
     * movements, stock updates in a single transaction. Quantity on the operation is the moved
     * amount; material and warehouse are unchanged.
     */
    public WarehouseOperation move(
            MaterialReference material,
            WarehouseId warehouseId,
            StorageCellId sourceCellId,
            StorageCellId destinationCellId,
            StockQuantity quantity) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(sourceCellId, "sourceCellId");
        Objects.requireNonNull(destinationCellId, "destinationCellId");
        Objects.requireNonNull(quantity, "quantity");
        requirePositiveQuantity(quantity, "Move");
        if (sourceCellId.equals(destinationCellId)) {
            throw new InvalidWarehouseStateException(
                    "Internal move requires distinct source and destination cells: cellId="
                            + sourceCellId);
        }

        WarehouseOperationId operationId = WarehouseOperationId.generate();
        try {
            return requireTransactionResult(
                    transactionTemplate.execute(
                            status -> {
                                StockPosition source =
                                        requireAvailableStock(
                                                warehouseId,
                                                sourceCellId,
                                                material,
                                                quantity,
                                                "move");

                                WarehouseOperation draft =
                                        operations.create(
                                                WarehouseOperation.draft(
                                                        operationId,
                                                        WarehouseOperationType.MOVE,
                                                        material,
                                                        warehouseId,
                                                        sourceCellId,
                                                        StockState.AVAILABLE,
                                                        quantity));

                                applyMoveLegs(draft, source, destinationCellId, quantity);
                                return operations.update(draft.complete());
                            }),
                    "Warehouse move returned null: operationId=" + operationId);
        } catch (RuntimeException ex) {
            markFailedBestEffort(operationId);
            rethrowOperationFailure(ex, "Warehouse move execution failed: operationId=" + operationId);
            throw new AssertionError("unreachable");
        }
    }

    private void applyStockAndMovement(WarehouseOperation operation) {
        Optional<StockPosition> existing =
                stockPositions.findByNaturalKey(
                        operation.warehouseId(),
                        operation.storageCellId(),
                        operation.material(),
                        operation.stockState());

        StockPosition resulting;
        BigDecimal quantityDelta;
        if (existing.isEmpty()) {
            StockPosition created = operation.createPosition(operation.stockState());
            resulting = stockPositions.create(created);
            quantityDelta = resulting.quantity().value();
        } else {
            StockPosition before = existing.get();
            quantityDelta = operation.quantity().value().subtract(before.quantity().value());
            StockPosition changed =
                    operation.applyTo(before, operation.stockState(), operation.quantity());
            resulting =
                    stockPositions.updateQuantityAndState(
                            changed.id(),
                            changed.quantity(),
                            changed.stockState(),
                            before.version());
        }

        movements.append(
                WarehouseMovement.record(
                        WarehouseMovementId.generate(),
                        resulting.id(),
                        operation.type(),
                        quantityDelta,
                        clock.instant()));
    }

    private void applyMoveLegs(
            WarehouseOperation operation,
            StockPosition sourceBefore,
            StorageCellId destinationCellId,
            StockQuantity moveQuantity) {
        applyQuantityDelta(
                sourceBefore,
                StockState.AVAILABLE,
                moveQuantity.value().negate(),
                WarehouseOperationType.MOVE);

        Optional<StockPosition> destinationExisting =
                stockPositions.findByNaturalKey(
                        operation.warehouseId(),
                        destinationCellId,
                        operation.material(),
                        StockState.AVAILABLE);
        StockPosition destinationUpdated;
        if (destinationExisting.isEmpty()) {
            StockPosition created =
                    StockPosition.of(
                            operation.warehouseId(),
                            destinationCellId,
                            operation.material(),
                            StockState.AVAILABLE,
                            moveQuantity);
            destinationUpdated = stockPositions.create(created);
        } else {
            StockPosition before = destinationExisting.get();
            StockQuantity destinationAfter =
                    StockQuantity.of(before.quantity().value().add(moveQuantity.value()));
            WarehouseOperation destinationWrite =
                    WarehouseOperation.draft(
                            operation.id(),
                            WarehouseOperationType.MOVE,
                            operation.material(),
                            operation.warehouseId(),
                            destinationCellId,
                            StockState.AVAILABLE,
                            destinationAfter);
            StockPosition changed =
                    destinationWrite.applyTo(before, StockState.AVAILABLE, destinationAfter);
            destinationUpdated =
                    stockPositions.updateQuantityAndState(
                            changed.id(),
                            changed.quantity(),
                            changed.stockState(),
                            before.version());
        }
        movements.append(
                WarehouseMovement.record(
                        WarehouseMovementId.generate(),
                        destinationUpdated.id(),
                        WarehouseOperationType.MOVE,
                        moveQuantity.value(),
                        clock.instant()));
    }

    private void applyInTransitIncrease(
            WarehouseId warehouseId,
            StorageCellId cellId,
            MaterialReference material,
            StockQuantity quantity,
            WarehouseOperationType operationType) {
        Optional<StockPosition> existing =
                stockPositions.findByNaturalKey(
                        warehouseId, cellId, material, StockState.IN_TRANSIT);
        if (existing.isEmpty()) {
            StockPosition created =
                    StockPosition.of(
                            warehouseId, cellId, material, StockState.IN_TRANSIT, quantity);
            StockPosition persisted = stockPositions.create(created);
            movements.append(
                    WarehouseMovement.record(
                            WarehouseMovementId.generate(),
                            persisted.id(),
                            operationType,
                            quantity.value(),
                            clock.instant()));
            return;
        }
        applyQuantityDelta(existing.get(), StockState.IN_TRANSIT, quantity.value(), operationType);
    }

    private void applyDestinationAvailableIncrease(
            WarehouseId warehouseId,
            StorageCellId cellId,
            MaterialReference material,
            StockQuantity quantity,
            WarehouseOperationType operationType) {
        Optional<StockPosition> existing =
                stockPositions.findByNaturalKey(
                        warehouseId, cellId, material, StockState.AVAILABLE);
        if (existing.isEmpty()) {
            StockPosition created =
                    StockPosition.of(
                            warehouseId, cellId, material, StockState.AVAILABLE, quantity);
            StockPosition persisted = stockPositions.create(created);
            movements.append(
                    WarehouseMovement.record(
                            WarehouseMovementId.generate(),
                            persisted.id(),
                            operationType,
                            quantity.value(),
                            clock.instant()));
            return;
        }
        applyQuantityDelta(existing.get(), StockState.AVAILABLE, quantity.value(), operationType);
    }

    private void applyQuantityDelta(
            StockPosition before,
            StockState state,
            BigDecimal delta,
            WarehouseOperationType operationType) {
        StockQuantity after = StockQuantity.of(before.quantity().value().add(delta));
        WarehouseOperation write =
                WarehouseOperation.draft(
                        WarehouseOperationId.generate(),
                        operationType,
                        before.material(),
                        before.warehouseId(),
                        before.storageCellId(),
                        state,
                        after);
        StockPosition changed = write.applyTo(before, state, after);
        StockPosition updated =
                stockPositions.updateQuantityAndState(
                        changed.id(), changed.quantity(), changed.stockState(), before.version());
        movements.append(
                WarehouseMovement.record(
                        WarehouseMovementId.generate(),
                        updated.id(),
                        operationType,
                        delta,
                        clock.instant()));
    }

    private StockPosition requireAvailableStock(
            WarehouseId warehouseId,
            StorageCellId cellId,
            MaterialReference material,
            StockQuantity quantity,
            String action) {
        StockPosition available =
                stockPositions
                        .findByNaturalKey(warehouseId, cellId, material, StockState.AVAILABLE)
                        .orElseThrow(
                                () ->
                                        new InvalidWarehouseStateException(
                                                "Insufficient stock for "
                                                        + action
                                                        + ": no AVAILABLE position at cell="
                                                        + cellId));
        if (available.quantity().value().compareTo(quantity.value()) < 0) {
            throw new InvalidWarehouseStateException(
                    "Insufficient stock for "
                            + action
                            + ": available="
                            + available.quantity().value()
                            + ", requested="
                            + quantity.value());
        }
        return available;
    }

    private static void requirePositiveQuantity(StockQuantity quantity, String label) {
        if (quantity.value().signum() <= 0) {
            throw new IllegalArgumentException(label + " quantity must be positive: " + quantity.value());
        }
    }

    private static void rejectSpecializedExecute(WarehouseOperation operation) {
        WarehouseOperationType type = operation.type();
        if (type == WarehouseOperationType.MOVE
                || type == WarehouseOperationType.TRANSFER_SEND
                || type == WarehouseOperationType.TRANSFER_RECEIVE) {
            throw new InvalidWarehouseStateException(
                    type + " operations must be executed via dedicated engine methods: operationId="
                            + operation.id());
        }
    }

    private static void rethrowOperationFailure(RuntimeException ex, String wrappedMessage) {
        if (ex instanceof InvalidWarehouseStateException
                || ex instanceof IllegalArgumentException
                || ex instanceof NoSuchElementException) {
            throw ex;
        }
        throw new InvalidWarehouseStateException(wrappedMessage, ex);
    }

    private void markFailedBestEffort(WarehouseOperationId id) {
        try {
            operations
                    .findById(id)
                    .filter(WarehouseOperation::isExecutable)
                    .ifPresent(draft -> operations.update(draft.fail()));
        } catch (RuntimeException ignored) {
            // Best-effort failure recording; original execution error is rethrown by caller.
        }
    }

    /**
     * Executes transfer send for an existing DRAFT {@link WarehouseOperationType#TRANSFER_SEND}.
     * Does not mutate stock until invoked.
     */
    public WarehouseOperation executeTransferSendDraft(WarehouseOperationId id) {
        Objects.requireNonNull(id, "id");
        WarehouseOperation draft =
                operations
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Warehouse operation not found: " + id));
        draft.ensureDraft();
        if (draft.type() != WarehouseOperationType.TRANSFER_SEND) {
            throw new InvalidWarehouseStateException(
                    "Operation is not a TRANSFER_SEND draft: operationId=" + id);
        }
        try {
            return requireTransactionResult(
                    transactionTemplate.execute(
                            status -> {
                                WarehouseOperation locked =
                                        operations
                                                .findById(id)
                                                .orElseThrow(
                                                        () ->
                                                                new NoSuchElementException(
                                                                        "Warehouse operation not found: "
                                                                                + id));
                                locked.ensureDraft();
                                StockPosition available =
                                        requireAvailableStock(
                                                locked.warehouseId(),
                                                locked.storageCellId(),
                                                locked.material(),
                                                locked.quantity(),
                                                "transfer send");
                                applyQuantityDelta(
                                        available,
                                        StockState.AVAILABLE,
                                        locked.quantity().value().negate(),
                                        WarehouseOperationType.TRANSFER_SEND);
                                applyInTransitIncrease(
                                        locked.warehouseId(),
                                        locked.storageCellId(),
                                        locked.material(),
                                        locked.quantity(),
                                        WarehouseOperationType.TRANSFER_SEND);
                                return operations.update(locked.complete());
                            }),
                    "Warehouse transfer send draft returned null: operationId=" + id);
        } catch (RuntimeException ex) {
            markFailedBestEffort(id);
            rethrowOperationFailure(ex, "Warehouse transfer send failed: operationId=" + id);
            throw new AssertionError("unreachable");
        }
    }

    private static <T> T requireTransactionResult(T result, String message) {
        if (result == null) {
            throw new InvalidWarehouseStateException(message);
        }
        return result;
    }
}
