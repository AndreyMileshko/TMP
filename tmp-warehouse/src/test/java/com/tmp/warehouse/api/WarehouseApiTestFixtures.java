package com.tmp.warehouse.api;

import com.tmp.warehouse.api.WarehouseApi.StockStateView;
import com.tmp.warehouse.api.WarehouseApi.StockView;
import java.math.BigDecimal;
import java.util.UUID;

/** Shared Public API DTO fixtures for Warehouse tests. */
public final class WarehouseApiTestFixtures {

    private WarehouseApiTestFixtures() {}

    public static StockView stockView(
            String article,
            String materialName,
            String color,
            String size,
            String unitOfMeasure,
            String warehouse,
            String storageCell,
            BigDecimal quantity,
            UUID warehouseId,
            UUID storageCellId) {
        return StockView.of(
                UUID.randomUUID(),
                article,
                materialName,
                color,
                size,
                unitOfMeasure,
                warehouse,
                storageCell,
                quantity,
                StockStateView.AVAILABLE,
                warehouseId,
                storageCellId);
    }

    public static StockView minimalStockView(
            String article, UUID warehouseId, UUID storageCellId, BigDecimal quantity) {
        return stockView(
                article,
                "",
                "",
                "",
                "",
                warehouseId.toString(),
                storageCellId.toString(),
                quantity,
                warehouseId,
                storageCellId);
    }
}
