package com.tmp.production.application.port;

import java.math.BigDecimal;
import java.util.List;
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
