package com.tmp.warehouse.domain.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-side AVAILABLE stock summaries and cell breakdown (Stage 3.5.11).
 *
 * <p>Aggregates {@code StockPosition} only — not a second inventory model. Excludes IN_TRANSIT and
 * BLOCKED. Inactive warehouses and inactive cells are excluded. Does not mutate stock.
 */
public interface WarehouseStockReadQuery {

    /**
     * One row per (warehouse, material) with positive AVAILABLE sum, ordered by article,
     * materialReferenceId, warehouseId.
     */
    List<StockSummaryRow> findSummaries(
            Collection<UUID> warehouseIds, String search, int pageIndex, int pageSize);

    /** Count of summary rows matching the same filters as {@link #findSummaries}. */
    long countSummaries(Collection<UUID> warehouseIds, String search);

    /**
     * Positive AVAILABLE quantities per active storage cell for one warehouse + material. Ordered by
     * cell code, then storageCellId.
     */
    List<StockCellRow> findCellBreakdown(UUID warehouseId, UUID materialReferenceId);

    record StockSummaryRow(
            UUID warehouseId,
            UUID materialReferenceId,
            String article,
            String name,
            String color,
            String size,
            String unitOfMeasure,
            BigDecimal availableQuantity) {

        public StockSummaryRow {
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(article, "article");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(size, "size");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
            Objects.requireNonNull(availableQuantity, "availableQuantity");
        }
    }

    record StockCellRow(
            UUID storageCellId, String storageCellCode, BigDecimal availableQuantity) {

        public StockCellRow {
            Objects.requireNonNull(storageCellId, "storageCellId");
            Objects.requireNonNull(storageCellCode, "storageCellCode");
            Objects.requireNonNull(availableQuantity, "availableQuantity");
        }
    }
}
