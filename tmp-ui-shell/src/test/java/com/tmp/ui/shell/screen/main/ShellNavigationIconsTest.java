package com.tmp.ui.shell.screen.main;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShellNavigationIconsTest {

    private static final String[] KNOWN_NAVIGATION_IDS = {
        ShellNavigationIcons.NAV_USERS,
        ShellNavigationIcons.NAV_ROLES,
        ShellNavigationIcons.NAV_AUDIT,
        ShellNavigationIcons.NAV_ORDERS,
        ShellNavigationIcons.NAV_WAREHOUSE,
        ShellNavigationIcons.NAV_PRODUCTION
    };

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void knownNavigationIdsHaveIcons() {
        for (String navigationId : KNOWN_NAVIGATION_IDS) {
            assertTrue(ShellNavigationIcons.hasIcon(navigationId), navigationId);
        }
    }

    @Test
    void knownNavigationIconsRenderWithNonZeroBounds() throws InterruptedException {
        JavaFxTestSupport.runOnFxThread(() -> {
            for (String navigationId : KNOWN_NAVIGATION_IDS) {
                assertIconRenders(navigationId);
            }
        });
    }

    @Test
    void productionIconHasVisibleFactoryGeometry() throws InterruptedException {
        JavaFxTestSupport.runOnFxThread(() -> {
            SVGPath icon = createRenderedIcon(ShellNavigationIcons.NAV_PRODUCTION);
            assertTrue(icon.getBoundsInLocal().getWidth() >= 18, "production icon width");
            assertTrue(icon.getBoundsInLocal().getHeight() >= 18, "production icon height");
        });
    }

    @Test
    void unknownNavigationIdUsesFallbackWithNonZeroBounds() throws InterruptedException {
        JavaFxTestSupport.runOnFxThread(() -> {
            SVGPath icon = createRenderedIcon("future.nav.unknown");
            assertFalse(ShellNavigationIcons.hasIcon("future.nav.unknown"));
            assertNotNull(icon.getContent());
            assertFalse(icon.getContent().isBlank());
            assertTrue(icon.getBoundsInLocal().getWidth() > 0, "fallback width");
            assertTrue(icon.getBoundsInLocal().getHeight() > 0, "fallback height");
        });
    }

    private static void assertIconRenders(String navigationId) {
        SVGPath icon = createRenderedIcon(navigationId);
        assertNotNull(icon.getContent(), navigationId + " content");
        assertFalse(icon.getContent().isBlank(), navigationId + " content blank");
        assertTrue(icon.getBoundsInLocal().getWidth() > 0, navigationId + " width");
        assertTrue(icon.getBoundsInLocal().getHeight() > 0, navigationId + " height");
    }

    private static SVGPath createRenderedIcon(String navigationId) {
        SVGPath icon = new SVGPath();
        icon.getStyleClass().add("main-window-nav-icon");
        icon.setScaleX(0.75);
        icon.setScaleY(0.75);
        ShellNavigationIcons.apply(icon, navigationId);

        StackPane container = new StackPane(icon);
        container.getStyleClass().add("main-window-nav-icon-container");
        container.setPrefSize(20, 20);
        Scene scene = new Scene(container, 40, 40);
        container.applyCss();
        container.layout();
        return icon;
    }
}
