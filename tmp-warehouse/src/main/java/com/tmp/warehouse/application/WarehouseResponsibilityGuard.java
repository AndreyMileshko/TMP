package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.WarehouseId;

/**
 * Operational warehouse scope guard (ADR-037): supplements RBAC with User↔Warehouse
 * responsibility. Does not replace {@link com.tmp.security.api.AuthorizationService}.
 */
@FunctionalInterface
public interface WarehouseResponsibilityGuard {

    /**
     * Requires the current authenticated user to be responsible for {@code warehouseId}.
     *
     * @throws com.tmp.security.api.AccessDeniedException when unauthenticated or not responsible
     */
    void requireResponsible(WarehouseId warehouseId);
}
