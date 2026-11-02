package com.tmp.ui.shell.screen.production;

import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import java.util.List;
import java.util.Objects;

/**
 * Presentation-only Production Workbench tree visual mapping. Style class names only — colors live
 * in CSS. Status dots reuse canonical {@code tmp-status-dot-*} theme tokens (Orders table).
 */
public final class ProductionWorkbenchVisualStyles {

    public static final String STYLESHEET =
            "com/tmp/ui/shell/theme/tmp-production-workbench.css";

    public static final String TREE_STYLE = "tmp-production-workbench-tree";

    public static final String ROW_ORDER = "tmp-prod-row-order";
    public static final String ROW_ITEM = "tmp-prod-row-item";

    public static final List<String> ALL_ROW_STYLE_CLASSES = List.of(ROW_ORDER, ROW_ITEM);

    public static final String STATUS_AWAITING = "tmp-status-dot-warning";
    public static final String STATUS_IN_PRODUCTION = "tmp-status-dot-info";
    public static final String STATUS_PARTIAL = "tmp-status-dot-warning-strong";
    public static final String STATUS_RELEASED = "tmp-status-dot-success";
    public static final String STATUS_CANCELLED = "tmp-status-dot-danger";

    private ProductionWorkbenchVisualStyles() {}

    public static String rowStyleClass(ProductionTreeNode node) {
        Objects.requireNonNull(node, "node");
        return node.isOrder() ? ROW_ORDER : ROW_ITEM;
    }

    public static String orderStatusDotStyleClass(OrderProductionViewStatus status) {
        Objects.requireNonNull(status, "status");
        return switch (status) {
            case NOT_ACCEPTED -> STATUS_AWAITING;
            case IN_PRODUCTION -> STATUS_IN_PRODUCTION;
            case MANUFACTURED -> STATUS_RELEASED;
            case CANCELLED -> STATUS_CANCELLED;
        };
    }

    public static String itemStatusDotStyleClass(ItemProductionStateStatus status) {
        if (status == null) {
            return STATUS_AWAITING;
        }
        return switch (status) {
            case IN_PRODUCTION -> STATUS_IN_PRODUCTION;
            case PARTIALLY_RELEASED -> STATUS_PARTIAL;
            case RELEASED -> STATUS_RELEASED;
            case CANCELLED -> STATUS_CANCELLED;
        };
    }

    public static String statusDotStyleClass(ProductionTreeNode node) {
        Objects.requireNonNull(node, "node");
        if (node.isOrder()) {
            return orderStatusDotStyleClass(
                    node.orderStatus().orElse(OrderProductionViewStatus.NOT_ACCEPTED));
        }
        return itemStatusDotStyleClass(node.itemStatus().orElse(null));
    }

    public static void clearRowStyleClasses(javafx.scene.Node row) {
        Objects.requireNonNull(row, "row");
        row.getStyleClass().removeAll(ALL_ROW_STYLE_CLASSES);
    }
}
