package com.tmp.production.security;

import com.tmp.security.api.PermissionId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable catalogue of Production permission identifiers (Production Specification §20).
 *
 * <p>Logical shorthand names ({@code PRODUCTION_*}) map to {@link PermissionId} codes in the
 * mandatory {@code <area>.<resource>.<action>} format. This catalogue does not assign permissions
 * to users or roles.
 */
public final class ProductionPermissions {

    /** Просмотр Production заказа и истории. */
    public static final PermissionId PRODUCTION_VIEW = PermissionId.of("production.order.view");

    /** Принятие заказа в производство. */
    public static final PermissionId PRODUCTION_ACCEPT = PermissionId.of("production.order.accept");

    /** Проверка материалов. */
    public static final PermissionId PRODUCTION_CHECK_MATERIALS =
            PermissionId.of("production.materials.check");

    /** Инициирование создания складского перемещения. */
    public static final PermissionId PRODUCTION_CREATE_TRANSFER =
            PermissionId.of("production.transfer.create");

    /** Подтверждение получения материалов. */
    public static final PermissionId PRODUCTION_CONFIRM_RECEIPT =
            PermissionId.of("production.receipt.confirm");

    /** Выпуск изделий. */
    public static final PermissionId PRODUCTION_RELEASE = PermissionId.of("production.release.create");

    /** Отмена производства заказа. */
    public static final PermissionId PRODUCTION_CANCEL =
            PermissionId.of("production.cancellation.create");

    private static final List<PermissionId> ALL =
            List.of(
                    PRODUCTION_VIEW,
                    PRODUCTION_ACCEPT,
                    PRODUCTION_CHECK_MATERIALS,
                    PRODUCTION_CREATE_TRANSFER,
                    PRODUCTION_CONFIRM_RECEIPT,
                    PRODUCTION_RELEASE,
                    PRODUCTION_CANCEL);

    private ProductionPermissions() {}

    /** All seven Production-owned permissions in declaration order. */
    public static List<PermissionId> all() {
        return ALL;
    }

    public static Set<PermissionId> asSet() {
        return new LinkedHashSet<>(ALL);
    }
}
