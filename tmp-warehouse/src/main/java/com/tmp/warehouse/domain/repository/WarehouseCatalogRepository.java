package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import java.util.List;

/**
 * Catalogue port for warehouses and storage cells (Public API / UI structure management).
 */
public interface WarehouseCatalogRepository {

    /** Returns all warehouses ordered by code. */
    List<Warehouse> findAll();

    /** Persists a new warehouse. */
    Warehouse save(Warehouse warehouse);

    /** Persists a new storage cell. */
    StorageCell save(StorageCell cell);

    /** Returns storage cells for a warehouse ordered by code. */
    List<StorageCell> findStorageCellsByWarehouse(WarehouseId warehouseId);
}
