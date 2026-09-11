package com.tmp.warehouse.domain.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-side Warehouse History read model (Stage 3.5.12).
 *
 * <p>Projects completed {@code WarehouseOperation} rows with physical movement semantics. Does not
 * create a parallel history store and does not mutate stock.
 */
public interface WarehouseHistoryReadQuery {

    List<HistoryRow> findHistory(
            Collection<UUID> warehouseIds,
            Instant fromInclusive,
            Instant toExclusive,
            String materialSearch,
            String operationType,
            int pageIndex,
            int pageSize);

    long countHistory(
            Collection<UUID> warehouseIds,
            Instant fromInclusive,
            Instant toExclusive,
            String materialSearch,
            String operationType);

    record HistoryRow(
            UUID entryId,
            Instant occurredAt,
            String operationType,
            UUID materialReferenceId,
            String materialArticle,
            String materialName,
            String unitOfMeasure,
            BigDecimal quantity,
            UUID sourceWarehouseId,
            String sourceWarehouseName,
            UUID sourceCellId,
            String sourceCellCode,
            UUID destinationWarehouseId,
            String destinationWarehouseName,
            UUID destinationCellId,
            String destinationCellCode,
            UUID documentId) {

        public HistoryRow {
            Objects.requireNonNull(entryId, "entryId");
            Objects.requireNonNull(occurredAt, "occurredAt");
            Objects.requireNonNull(operationType, "operationType");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(materialArticle, "materialArticle");
            Objects.requireNonNull(materialName, "materialName");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
            Objects.requireNonNull(quantity, "quantity");
        }
    }
}
