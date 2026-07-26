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
                            "View orders",
                            "View customer orders"),
                    descriptor(
                            OrderManagementPermissions.ORDER_CREATE,
                            "Create order",
                            "Create a draft customer order"),
                    descriptor(
                            OrderManagementPermissions.ORDER_EDIT,
                            "Edit order",
                            "Edit commercial fields of a draft customer order"),
                    descriptor(
                            OrderManagementPermissions.ORDER_APPROVE,
                            "Approve order",
                            "Approve a draft customer order"),
                    descriptor(
                            OrderManagementPermissions.ORDER_CANCEL,
                            "Cancel order",
                            "Cancel a draft customer order"),
                    descriptor(
                            OrderManagementPermissions.ITEM_VIEW,
                            "View order items",
                            "View order items and revisions"),
                    descriptor(
                            OrderManagementPermissions.ITEM_CREATE,
                            "Create order item",
                            "Create a draft order item"),
                    descriptor(
                            OrderManagementPermissions.ITEM_EDIT,
                            "Edit order item",
                            "Edit commercial fields of a draft order item"),
                    descriptor(
                            OrderManagementPermissions.ITEM_APPROVE,
                            "Approve order item revision",
                            "Approve a draft order item revision"),
                    descriptor(
                            OrderManagementPermissions.ITEM_CANCEL,
                            "Cancel order item",
                            "Cancel a draft order item"),
                    descriptor(
                            OrderManagementPermissions.REVISION_CREATE,
                            "Create revision",
                            "Create a new draft order item revision"),
                    descriptor(
                            OrderManagementPermissions.REVISION_EDIT,
                            "Edit revision",
                            "Edit a draft order item revision"),
                    descriptor(
                            OrderManagementPermissions.SPECIFICATION_VIEW,
                            "View specification",
                            "View item specification lines"));

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
