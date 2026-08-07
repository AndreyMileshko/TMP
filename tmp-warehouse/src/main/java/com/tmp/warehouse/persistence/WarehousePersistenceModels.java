package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.WarehouseOperationType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistence-facing records for Warehouse schema rows (STAGE6-003). Domain types remain free of
 * JDBC/timestamps; adapters map between these records and domain where applicable.
 */
public final class WarehousePersistenceModels {

    private WarehousePersistenceModels() {}

    /**
     * Thrown when a Warehouse persistence optimistic-lock update affects zero rows.
     */
    public static final class OptimisticLockException extends RuntimeException {

        public OptimisticLockException(String message) {
            super(message);
        }
    }

    public record WarehouseRow(
            WarehouseId id,
            String code,
            String name,
            boolean active,
            long version,
            Instant createdAt,
            Instant updatedAt) {

        public WarehouseRow {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }

        public static WarehouseRow fromDomain(Warehouse warehouse, long version, Instant createdAt, Instant updatedAt) {
            Objects.requireNonNull(warehouse, "warehouse");
            return new WarehouseRow(
                    warehouse.id(),
                    warehouse.code(),
                    warehouse.name(),
                    warehouse.active(),
                    version,
                    createdAt,
                    updatedAt);
        }

        public Warehouse toDomain() {
            return Warehouse.of(id, code, name, active);
        }
    }

    public record StorageCellRow(
            StorageCellId id,
            WarehouseId warehouseId,
            String code,
            boolean active,
            long version,
            Instant createdAt,
            Instant updatedAt) {

        public StorageCellRow {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }

        public static StorageCellRow fromDomain(
                StorageCell cell, long version, Instant createdAt, Instant updatedAt) {
            Objects.requireNonNull(cell, "cell");
            return new StorageCellRow(
                    cell.id(),
                    cell.warehouseId(),
                    cell.code(),
                    cell.active(),
                    version,
                    createdAt,
                    updatedAt);
        }

        public StorageCell toDomain() {
            return StorageCell.of(id, warehouseId, code, active);
        }
    }

    public record StockPositionRow(
            UUID id,
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            MaterialReference materialReference,
            StockQuantity quantity,
            StockState stockState,
            long version,
            Instant createdAt,
            Instant updatedAt) {

        public StockPositionRow {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(storageCellId, "storageCellId");
            Objects.requireNonNull(materialReference, "materialReference");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(stockState, "stockState");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }

    public record WarehouseMovementRow(
            UUID id,
            UUID stockPositionId,
            WarehouseOperationType operationType,
            BigDecimal quantityDelta,
            Instant createdAt) {

        public WarehouseMovementRow {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(stockPositionId, "stockPositionId");
            Objects.requireNonNull(operationType, "operationType");
            Objects.requireNonNull(quantityDelta, "quantityDelta");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    public enum WarehouseOperationStatus {
        CREATED,
        COMPLETED,
        FAILED
    }

    public record WarehouseOperationRow(
            WarehouseOperationId id,
            WarehouseOperationType operationType,
            WarehouseOperationStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt) {

        public WarehouseOperationRow {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(operationType, "operationType");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }
}
