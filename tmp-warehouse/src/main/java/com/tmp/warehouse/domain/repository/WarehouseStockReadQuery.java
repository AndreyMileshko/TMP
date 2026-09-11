package com.tmp.warehouse.domain.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-side AVAILABLE stock summaries and cell-centric lines (Stage 3.5.11 / 3.5.15 corrective).
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

    /**
     * One row per (warehouse, storage cell, material) with positive AVAILABLE sum.
     *
     * <p>Optional {@code storageCellId} filters to a single cell. Ordering: warehouse code/id →
     * cell code/id → article → materialReferenceId when multiple warehouses; cell → article →
     * materialReferenceId when a single warehouse is in scope.
     */
    List<StockCellLineRow> findCellLines(
            Collection<UUID> warehouseIds,
            UUID storageCellId,
            String search,
            int pageIndex,
            int pageSize);

    /** Count of cell-line rows matching the same filters as {@link #findCellLines}. */
    long countCellLines(Collection<UUID> warehouseIds, UUID storageCellId, String search);

    /**
     * Active storage cells for the given warehouses, ordered by warehouse code then cell code (with
     * deterministic id tie-break).
     */
    List<StockCellFilterRow> findCellFilterOptions(Collection<UUID> warehouseIds);

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

    record StockCellLineRow(
            UUID warehouseId,
            String warehouseCode,
            String warehouseName,
            UUID storageCellId,
            String storageCellCode,
            UUID materialReferenceId,
            String article,
            String name,
            String color,
            String size,
            String unitOfMeasure,
            BigDecimal availableQuantity) {

        public StockCellLineRow {
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(warehouseCode, "warehouseCode");
            Objects.requireNonNull(warehouseName, "warehouseName");
            Objects.requireNonNull(storageCellId, "storageCellId");
            Objects.requireNonNull(storageCellCode, "storageCellCode");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(article, "article");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(size, "size");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
            Objects.requireNonNull(availableQuantity, "availableQuantity");
        }
    }

    record StockCellFilterRow(
            UUID warehouseId,
            String warehouseCode,
            String warehouseName,
            UUID storageCellId,
            String storageCellCode) {

        public StockCellFilterRow {
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(warehouseCode, "warehouseCode");
            Objects.requireNonNull(warehouseName, "warehouseName");
            Objects.requireNonNull(storageCellId, "storageCellId");
            Objects.requireNonNull(storageCellCode, "storageCellCode");
        }
    }
}
