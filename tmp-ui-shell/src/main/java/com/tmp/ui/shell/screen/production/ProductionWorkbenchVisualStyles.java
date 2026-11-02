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

    /** Comfortable checkbox + disclosure column. */
    public static final double COL_CHECKBOX_MIN = 56;
    public static final double COL_CHECKBOX_PREF = 64;
    public static final double COL_CHECKBOX_MAX = 72;

    /** Compact order/position identity. */
    public static final double COL_IDENTITY_MIN = 110;
    public static final double COL_IDENTITY_PREF = 140;
    public static final double COL_IDENTITY_MAX = 180;

    /** Flex column — absorbs leftover table width. */
    public static final double COL_SECONDARY_MIN = 120;

    /** Numeric headers must remain readable ("Количество"). */
    public static final double COL_QUANTITY_MIN = 100;
    public static final double COL_QUANTITY_PREF = 110;
    public static final double COL_QUANTITY_MAX = 120;

    /** Status dot + full meaningful caption. */
    public static final double COL_STATUS_MIN = 180;
    public static final double COL_STATUS_PREF = 200;
    public static final double COL_STATUS_MAX = 240;

    /** Numeric header "Изготовлено". */
    public static final double COL_RELEASED_MIN = 110;
    public static final double COL_RELEASED_PREF = 120;
    public static final double COL_RELEASED_MAX = 130;

    /** Numeric header "Осталось". */
    public static final double COL_REMAINING_MIN = 90;
    public static final double COL_REMAINING_PREF = 100;
    public static final double COL_REMAINING_MAX = 110;

    /** Material requests coverage column ("Учтено: X/Y"). */
    public static final double COL_MATERIAL_REQUESTS_MIN = 235;
    public static final double COL_MATERIAL_REQUESTS_PREF = 250;
    public static final double COL_MATERIAL_REQUESTS_MAX = 260;

    /**
     * Reserved for vertical scrollbar so show/hide does not reflow every column (Warehouse Stocks
     * pattern).
     */
    public static final double TREE_VERTICAL_SCROLL_GUTTER = 18;

    /**
     * Maximum unused trailing space allowed at normal desktop widths after redistribution (gutter +
     * rounding slack).
     */
    public static final double TREE_MAX_UNUSED_TRAILING = 28;

    private ProductionWorkbenchVisualStyles() {}

    /**
     * Preferred width for the flex secondary column given the current table width and fixed sibling
     * preferred widths. Never below {@link #COL_SECONDARY_MIN}.
     */
    public static double secondaryFlexPrefWidth(double tableWidth, double fixedColumnsPrefWidth) {
        if (tableWidth <= 0 || fixedColumnsPrefWidth < 0) {
            return COL_SECONDARY_MIN;
        }
        return Math.max(
                COL_SECONDARY_MIN, tableWidth - fixedColumnsPrefWidth - TREE_VERTICAL_SCROLL_GUTTER);
    }

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
