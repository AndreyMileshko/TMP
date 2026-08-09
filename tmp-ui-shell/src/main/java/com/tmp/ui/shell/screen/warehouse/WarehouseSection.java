package com.tmp.ui.shell.screen.warehouse;

/**
 * Logical Warehouse UI sections (Capability-gated screens within the workbench).
 */
public enum WarehouseSection {
    WAREHOUSES("Список складов"),
    STOCK("Остатки склада"),
    RECEIPT("Поступление"),
    MOVE("Перемещение"),
    TRANSFER("Межскладское перемещение"),
    CONSUMPTION("Списание"),
    ADJUSTMENT("Корректировка"),
    RESERVATIONS("Информационные связи");

    private final String title;

    WarehouseSection(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
