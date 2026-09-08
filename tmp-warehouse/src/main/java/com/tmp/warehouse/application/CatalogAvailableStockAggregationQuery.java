package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.MaterialReferenceId;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.repository.AvailableStockAggregationQuery;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Catalogue-backed AVAILABLE aggregation for unit tests and constructor fallback.
 *
 * <p>Loads stock per distinct material reference (not per warehouse/cell query). Prefer {@link
 * com.tmp.warehouse.persistence.JdbcAvailableStockAggregationQuery} in production wiring.
 */
public final class CatalogAvailableStockAggregationQuery implements AvailableStockAggregationQuery {

    private final StockPositionRepository stockPositions;
    private final WarehouseCatalogRepository catalog;
    private final MaterialReferenceRepository materials;

    public CatalogAvailableStockAggregationQuery(
            StockPositionRepository stockPositions,
            WarehouseCatalogRepository catalog,
            MaterialReferenceRepository materials) {
        this.stockPositions = Objects.requireNonNull(stockPositions, "stockPositions");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.materials = Objects.requireNonNull(materials, "materials");
    }

    @Override
    public List<AvailableCellStock> findAvailableByMaterials(
            Collection<UUID> materialReferenceIds) {
        Objects.requireNonNull(materialReferenceIds, "materialReferenceIds");
        Set<UUID> distinct = new LinkedHashSet<>();
        for (UUID id : materialReferenceIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return List.of();
        }

        Map<UUID, Warehouse> warehousesById = new HashMap<>();
        for (Warehouse warehouse : catalog.findAll()) {
            warehousesById.put(warehouse.id().value(), warehouse);
        }

        Map<CellKey, BigDecimal> totals = new LinkedHashMap<>();
        Map<CellKey, AvailableCellStock> meta = new LinkedHashMap<>();

        for (UUID materialId : distinct) {
            MaterialReference material =
                    materials.findById(MaterialReferenceId.of(materialId)).orElse(null);
            if (material == null) {
                continue;
            }
            for (StockPosition position : stockPositions.findByMaterial(material)) {
                if (position.stockState() != StockState.AVAILABLE) {
                    continue;
                }
                BigDecimal qty = position.quantity().value();
                if (qty.signum() <= 0) {
                    continue;
                }
                Warehouse warehouse = warehousesById.get(position.warehouseId().value());
                if (warehouse == null || !warehouse.active()) {
                    continue;
                }
                StorageCell cell = findActiveCell(position.warehouseId(), position.storageCellId().value());
                if (cell == null) {
                    continue;
                }
                CellKey key =
                        new CellKey(
                                materialId, warehouse.id().value(), cell.id().value());
                totals.merge(key, qty, BigDecimal::add);
                meta.putIfAbsent(
                        key,
                        new AvailableCellStock(
                                materialId,
                                warehouse.id().value(),
                                warehouse.code(),
                                cell.id().value(),
                                cell.code(),
                                qty));
            }
        }

        List<AvailableCellStock> result = new ArrayList<>();
        for (Map.Entry<CellKey, BigDecimal> entry : totals.entrySet()) {
            if (entry.getValue().signum() <= 0) {
                continue;
            }
            AvailableCellStock sample = meta.get(entry.getKey());
            result.add(
                    new AvailableCellStock(
                            sample.materialReferenceId(),
                            sample.warehouseId(),
                            sample.warehouseCode(),
                            sample.storageCellId(),
                            sample.storageCellCode(),
                            entry.getValue()));
        }
        return result;
    }

    private StorageCell findActiveCell(WarehouseId warehouseId, UUID storageCellId) {
        for (StorageCell cell : catalog.findStorageCellsByWarehouse(warehouseId)) {
            if (cell.id().value().equals(storageCellId) && cell.active()) {
                return cell;
            }
        }
        return null;
    }

    private record CellKey(UUID materialReferenceId, UUID warehouseId, UUID storageCellId) {}
}
