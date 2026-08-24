package com.tmp.production.security;

import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.security.api.PermissionId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Immutable set of Production {@link PermissionDescriptor}s (Production Specification §20).
 *
 * <p>Duplicates are rejected at class initialization. Descriptors are metadata only — they do not
 * grant permissions to users or roles.
 */
public final class ProductionPermissionCatalog {

    private static final List<PermissionDescriptor> DESCRIPTORS =
            List.of(
                    descriptor(
                            ProductionPermissions.PRODUCTION_VIEW,
                            "Просмотр Production",
                            "Просмотр Production заказа и истории"),
                    descriptor(
                            ProductionPermissions.PRODUCTION_ACCEPT,
                            "Принять в производство",
                            "Принятие заказа в производство"),
                    descriptor(
                            ProductionPermissions.PRODUCTION_CHECK_MATERIALS,
                            "Проверить материалы",
                            "Проверка наличия материалов"),
                    descriptor(
                            ProductionPermissions.PRODUCTION_CREATE_TRANSFER,
                            "Создать перемещение",
                            "Инициирование создания складского перемещения"),
                    descriptor(
                            ProductionPermissions.PRODUCTION_CONFIRM_RECEIPT,
                            "Подтвердить получение",
                            "Подтверждение получения материалов"),
                    descriptor(
                            ProductionPermissions.PRODUCTION_RELEASE,
                            "Выпустить изделия",
                            "Выпуск изделий"),
                    descriptor(
                            ProductionPermissions.PRODUCTION_CANCEL,
                            "Отменить производство",
                            "Отмена производства заказа"));

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
        if (DESCRIPTORS.size() != ProductionPermissions.all().size()) {
            throw new IllegalStateException(
                    "Permission descriptor count must equal ProductionPermissions.all()");
        }
        if (BY_ID.size() != DESCRIPTORS.size()) {
            throw new IllegalStateException("Duplicate permission descriptors detected");
        }
    }

    private ProductionPermissionCatalog() {}

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
