package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import java.util.List;
import java.util.Optional;

/**
 * Catalogue port for warehouses and storage cells (Public API / UI structure management).
 */
public interface WarehouseCatalogRepository {

    /** Returns all warehouses ordered by code. */
    List<Warehouse> findAll();

    /** Returns a warehouse by id when present. */
    Optional<Warehouse> findById(WarehouseId warehouseId);

    /** Persists a new warehouse. */
    Warehouse save(Warehouse warehouse);

    /**
     * Updates an existing warehouse (code/name/active). Does not change id. Optimistic lock is
     * handled internally.
     */
    Warehouse update(Warehouse warehouse);

    /** Persists a new storage cell. */
    StorageCell save(StorageCell cell);

    /** Returns a storage cell by id when present. */
    Optional<StorageCell> findStorageCellById(StorageCellId storageCellId);

    /**
     * Updates an existing storage cell (code/active). Does not change id or owning warehouse.
     * Optimistic lock is handled internally.
     */
    StorageCell update(StorageCell cell);

    /** Returns storage cells for a warehouse ordered by code. */
    List<StorageCell> findStorageCellsByWarehouse(WarehouseId warehouseId);
}
