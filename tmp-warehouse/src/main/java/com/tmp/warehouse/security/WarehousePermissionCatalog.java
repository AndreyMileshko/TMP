package com.tmp.warehouse.security;

import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.security.api.PermissionId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Immutable set of Warehouse {@link PermissionDescriptor}s (Specification §18 + structure
 * management).
 *
 * <p>Duplicates are rejected at class initialization. Descriptors are metadata only — they do not
 * grant permissions to users or roles.
 */
public final class WarehousePermissionCatalog {

    private static final List<PermissionDescriptor> DESCRIPTORS =
            List.of(
                    descriptor(
                            WarehousePermissions.WAREHOUSE_VIEW,
                            "Просмотр складов",
                            "Просмотр складов, ячеек и остатков"),
                    descriptor(
                            WarehousePermissions.WAREHOUSE_RECEIPT,
                            "Приёмка на склад",
                            "Создание Receipt операций"),
                    descriptor(
                            WarehousePermissions.WAREHOUSE_MOVE,
                            "Внутреннее перемещение",
                            "Внутреннее перемещение материалов между ячейками"),
                    descriptor(
                            WarehousePermissions.WAREHOUSE_TRANSFER,
                            "Межскладское перемещение",
                            "Межскладское перемещение материалов"),
                    descriptor(
                            WarehousePermissions.WAREHOUSE_RESERVATION,
                            "Информационное резервирование",
                            "Создание информационных связей резервирования"),
                    descriptor(
                            WarehousePermissions.WAREHOUSE_CONSUMPTION,
                            "Списание материалов",
                            "Списание материалов со склада"),
                    descriptor(
                            WarehousePermissions.WAREHOUSE_ADJUSTMENT,
                            "Корректировка остатков",
                            "Корректировка складских остатков"),
                    descriptor(
                            WarehousePermissions.WAREHOUSE_INVENTORY,
                            "Инвентаризация",
                            "Инвентаризация и сверка остатков"),
                    descriptor(
                            WarehousePermissions.WAREHOUSE_STRUCTURE_VIEW,
                            "Просмотр структуры склада",
                            "Просмотр складской структуры (склады)"),
                    descriptor(
                            WarehousePermissions.WAREHOUSE_STRUCTURE_CREATE,
                            "Создание склада",
                            "Создание склада в каталоге"),
                    descriptor(
                            WarehousePermissions.WAREHOUSE_STRUCTURE_UPDATE,
                            "Изменение склада",
                            "Изменение склада в каталоге"),
                    descriptor(
                            WarehousePermissions.WAREHOUSE_STRUCTURE_DELETE,
                            "Удаление склада",
                            "Удаление склада из каталога"),
                    descriptor(
                            WarehousePermissions.STORAGE_CELL_VIEW,
                            "Просмотр ячеек хранения",
                            "Просмотр ячеек хранения"),
                    descriptor(
                            WarehousePermissions.STORAGE_CELL_CREATE,
                            "Создание ячейки хранения",
                            "Создание ячейки хранения в каталоге"),
                    descriptor(
                            WarehousePermissions.STORAGE_CELL_UPDATE,
                            "Изменение ячейки хранения",
                            "Изменение ячейки хранения в каталоге"),
                    descriptor(
                            WarehousePermissions.STORAGE_CELL_DELETE,
                            "Удаление ячейки хранения",
                            "Удаление ячейки хранения из каталога"));

    private static final Map<String, PermissionDescriptor> BY_ID =
            DESCRIPTORS.stream()
                    .collect(
                            Collectors.toMap(
                                    PermissionDescriptor::permissionId,
                                    descriptor -> descriptor,
                                    (left, right) -> {
                                        throw new IllegalStateException(
                                                "Duplicate permission descriptor: "
                                                        + left.permissionId());
                                    },
                                    LinkedHashMap::new));

    static {
        if (DESCRIPTORS.size() != WarehousePermissions.all().size()) {
            throw new IllegalStateException(
                    "Permission descriptor count must equal WarehousePermissions.all()");
        }
        if (BY_ID.size() != DESCRIPTORS.size()) {
            throw new IllegalStateException("Duplicate permission descriptors detected");
        }
    }

    private WarehousePermissionCatalog() {}

    public static List<PermissionDescriptor> all() {
        return DESCRIPTORS;
    }

    public static Optional<PermissionDescriptor> findById(PermissionId permissionId) {
        Objects.requireNonNull(permissionId, "permissionId");
        return Optional.ofNullable(BY_ID.get(permissionId.value()));
    }

    public static boolean contains(PermissionId permissionId) {
        Objects.requireNonNull(permissionId, "permissionId");
        return BY_ID.containsKey(permissionId.value());
    }

    public static boolean containsCode(String permissionCode) {
        Objects.requireNonNull(permissionCode, "permissionCode");
        return BY_ID.containsKey(permissionCode);
    }

    private static PermissionDescriptor descriptor(
            PermissionId permissionId, String displayName, String description) {
        return PermissionDescriptor.of(permissionId.value(), displayName, description);
    }
}
