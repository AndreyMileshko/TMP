package com.tmp.warehouse.testsupport;

import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory catalogue for Warehouse transfer unit tests. */
public final class InMemoryWarehouseCatalogRepository implements WarehouseCatalogRepository {

    private final List<Warehouse> warehouses = new CopyOnWriteArrayList<>();
    private final List<StorageCell> cells = new CopyOnWriteArrayList<>();

    @Override
    public List<Warehouse> findAll() {
        return List.copyOf(warehouses);
    }

    @Override
    public Warehouse save(Warehouse warehouse) {
        Objects.requireNonNull(warehouse, "warehouse");
        warehouses.removeIf(existing -> existing.id().equals(warehouse.id()));
        warehouses.add(warehouse);
        return warehouse;
    }

    @Override
    public StorageCell save(StorageCell cell) {
        Objects.requireNonNull(cell, "cell");
        cells.removeIf(existing -> existing.id().equals(cell.id()));
        cells.add(cell);
        return cell;
    }

    @Override
    public List<StorageCell> findStorageCellsByWarehouse(WarehouseId warehouseId) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        List<StorageCell> result = new ArrayList<>();
        for (StorageCell cell : cells) {
            if (cell.warehouseId().equals(warehouseId)) {
                result.add(cell);
            }
        }
        return List.copyOf(result);
    }
}
