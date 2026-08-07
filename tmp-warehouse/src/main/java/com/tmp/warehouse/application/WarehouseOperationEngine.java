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
 * COMPLETED or FAILED. Business operations such as Receipt and Move use this engine; they do not
 * bypass it. Internal Move uses {@link #move} (dual-leg source/destination update in one
 * transaction).
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
     * <p>MOVE drafts must not use this path; use {@link #move} instead.
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
        if (current.type() == WarehouseOperationType.MOVE) {
            throw new InvalidWarehouseStateException(
                    "MOVE operations must be executed via move(): operationId=" + id);
        }

        try {
            WarehouseOperation completed =
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
                                if (locked.type() == WarehouseOperationType.MOVE) {
                                    throw new InvalidWarehouseStateException(
                                            "MOVE operations must be executed via move(): operationId="
                                                    + id);
                                }
                                applyStockAndMovement(locked);
                                return operations.update(locked.complete());
                            });
            return Objects.requireNonNull(completed, "transaction returned null");
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
        if (quantity.value().signum() <= 0) {
            throw new IllegalArgumentException("Move quantity must be positive: " + quantity.value());
        }
        if (sourceCellId.equals(destinationCellId)) {
            throw new InvalidWarehouseStateException(
                    "Internal move requires distinct source and destination cells: cellId="
                            + sourceCellId);
        }

        WarehouseOperationId operationId = WarehouseOperationId.generate();
        try {
            WarehouseOperation completed =
                    transactionTemplate.execute(
                            status -> {
                                StockPosition source =
                                        stockPositions
                                                .findByNaturalKey(
                                                        warehouseId,
                                                        sourceCellId,
                                                        material,
                                                        StockState.AVAILABLE)
                                                .orElseThrow(
                                                        () ->
                                                                new InvalidWarehouseStateException(
                                                                        "Insufficient stock for move: no AVAILABLE position at source cell="
                                                                                + sourceCellId));
                                if (source.quantity().value().compareTo(quantity.value()) < 0) {
                                    throw new InvalidWarehouseStateException(
                                            "Insufficient stock for move: available="
                                                    + source.quantity().value()
                                                    + ", requested="
                                                    + quantity.value());
                                }

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
                            });
            return Objects.requireNonNull(completed, "transaction returned null");
        } catch (RuntimeException ex) {
            markFailedBestEffort(operationId);
            if (ex instanceof InvalidWarehouseStateException
                    || ex instanceof IllegalArgumentException
                    || ex instanceof NoSuchElementException) {
                throw ex;
            }
            throw new InvalidWarehouseStateException(
                    "Warehouse move execution failed: operationId=" + operationId, ex);
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
        StockQuantity sourceAfter =
                StockQuantity.of(sourceBefore.quantity().value().subtract(moveQuantity.value()));
        StockPosition sourceChanged =
                operation.applyTo(sourceBefore, StockState.AVAILABLE, sourceAfter);
        StockPosition sourceUpdated =
                stockPositions.updateQuantityAndState(
                        sourceChanged.id(),
                        sourceChanged.quantity(),
                        sourceChanged.stockState(),
                        sourceBefore.version());
        movements.append(
                WarehouseMovement.record(
                        WarehouseMovementId.generate(),
                        sourceUpdated.id(),
                        WarehouseOperationType.MOVE,
                        moveQuantity.value().negate(),
                        clock.instant()));

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
}
