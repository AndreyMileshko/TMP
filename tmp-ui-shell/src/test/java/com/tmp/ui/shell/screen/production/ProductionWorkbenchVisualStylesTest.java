package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductionWorkbenchVisualStylesTest {

    private static final UUID ORDER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ITEM = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    void statusDotsReuseCanonicalThemeClasses() {
        assertEquals(
                "tmp-status-dot-warning",
                ProductionWorkbenchVisualStyles.orderStatusDotStyleClass(
                        OrderProductionViewStatus.NOT_ACCEPTED));
        assertEquals(
                "tmp-status-dot-info",
                ProductionWorkbenchVisualStyles.orderStatusDotStyleClass(
                        OrderProductionViewStatus.IN_PRODUCTION));
        assertEquals(
                "tmp-status-dot-success",
                ProductionWorkbenchVisualStyles.orderStatusDotStyleClass(
                        OrderProductionViewStatus.MANUFACTURED));
        assertEquals(
                "tmp-status-dot-danger",
                ProductionWorkbenchVisualStyles.orderStatusDotStyleClass(
                        OrderProductionViewStatus.CANCELLED));

        assertEquals(
                "tmp-status-dot-warning",
                ProductionWorkbenchVisualStyles.itemStatusDotStyleClass(null));
        assertEquals(
                "tmp-status-dot-info",
                ProductionWorkbenchVisualStyles.itemStatusDotStyleClass(
                        ItemProductionStateStatus.IN_PRODUCTION));
        assertEquals(
                "tmp-status-dot-warning-strong",
                ProductionWorkbenchVisualStyles.itemStatusDotStyleClass(
                        ItemProductionStateStatus.PARTIALLY_RELEASED));
        assertEquals(
                "tmp-status-dot-success",
                ProductionWorkbenchVisualStyles.itemStatusDotStyleClass(
                        ItemProductionStateStatus.RELEASED));
        assertEquals(
                "tmp-status-dot-danger",
                ProductionWorkbenchVisualStyles.itemStatusDotStyleClass(
                        ItemProductionStateStatus.CANCELLED));
    }

    @Test
    void rowStyleClassDistinguishesOrderAndItem() {
        ProductionTreeNode order =
                ProductionTreeNode.order(
                        ORDER,
                        "25096174",
                        "Customer",
                        OrderProductionViewStatus.NOT_ACCEPTED,
                        "—",
                        "—",
                        "—",
                        List.of());
        ProductionTreeNode item =
                ProductionTreeNode.item(
                        ORDER,
                        ITEM,
                        "Поз. 1",
                        "Окно",
                        "2",
                        ItemProductionStateStatus.PARTIALLY_RELEASED,
                        "1",
                        "1",
                        1,
                        true);

        assertEquals(
                ProductionWorkbenchVisualStyles.ROW_ORDER,
                ProductionWorkbenchVisualStyles.rowStyleClass(order));
        assertEquals(
                ProductionWorkbenchVisualStyles.ROW_ITEM,
                ProductionWorkbenchVisualStyles.rowStyleClass(item));
        assertEquals("Ожидает производства", order.statusLabel());
        assertEquals("Частично выпущено", item.statusLabel());
        assertEquals(
                ProductionWorkbenchVisualStyles.STATUS_AWAITING,
                ProductionWorkbenchVisualStyles.statusDotStyleClass(order));
        assertEquals(
                ProductionWorkbenchVisualStyles.STATUS_PARTIAL,
                ProductionWorkbenchVisualStyles.statusDotStyleClass(item));
    }

    @Test
    void secondaryFlexAbsorbsLeftoverWidthWithoutStretchingNumericColumns() {
        double fixed =
                ProductionWorkbenchVisualStyles.COL_CHECKBOX_PREF
                        + ProductionWorkbenchVisualStyles.COL_IDENTITY_PREF
                        + ProductionWorkbenchVisualStyles.COL_QUANTITY_PREF
                        + ProductionWorkbenchVisualStyles.COL_STATUS_PREF
                        + ProductionWorkbenchVisualStyles.COL_RELEASED_PREF
                        + ProductionWorkbenchVisualStyles.COL_REMAINING_PREF;
        double at1650 = ProductionWorkbenchVisualStyles.secondaryFlexPrefWidth(1600, fixed);
        assertTrue(at1650 > 500, "secondary should absorb most leftover at ~1650px");
        assertEquals(
                1600 - fixed - ProductionWorkbenchVisualStyles.TREE_VERTICAL_SCROLL_GUTTER,
                at1650,
                0.01);
        assertEquals(
                ProductionWorkbenchVisualStyles.COL_SECONDARY_MIN,
                ProductionWorkbenchVisualStyles.secondaryFlexPrefWidth(400, fixed),
                0.01);
    }

    @Test
    void scopedCssDefinesGridSeparatorsAndRowTintsWithoutGlobalLeak() throws Exception {
        String css;
        try (var in =
                ProductionWorkbenchVisualStyles.class
                        .getClassLoader()
                        .getResourceAsStream(ProductionWorkbenchVisualStyles.STYLESHEET)) {
            assertTrue(in != null);
            css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(css.contains(".tmp-production-workbench-tree"));
        assertTrue(css.contains("tmp-prod-row-order"));
        assertTrue(css.contains("tmp-prod-row-item"));
        assertTrue(css.contains("-tmp-table-row-separator"));
        assertTrue(css.contains("-tmp-table-column-separator"));
        assertTrue(css.contains("-tmp-table-selection-bg"));
        assertTrue(css.contains("-tmp-table-header-bg"));
        assertTrue(css.contains("-tmp-prod-order-row-bg"));
        assertTrue(css.contains("-tmp-prod-item-row-bg"));
        assertTrue(!css.contains(".table-view .table-row-cell.tmp-prod-row-order"));
    }
}