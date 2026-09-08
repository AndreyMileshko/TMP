package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.WarehouseId;
import java.util.List;
import java.util.UUID;

/**
 * Persistence for User ↔ Warehouse responsibility (ADR-037 / Stage 3.5.1).
 *
 * <p>{@code userId} is an opaque Security user UUID. This repository never accesses Security
 * tables.
 */
public interface WarehouseUserResponsibilityRepository {

    /** Idempotent assign; duplicate pair is a no-op. */
    void assign(UUID userId, WarehouseId warehouseId);

    /** Idempotent remove; missing pair is a no-op. */
    void remove(UUID userId, WarehouseId warehouseId);

    boolean isResponsible(UUID userId, WarehouseId warehouseId);

    /** Deterministic order by warehouse id. */
    List<WarehouseId> listWarehouseIdsForUser(UUID userId);

    /** Deterministic order by user id. */
    List<UUID> listUserIdsForWarehouse(WarehouseId warehouseId);
}
