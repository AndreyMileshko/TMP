package com.tmp.order.capability;

import com.tmp.security.api.PermissionId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable catalogue of Order Management permission identifiers (Specification §18).
 *
 * <p>Each code has exactly three segments {@code <area>.<resource>.<action>} matching
 * {@link PermissionId}. This catalogue does not assign permissions to users or roles.
 */
public final class OrderManagementPermissions {

    public static final PermissionId ORDER_VIEW = PermissionId.of("order.order.view");
    public static final PermissionId ORDER_CREATE = PermissionId.of("order.order.create");
    public static final PermissionId ORDER_EDIT = PermissionId.of("order.order.edit");
    public static final PermissionId ORDER_APPROVE = PermissionId.of("order.order.approve");
    public static final PermissionId ORDER_CANCEL = PermissionId.of("order.order.cancel");

    public static final PermissionId ITEM_VIEW = PermissionId.of("order.item.view");
    public static final PermissionId ITEM_CREATE = PermissionId.of("order.item.create");
    public static final PermissionId ITEM_EDIT = PermissionId.of("order.item.edit");
    public static final PermissionId ITEM_APPROVE = PermissionId.of("order.item.approve");
    public static final PermissionId ITEM_CANCEL = PermissionId.of("order.item.cancel");

    public static final PermissionId REVISION_CREATE = PermissionId.of("order.revision.create");
    public static final PermissionId REVISION_EDIT = PermissionId.of("order.revision.edit");

    public static final PermissionId SPECIFICATION_VIEW = PermissionId.of("order.specification.view");

    private static final List<PermissionId> ALL =
            List.of(
                    ORDER_VIEW,
                    ORDER_CREATE,
                    ORDER_EDIT,
                    ORDER_APPROVE,
                    ORDER_CANCEL,
                    ITEM_VIEW,
                    ITEM_CREATE,
                    ITEM_EDIT,
                    ITEM_APPROVE,
                    ITEM_CANCEL,
                    REVISION_CREATE,
                    REVISION_EDIT,
                    SPECIFICATION_VIEW);

    private static final Set<PermissionId> VIEW_CAPABILITIES =
            Set.of(ORDER_VIEW, ITEM_VIEW, SPECIFICATION_VIEW);

    private OrderManagementPermissions() {}

    /** All 13 Order Management permissions in declaration order. */
    public static List<PermissionId> all() {
        return ALL;
    }

    /** View capabilities required by the Public Query API. */
    public static Set<PermissionId> viewCapabilities() {
        return VIEW_CAPABILITIES;
    }

    public static Set<PermissionId> asSet() {
        return new LinkedHashSet<>(ALL);
    }
}
