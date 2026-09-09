package com.tmp.production.application.port;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production port for Warehouse-owned cross-capability reference reads used by Material
 * Requirement preparation (Stage 3.5.9 corrective).
 *
 * <p>Does not use user-facing Warehouse catalogue APIs that require Warehouse permissions.
 */
public interface WarehouseReferenceQueryPort {

    Optional<WarehouseReferenceEntry> getWarehouse(UUID warehouseId);

    /**
     * Candidates for Spec→Warehouse material identity resolution ({@code article + color +
     * unitOfMeasure}).
     */
    List<MaterialReferenceEntry> findMaterialReferencesByIdentity(
            String article, String color, String unitOfMeasure);

    record WarehouseReferenceEntry(UUID warehouseId, String code, String name, boolean active) {

        public WarehouseReferenceEntry {
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
