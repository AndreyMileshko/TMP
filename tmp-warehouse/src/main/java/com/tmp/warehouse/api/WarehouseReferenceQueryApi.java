package com.tmp.warehouse.api;

import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceView;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Warehouse-owned cross-capability reference reads for trusted backend callers (Stage 3.5.9
 * corrective).
 *
 * <p>Read-only: no stock mutation, no source routing, no Transfer Document / task creation, no
 * WarehouseResponsibility filter. Does not replace user-facing {@link WarehouseQueryApi} catalogue
 * methods — those retain Warehouse permission guards. Caller use-case owns user-facing
 * authorization.
 */
public interface WarehouseReferenceQueryApi {

    /**
     * Returns the warehouse reference for a known id, or empty if it does not exist.
     *
     * <p>No RBAC check. Does not expose the full warehouse catalogue.
     */
    Optional<WarehouseReferenceView> getWarehouseReference(UUID warehouseId);

    /**
     * Material references matching Spec→Warehouse identity ({@code article + color +
     * unitOfMeasure}). Size is not part of Spec matching. Returns 0..N candidates for caller
     * fail-closed resolution (exact / unresolved / ambiguous). Does not create Material→Warehouse
     * bindings.
     */
    List<MaterialReferenceView> findMaterialReferencesByIdentity(
            String article, String color, String unitOfMeasure);

    /** Minimal warehouse identity snapshot for destination validation. */
    record WarehouseReferenceView(UUID warehouseId, String code, String name, boolean active) {

        public WarehouseReferenceView {
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(name, "name");
        }
    }
}
