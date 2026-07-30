package com.tmp.order.capability;

import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.security.api.PermissionId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Immutable set of Order Management {@link PermissionDescriptor}s (Specification §18).
 *
 * <p>Duplicates are rejected at class initialization. Descriptors are metadata only — they do not
 * grant permissions to users or roles.
 */
public final class OrderManagementPermissionCatalog {

    private static final List<PermissionDescriptor> DESCRIPTORS =
            List.of(
                    descriptor(
                            OrderManagementPermissions.ORDER_VIEW,
                            "Просмотр заказов",
                            "Просмотр заказов клиентов"),
                    descriptor(
                            OrderManagementPermissions.ORDER_CREATE,
                            "Создание заказов",
                            "Создание черновика заказа клиента"),
                    descriptor(
                            OrderManagementPermissions.ORDER_EDIT,
                            "Изменение заказов",
                            "Изменение коммерческих полей черновика заказа"),
                    descriptor(
                            OrderManagementPermissions.ORDER_APPROVE,
                            "Утверждение заказов",
                            "Утверждение черновика заказа клиента"),
                    descriptor(
                            OrderManagementPermissions.ORDER_CANCEL,
                            "Отмена заказов",
                            "Отмена черновика заказа клиента"),
                    descriptor(
                            OrderManagementPermissions.ITEM_VIEW,
                            "Просмотр позиций заказа",
                            "Просмотр позиций заказа и редакций"),
                    descriptor(
                            OrderManagementPermissions.ITEM_CREATE,
                            "Создание позиций заказа",
                            "Создание черновика позиции заказа"),
                    descriptor(
                            OrderManagementPermissions.ITEM_EDIT,
                            "Изменение позиций заказа",
                            "Изменение коммерческих полей черновика позиции заказа"),
                    descriptor(
                            OrderManagementPermissions.ITEM_APPROVE,
                            "Утверждение позиций заказа",
                            "Утверждение черновика редакции позиции заказа"),
                    descriptor(
                            OrderManagementPermissions.ITEM_CANCEL,
                            "Отмена позиций заказа",
                            "Отмена черновика позиции заказа"),
                    descriptor(
                            OrderManagementPermissions.REVISION_CREATE,
                            "Создание редакций позиции заказа",
                            "Создание новой черновой редакции позиции заказа"),
                    descriptor(
                            OrderManagementPermissions.REVISION_EDIT,
                            "Изменение редакций позиции заказа",
                            "Изменение черновой редакции позиции заказа"),
                    descriptor(
                            OrderManagementPermissions.SPECIFICATION_VIEW,
                            "Просмотр спецификаций позиции заказа",
                            "Просмотр строк спецификации позиции заказа"));

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
        if (DESCRIPTORS.size() != OrderManagementPermissions.all().size()) {
            throw new IllegalStateException(
                    "Permission descriptor count must equal OrderManagementPermissions.all()");
        }
        if (BY_ID.size() != DESCRIPTORS.size()) {
            throw new IllegalStateException("Duplicate permission descriptors detected");
        }
    }

    private OrderManagementPermissionCatalog() {}

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
