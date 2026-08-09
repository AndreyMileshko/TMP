package com.tmp.warehouse.security;

import com.tmp.security.api.PermissionId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable catalogue of Warehouse permission identifiers (Specification §18).
 *
 * <p>Logical capability names {@code WAREHOUSE_*} map to {@link PermissionId} codes in the
 * mandatory {@code <area>.<resource>.<action>} format. This catalogue does not assign permissions
 * to users or roles.
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

    private static final List<PermissionId> ALL =
            List.of(
                    WAREHOUSE_VIEW,
                    WAREHOUSE_RECEIPT,
                    WAREHOUSE_MOVE,
                    WAREHOUSE_TRANSFER,
                    WAREHOUSE_RESERVATION,
                    WAREHOUSE_CONSUMPTION,
                    WAREHOUSE_ADJUSTMENT,
                    WAREHOUSE_INVENTORY);

    private WarehousePermissions() {}

    /** All 8 Warehouse permissions in declaration order. */
    public static List<PermissionId> all() {
        return ALL;
    }

    public static Set<PermissionId> asSet() {
        return new LinkedHashSet<>(ALL);
    }
}
