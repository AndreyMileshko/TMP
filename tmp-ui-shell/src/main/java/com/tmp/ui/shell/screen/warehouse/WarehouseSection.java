package com.tmp.ui.shell.screen.warehouse;

/**
 * Logical Warehouse UI sections (Capability-gated screens within the workbench).
 *
 * <p>Move / Transfer / Consumption / Adjustment user flows live on Stocks (Остатки) workspace.
 */
public enum WarehouseSection {
    WAREHOUSES("Список складов"),
    STOCK("Остатки склада"),
    RECEIPT("Поступление"),
    INVENTORY("Инвентаризация"),
    RESERVATIONS("Информационные связи");

    private final String title;

    WarehouseSection(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
