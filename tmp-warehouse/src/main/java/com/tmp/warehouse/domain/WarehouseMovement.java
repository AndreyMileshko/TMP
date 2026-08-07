package com.tmp.warehouse.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable warehouse movement history record (Specification §9).
 *
 * <p>Append-only audit entry for a stock change: references the affected {@link StockPosition},
 * records operation type and quantity delta. Never updated or deleted after creation.
 */
public final class WarehouseMovement {

    private final WarehouseMovementId id;
    private final StockPositionId stockPositionId;
    private final WarehouseOperationType operationType;
    private final BigDecimal quantityDelta;
    private final Instant createdAt;

    private WarehouseMovement(
            WarehouseMovementId id,
            StockPositionId stockPositionId,
            WarehouseOperationType operationType,
            BigDecimal quantityDelta,
            Instant createdAt) {
        this.id = id;
        this.stockPositionId = stockPositionId;
        this.operationType = operationType;
        this.quantityDelta = quantityDelta;
        this.createdAt = createdAt;
    }

    /**
     * Records a new immutable movement. Intended for append-only persistence.
     */
    public static WarehouseMovement record(
            WarehouseMovementId id,
            StockPositionId stockPositionId,
            WarehouseOperationType operationType,
            BigDecimal quantityDelta,
            Instant createdAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(stockPositionId, "stockPositionId");
        Objects.requireNonNull(operationType, "operationType");
        Objects.requireNonNull(quantityDelta, "quantityDelta");
        Objects.requireNonNull(createdAt, "createdAt");
        return new WarehouseMovement(id, stockPositionId, operationType, quantityDelta, createdAt);
    }

    public WarehouseMovementId id() {
        return id;
    }

    public StockPositionId stockPositionId() {
        return stockPositionId;
    }

    public WarehouseOperationType operationType() {
        return operationType;
    }

    public BigDecimal quantityDelta() {
        return quantityDelta;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WarehouseMovement that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "WarehouseMovement{id="
                + id
                + ", stockPositionId="
                + stockPositionId
                + ", operationType="
                + operationType
                + ", quantityDelta="
                + quantityDelta
                + ", createdAt="
                + createdAt
                + '}';
    }
}
