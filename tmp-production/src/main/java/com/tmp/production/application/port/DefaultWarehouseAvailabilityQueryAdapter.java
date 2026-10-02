package com.tmp.production.application.port;

import com.tmp.warehouse.api.WarehouseApi.StockStateView;
import com.tmp.warehouse.api.WarehouseApi.StockView;
import com.tmp.warehouse.api.WarehouseQueryApi;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    @Override
    public Map<UUID, BigDecimal> availableQuantities(
            UUID warehouseId, Collection<UUID> materialReferenceIds) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(materialReferenceIds, "materialReferenceIds");
        Set<UUID> requested = new HashSet<>();
        for (UUID materialReferenceId : materialReferenceIds) {
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            requested.add(materialReferenceId);
        }
        if (requested.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BigDecimal> availableByMaterial = new HashMap<>();
        for (StockView stock : warehouseQuery.getStockByWarehouse(warehouseId)) {
            if (stock.stockState() != StockStateView.AVAILABLE) {
                continue;
            }
            if (!requested.contains(stock.materialReferenceId())) {
                continue;
            }
            availableByMaterial.merge(
                    stock.materialReferenceId(), stock.quantity(), BigDecimal::add);
        }
        return Map.copyOf(availableByMaterial);
    }
}
