package com.tmp.production.application.port;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Production port for read-only Warehouse availability queries.
 *
 * <p>Isolates Production from Warehouse public DTOs at the application boundary.
 */
public interface WarehouseAvailabilityQueryPort {

    List<WarehouseCatalogEntry> listWarehouses();

    List<MaterialReferenceEntry> listMaterialReferences();

    /**
     * Returns AVAILABLE stock quantity for the material reference in the given warehouse.
     *
     * <p>Only {@code AVAILABLE} stock is counted; IN_TRANSIT and BLOCKED are excluded by Warehouse.
     */
    BigDecimal availableQuantity(UUID materialReferenceId, UUID warehouseId);

    /**
     * Batch AVAILABLE quantities on one warehouse, keyed by material reference id.
     *
     * <p>Only {@code AVAILABLE} stock is counted (cells aggregated). Materials with no AVAILABLE
     * stock are absent from the map (callers treat missing as zero). Does not mutate Warehouse.
     */
    default Map<UUID, BigDecimal> availableQuantities(
            UUID warehouseId, Collection<UUID> materialReferenceIds) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(materialReferenceIds, "materialReferenceIds");
        Map<UUID, BigDecimal> result = new java.util.HashMap<>();
        for (UUID materialReferenceId : materialReferenceIds) {
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            BigDecimal available = availableQuantity(materialReferenceId, warehouseId);
            if (available.signum() > 0) {
                result.put(materialReferenceId, available);
            }
        }
        return Map.copyOf(result);
    }

    record WarehouseCatalogEntry(UUID warehouseId, String code, String name, boolean active) {

        public WarehouseCatalogEntry {
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(name, "name");
        }
    }

    record MaterialReferenceEntry(
            UUID materialReferenceId,
            String article,
            String name,
            String color,
            String size,
            String unitOfMeasure) {

        public MaterialReferenceEntry {
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(article, "article");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(size, "size");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        }
    }
}
