package com.tmp.warehouse.api;

/**
 * Derived Warehouse Demand status representation (ADR-038 / B3B-3A + optional resupply waiver).
 *
 * <p>This enum is a read-model projection. It does <strong>not</strong> imply a persisted status
 * column on Demand header or lines. {@link #RESUPPLY_WAIVED} means remaining obligation is zero via
 * waiver ledger events — never physical receipt fulfillment.
 */
public enum WarehouseDemandDerivedStatus {
    WAITING_FOR_SUPPLY,
    IN_FULFILLMENT,
    FULFILLED,
    RESUPPLY_WAIVED,
    CANCELLED
}
