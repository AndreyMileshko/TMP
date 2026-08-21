package com.tmp.production.application.port;

import com.tmp.warehouse.api.WarehouseQueryApi;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Adapter over {@link WarehouseQueryApi} for Production material availability reads. */
public final class DefaultWarehouseAvailabilityQueryAdapter implements WarehouseAvailabilityQueryPort {

    private static final BigDecimal PROBE_QUANTITY = BigDecimal.ONE;

    private final WarehouseQueryApi warehouseQuery;

    public DefaultWarehouseAvailabilityQueryAdapter(WarehouseQueryApi warehouseQuery) {
        this.warehouseQuery = Objects.requireNonNull(warehouseQuery, "warehouseQuery");
    }

    @Override
    public List<WarehouseCatalogEntry> listWarehouses() {
        return warehouseQuery.listWarehouses().stream()
                .map(
                        view ->
                                new WarehouseCatalogEntry(
                                        view.warehouseId(),
                                        view.code(),
                                        view.name(),
                                        view.active()))
                .toList();
    }

    @Override
    public List<MaterialReferenceEntry> listMaterialReferences() {
        return warehouseQuery.listMaterialReferences().stream()
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

    @Override
    public BigDecimal availableQuantity(UUID materialReferenceId, UUID warehouseId) {
        Objects.requireNonNull(materialReferenceId, "materialReferenceId");
        Objects.requireNonNull(warehouseId, "warehouseId");
        return warehouseQuery
                .checkAvailability(materialReferenceId, warehouseId, PROBE_QUANTITY)
                .availableQuantity();
    }
}
