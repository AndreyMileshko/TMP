package com.tmp.ui.shell.screen.main;

import java.util.Map;
import javafx.scene.shape.SVGPath;

/**
 * Presentation-only mapping from stable navigationId to monochrome SVG icons.
 */
final class ShellNavigationIcons {

    static final String NAV_USERS = "security.nav.users";
    static final String NAV_ROLES = "security.nav.roles";
    static final String NAV_AUDIT = "security.nav.audit";
    static final String NAV_ORDERS = "order.nav.orders";
    static final String NAV_WAREHOUSE = "warehouse.nav.workbench";
    static final String NAV_PRODUCTION = "production.nav.workbench";

    private static final String FALLBACK =
            "M4 8h4V4H4v4zm6 12h4v-4h-4v4zm-6 0h4v-4H4v4zm0-6h4v-4H4v4zm6 0h4v-4h-4v4zm6-10v4h4V4h-4zm-6 4h4V4h-4v4zm6 6h4v-4h-4v4zm0 6h4v-4h-4v4z";

    private static final Map<String, String> ICONS = Map.ofEntries(
            Map.entry(
                    NAV_USERS,
                    "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"),
            Map.entry(
                    NAV_ROLES,
                    "M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4z"),
            Map.entry(
                    NAV_AUDIT,
                    "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-5 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z"),
            Map.entry(
                    NAV_ORDERS,
                    "M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zM6 20V4h7v5h5v11H6z"),
            Map.entry(
                    NAV_WAREHOUSE,
                    "M20 8h-3V4H3c-1.1 0-2 .9-2 2v11h2c0 1.66 1.34 3 3 3s3-1.34 3-3h6c0 1.66 1.34 3 3 3s3-1.34 3-3h2v-5l-3-4z"),
            Map.entry(
                    NAV_PRODUCTION,
                    "M22.7 19l-9.1-9.1c.9-2.3.4-5-1.5-6.9-2-5-2.4-7.4-1.3L9 6 6 9 1.6 4.7C.4 7.1.9 10.1 2.9 12.1c1.9 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0l2.3-2.3c.5-.4.5-1.1.1-1.4z"));

    private ShellNavigationIcons() {
    }

    static void apply(SVGPath icon, String navigationId) {
        icon.setContent(ICONS.getOrDefault(navigationId, FALLBACK));
    }

    static boolean hasIcon(String navigationId) {
        return ICONS.containsKey(navigationId);
    }
}
