package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.Warehouse;
import java.util.List;

/**
 * Read port for Warehouse catalogue (warehouses list for Public API / UI).
 */
public interface WarehouseCatalogRepository {

    /** Returns all warehouses ordered by code. */
    List<Warehouse> findAll();
}
