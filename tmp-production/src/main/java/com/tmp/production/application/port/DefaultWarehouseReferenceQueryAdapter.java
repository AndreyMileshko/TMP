package com.tmp.production.application.port;

import com.tmp.warehouse.api.WarehouseReferenceQueryApi;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter over {@link WarehouseReferenceQueryApi} for Material Requirement reference reads.
 *
 * <p>Does not call user-facing {@code WarehouseQueryApi.listWarehouses} /
 * {@code listMaterialReferences}.
 */
public final class DefaultWarehouseReferenceQueryAdapter implements WarehouseReferenceQueryPort {

    private final WarehouseReferenceQueryApi warehouseReferences;

    public DefaultWarehouseReferenceQueryAdapter(WarehouseReferenceQueryApi warehouseReferences) {
        this.warehouseReferences =
                Objects.requireNonNull(warehouseReferences, "warehouseReferences");
    }

    @Override
    public Optional<WarehouseReferenceEntry> getWarehouse(UUID warehouseId) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        return warehouseReferences
                .getWarehouseReference(warehouseId)
                .map(
                        view ->
                                new WarehouseReferenceEntry(
                                        view.warehouseId(),
                                        view.code(),
                                        view.name(),
                                        view.active()));
    }

    @Override
    public List<MaterialReferenceEntry> findMaterialReferencesByIdentity(
            String article, String color, String unitOfMeasure) {
        return warehouseReferences
                .findMaterialReferencesByIdentity(article, color, unitOfMeasure)
                .stream()
                .map(
                        view ->
                                new MaterialReferenceEntry(
                                        view.materialReferenceId(),
                                        view.article(),
                                        view.name(),
                                        view.color(),
                                        view.size(),
                                        view.unitOfMeasure()))
                .toList();
    }
}
