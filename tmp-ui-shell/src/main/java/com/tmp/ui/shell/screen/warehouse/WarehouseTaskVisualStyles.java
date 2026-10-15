package com.tmp.ui.shell.screen.warehouse;

import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskKind;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskState;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Presentation-only Warehouse Tasks visual mapping. Style class names only — colors live in CSS.
 *
 * <p>Warehouse badge role uses authoritative {@code productionWarehouse} metadata (never name/code).
 */
public final class WarehouseTaskVisualStyles {

    public static final String STYLESHEET =
            "com/tmp/ui/shell/theme/tmp-warehouse-tasks.css";

    public static final String ROW_SUPPLY = "tmp-wh-task-row-supply";
    public static final String ROW_RETURN = "tmp-wh-task-row-return";
    public static final String ROW_RECEIPT = "tmp-wh-task-row-receipt";
    public static final String ROW_TRANSFER = "tmp-wh-task-row-transfer";
    public static final String ROW_NEUTRAL = "tmp-wh-task-row-neutral";

    public static final List<String> ALL_ROW_STYLE_CLASSES =
            List.of(ROW_SUPPLY, ROW_RETURN, ROW_RECEIPT, ROW_TRANSFER, ROW_NEUTRAL);

    public static final String BADGE_PRODUCTION = "tmp-wh-badge-production";
    public static final String BADGE_STANDARD = "tmp-wh-badge-standard";
    public static final String BADGE_SYSTEM = "tmp-wh-badge-system";

    public static final String STATUS_NEW = "tmp-status-dot-danger";
    public static final String STATUS_IN_WORK = "tmp-status-dot-success";
    public static final String STATUS_NEUTRAL = "tmp-status-dot-neutral";

    public static final String KIND_DOT_SUPPLY = "tmp-wh-kind-dot-supply";
    public static final String KIND_DOT_RETURN = "tmp-wh-kind-dot-return";
    public static final String KIND_DOT_RECEIPT = "tmp-wh-kind-dot-receipt";
    public static final String KIND_DOT_TRANSFER = "tmp-wh-kind-dot-transfer";
    public static final String KIND_DOT_NEUTRAL = "tmp-wh-kind-dot-neutral";

    public static final List<String> ALL_KIND_DOT_STYLE_CLASSES =
            List.of(
                    KIND_DOT_SUPPLY,
                    KIND_DOT_RETURN,
                    KIND_DOT_RECEIPT,
                    KIND_DOT_TRANSFER,
                    KIND_DOT_NEUTRAL);

    public static final String KIND_LABEL = "tmp-wh-kind-label";
    public static final String KIND_CELL = "tmp-wh-kind-cell";
    public static final String ROUTE_ARROW = "tmp-wh-route-arrow";
    public static final String ROUTE_ARROW_CELL = "tmp-wh-route-arrow-cell";
    public static final String ROUTE_ARROW_TEXT = "\u2192";

    public static final String SYSTEM_IN_TRANSIT = "\u0412 \u043f\u0443\u0442\u0438";

    private WarehouseTaskVisualStyles() {}

    public static String rowStyleClass(WarehouseTaskKind kind) {
        if (kind == null) {
            return ROW_NEUTRAL;
        }
        return switch (kind) {
            case PRODUCTION_MATERIAL_SUPPLY -> ROW_SUPPLY;
            case RETURN_MATERIALS -> ROW_RETURN;
            case TRANSFER_RECEIPT -> ROW_RECEIPT;
            case TRANSFER_PREPARATION -> ROW_TRANSFER;
        };
    }

    /** Kind-indicator dot class; fill matches the left task-kind stripe (not status dots). */
    public static String kindDotStyleClass(WarehouseTaskKind kind) {
        if (kind == null) {
            return KIND_DOT_NEUTRAL;
        }
        return switch (kind) {
            case PRODUCTION_MATERIAL_SUPPLY -> KIND_DOT_SUPPLY;
            case RETURN_MATERIALS -> KIND_DOT_RETURN;
            case TRANSFER_RECEIPT -> KIND_DOT_RECEIPT;
            case TRANSFER_PREPARATION -> KIND_DOT_TRANSFER;
        };
    }

    public static String statusDotStyleClass(WarehouseTaskState state) {
        if (state == null) {
            return STATUS_NEUTRAL;
        }
        return switch (state) {
            case NEW -> STATUS_NEW;
            case IN_WORK -> STATUS_IN_WORK;
        };
    }

    /**
     * Role-based warehouse badge style.
     *
     * @param warehouseId warehouse id when the location is a real warehouse; {@code null} for system
     * @param systemLocation when true, always gray system badge
     * @param productionWarehouseIds authoritative production warehouse ids from {@code WarehouseView}
     */
    public static String warehouseBadgeStyleClass(
            UUID warehouseId, boolean systemLocation, Set<UUID> productionWarehouseIds) {
        if (systemLocation || warehouseId == null) {
            return BADGE_SYSTEM;
        }
        Set<UUID> production =
                productionWarehouseIds == null ? Set.of() : productionWarehouseIds;
        if (production.contains(warehouseId)) {
            return BADGE_PRODUCTION;
        }
        return BADGE_STANDARD;
    }

    public static boolean isSystemLocationLabel(String label) {
        if (label == null || label.isBlank() || "\u2014".equals(label.trim())) {
            return true;
        }
        String trimmed = label.trim();
        return SYSTEM_IN_TRANSIT.equals(trimmed)
                || "\u041f\u043e\u0441\u0442\u0430\u0432\u0449\u0438\u043a".equalsIgnoreCase(trimmed)
                || "-".equals(trimmed);
    }

    /** Non-Label graphic: Label text-overrun turns → into "..." in a narrow column. */
    public static javafx.scene.text.Text createRouteArrowGraphic() {
        javafx.scene.text.Text arrow = new javafx.scene.text.Text(ROUTE_ARROW_TEXT);
        arrow.getStyleClass().add(ROUTE_ARROW);
        return arrow;
    }

    public static void clearRowStyleClasses(javafx.scene.Node row) {
        Objects.requireNonNull(row, "row");
        row.getStyleClass().removeAll(ALL_ROW_STYLE_CLASSES);
    }
}