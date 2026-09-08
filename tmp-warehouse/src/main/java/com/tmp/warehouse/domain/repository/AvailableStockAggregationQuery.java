package com.tmp.warehouse.domain.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Batch read of AVAILABLE stock aggregated per material / active warehouse / active cell.
 *
 * <p>IN_TRANSIT and BLOCKED are excluded. Inactive warehouses and inactive cells are excluded.
 * Does not mutate stock.
 */
public interface AvailableStockAggregationQuery {

    /**
     * Returns one row per (material, warehouse, cell) with positive AVAILABLE quantity sum.
     * Empty input yields an empty list without querying.
     */
    List<AvailableCellStock> findAvailableByMaterials(Collection<UUID> materialReferenceIds);

    /** Aggregated AVAILABLE quantity at one active cell of an active warehouse. */
    record AvailableCellStock(
            UUID materialReferenceId,
            UUID warehouseId,
            String warehouseCode,
            UUID storageCellId,
            String storageCellCode,
            BigDecimal availableQuantity) {

        public AvailableCellStock {
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(warehouseCode, "warehouseCode");
            Objects.requireNonNull(storageCellId, "storageCellId");
            Objects.requireNonNull(storageCellCode, "storageCellCode");
            Objects.requireNonNull(availableQuantity, "availableQuantity");
            if (availableQuantity.signum() <= 0) {
                throw new IllegalArgumentException(
                        "availableQuantity must be positive: " + availableQuantity);
            }
        }
    }
}
