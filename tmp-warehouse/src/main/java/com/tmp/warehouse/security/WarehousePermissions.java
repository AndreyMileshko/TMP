package com.tmp.warehouse.security;

import com.tmp.security.api.PermissionId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable catalogue of Warehouse permission identifiers (Specification §18 + structure
 * management).
 *
 * <p>Logical capability names {@code WAREHOUSE_*} map to {@link PermissionId} codes in the
 * mandatory {@code <area>.<resource>.<action>} format. This catalogue does not assign permissions
 * to users or roles.
 *
 * <p>Operation permissions ({@code warehouse.stock.*}, {@code warehouse.*.create} for movements)
 * remain separate from warehouse/storage-cell structure management permissions.
 */
public final class WarehousePermissions {

    /** Просмотр складов, ячеек, остатков. */
    public static final PermissionId WAREHOUSE_VIEW = PermissionId.of("warehouse.stock.view");

    /** Создание Receipt операций. */
    public static final PermissionId WAREHOUSE_RECEIPT = PermissionId.of("warehouse.receipt.create");

    /** Внутреннее перемещение. */
    public static final PermissionId WAREHOUSE_MOVE = PermissionId.of("warehouse.move.create");

    /** Межскладское перемещение. */
    public static final PermissionId WAREHOUSE_TRANSFER =
            PermissionId.of("warehouse.transfer.create");

    /** Создание информационных связей. */
    public static final PermissionId WAREHOUSE_RESERVATION =
            PermissionId.of("warehouse.reservation.create");

    /** Списание материалов. */
    public static final PermissionId WAREHOUSE_CONSUMPTION =
            PermissionId.of("warehouse.consumption.create");

    /** Корректировка остатков. */
    public static final PermissionId WAREHOUSE_ADJUSTMENT =
            PermissionId.of("warehouse.adjustment.create");

    /** Инвентаризация. */
    public static final PermissionId WAREHOUSE_INVENTORY =
            PermissionId.of("warehouse.inventory.create");

    /** Просмотр складской структуры (склады). */
    public static final PermissionId WAREHOUSE_STRUCTURE_VIEW =
            PermissionId.of("warehouse.warehouse.view");

    /** Создание склада. */
    public static final PermissionId WAREHOUSE_STRUCTURE_CREATE =
            PermissionId.of("warehouse.warehouse.create");

    /** Изменение склада. */
    public static final PermissionId WAREHOUSE_STRUCTURE_UPDATE =
            PermissionId.of("warehouse.warehouse.update");

    /** Удаление склада. */
    public static final PermissionId WAREHOUSE_STRUCTURE_DELETE =
            PermissionId.of("warehouse.warehouse.delete");

    /**
     * Просмотр ячеек хранения.
     *
     * <p>Code uses hyphen ({@code storage-cell}) because {@link PermissionId} allows {@code [a-z0-9-]}
     * only (underscore rejected).
     */
    public static final PermissionId STORAGE_CELL_VIEW =
            PermissionId.of("warehouse.storage-cell.view");

    /** Создание ячейки хранения. */
    public static final PermissionId STORAGE_CELL_CREATE =
            PermissionId.of("warehouse.storage-cell.create");

    /** Изменение ячейки хранения. */
    public static final PermissionId STORAGE_CELL_UPDATE =
            PermissionId.of("warehouse.storage-cell.update");

    /** Удаление ячейки хранения. */
    public static final PermissionId STORAGE_CELL_DELETE =
            PermissionId.of("warehouse.storage-cell.delete");

    private static final List<PermissionId> ALL =
            List.of(
                    WAREHOUSE_VIEW,
                    WAREHOUSE_RECEIPT,
                    WAREHOUSE_MOVE,
                    WAREHOUSE_TRANSFER,
                    WAREHOUSE_RESERVATION,
                    WAREHOUSE_CONSUMPTION,
                    WAREHOUSE_ADJUSTMENT,
                    WAREHOUSE_INVENTORY,
                    WAREHOUSE_STRUCTURE_VIEW,
                    WAREHOUSE_STRUCTURE_CREATE,
                    WAREHOUSE_STRUCTURE_UPDATE,
                    WAREHOUSE_STRUCTURE_DELETE,
                    STORAGE_CELL_VIEW,
                    STORAGE_CELL_CREATE,
                    STORAGE_CELL_UPDATE,
                    STORAGE_CELL_DELETE);

    private WarehousePermissions() {}

    /** All Warehouse permissions in declaration order. */
    public static List<PermissionId> all() {
        return ALL;
    }

    public static Set<PermissionId> asSet() {
        return new LinkedHashSet<>(ALL);
    }
}
