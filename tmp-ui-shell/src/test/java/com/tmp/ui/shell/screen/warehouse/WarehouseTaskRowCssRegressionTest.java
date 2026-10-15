package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.theme.TmpTheme;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Guards against unresolved Warehouse Tasks row tokens when the stylesheet is attached only to the
 * Tasks TableView (looked-up colors must be defined on {@code .table-view}, not only {@code .root}).
 */
class WarehouseTaskRowCssRegressionTest {

    @Test
    void warehouseTaskTokensDefinedOnTableViewWithBorderStripe() throws Exception {
        String css;
        try (InputStream in =
                TmpTheme.class.getClassLoader().getResourceAsStream(WarehouseTaskVisualStyles.STYLESHEET)) {
            assertTrue(in != null, "tmp-warehouse-tasks.css must be on classpath");
            css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(css.contains(".table-view {"), "tokens must be scoped to .table-view");
        assertTrue(css.contains("-tmp-wh-task-supply-tint:"));
        assertTrue(css.contains("-tmp-wh-task-supply-stripe:"));
        assertTrue(css.contains("-tmp-wh-task-return-tint:"));
        assertTrue(css.contains("-tmp-wh-task-receipt-tint:"));
        assertTrue(css.contains("-tmp-wh-task-transfer-tint:"));

        // Row paint must use solid tint + left border stripe (not .root-only gradients).
        assertTrue(css.contains("-fx-background-color: -tmp-wh-task-supply-tint;"));
        assertTrue(css.contains("-tmp-wh-task-supply-stripe;"));
        assertTrue(css.contains("-fx-border-width: 0 0 1 4;"));
        assertTrue(
                css.contains("-tmp-table-row-separator"),
                "warehouse rows must reuse shared horizontal separator token");
        assertTrue(css.contains(".tmp-wh-kind-dot-supply"));
        assertTrue(css.contains(".tmp-wh-kind-label"));
        assertTrue(css.contains(".tmp-wh-route-arrow"));
        assertTrue(css.contains(".tmp-wh-route-arrow-cell"));
        assertTrue(css.contains("-fx-padding: 0;"));
        assertFalse(
                css.contains("-fx-border-color: transparent -tmp-table-column-separator"),
                "vertical separators stay in global theme, not warehouse sheet");

        assertFalse(
                css.matches("(?s).*\\.root\\s*\\{[^}]*-tmp-wh-task-supply-tint.*"),
                "warehouse task tokens must not depend on .root-only scope");
        assertFalse(css.contains("linear-gradient(to right,"), "prefer border stripe over px gradients");
    }
}