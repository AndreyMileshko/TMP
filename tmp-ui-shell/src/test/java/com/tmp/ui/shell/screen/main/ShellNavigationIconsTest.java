package com.tmp.ui.shell.screen.main;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import com.tmp.ui.shell.navigation.ShellNavEntry;
import com.tmp.ui.shell.navigation.ShellNavigationCatalogue;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.tmp.ui.shell.JavaFxTestSupport;

class ShellNavigationIconsTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void knownNavigationIdsHaveIcons() {
        assertTrue(ShellNavigationIcons.hasIcon(ShellNavigationIcons.NAV_USERS));
        assertTrue(ShellNavigationIcons.hasIcon(ShellNavigationIcons.NAV_ROLES));
        assertTrue(ShellNavigationIcons.hasIcon(ShellNavigationIcons.NAV_AUDIT));
        assertTrue(ShellNavigationIcons.hasIcon(ShellNavigationIcons.NAV_ORDERS));
        assertTrue(ShellNavigationIcons.hasIcon(ShellNavigationIcons.NAV_WAREHOUSE));
        assertTrue(ShellNavigationIcons.hasIcon(ShellNavigationIcons.NAV_PRODUCTION));
    }

    @Test
    void unknownNavigationIdUsesFallbackWithoutException() {
        SVGPath icon = new SVGPath();
        ShellNavigationIcons.apply(icon, "future.nav.unknown");
        assertFalse(ShellNavigationIcons.hasIcon("future.nav.unknown"));
        assertNotNull(icon.getContent());
        assertFalse(icon.getContent().isBlank());
    }
}
