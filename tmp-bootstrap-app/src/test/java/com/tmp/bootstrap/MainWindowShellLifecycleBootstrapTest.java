package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.Login;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.navigation.NavigationService;
import com.tmp.ui.shell.screen.main.MainWindowViewModel;
import com.tmp.ui.shell.screen.main.NavigationItem;
import com.tmp.ui.shell.theme.TmpTheme;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Production wiring regression: singleton MainWindowViewModel is created before login,
 * then must refresh shell state when MainWindow opens after authentication.
 */
@SpringBootTest(properties = "tmp.capability.sample.diagnostic=false")
@ActiveProfiles("test")
class MainWindowShellLifecycleBootstrapTest extends AbstractBootstrapPostgresSpringTest {

    private static final char[] ADMIN_PASSWORD = "test-admin-password".toCharArray();

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private MainWindowViewModel mainWindowViewModel;

    @Autowired
    private NavigationService navigationService;

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException alreadyStarted) {
            // Toolkit already initialized.
        }
        Platform.setImplicitExit(false);
    }

    @BeforeEach
    void clearSession() {
        authenticationService.logout();
        mainWindowViewModel.refreshShellState();
    }

    @Test
    void mainWindowShowsAdminAndNavigationAfterLoginLifecycle() throws Exception {
        assertEquals("—", mainWindowViewModel.currentUserLoginProperty().get());

        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Parent root = navigationService.load(UiShellScreens.MAIN_SCREEN_ID);
                Scene scene = new Scene(root, 1280, 800);
                TmpTheme.apply(scene);
                TmpTheme.addStylesheet(scene, "com/tmp/ui/shell/screen/main/MainWindow.css");
                root.applyCss();
                root.layout();

                assertEquals("admin", mainWindowViewModel.currentUserLoginProperty().get());
                assertEquals("A", mainWindowViewModel.currentUserInitialProperty().get());
                assertEquals("admin", ((Label) root.lookup("#userLoginLabel")).getText());
                assertEquals("A", ((Label) root.lookup("#userAvatarLabel")).getText());
                assertFalse(mainWindowViewModel.navigationItems().isEmpty());

                @SuppressWarnings("unchecked")
                ListView<NavigationItem> navigationList =
                        (ListView<NavigationItem>) root.lookup("#navigationList");
                assertFalse(navigationList.getItems().isEmpty());
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertEquals(true, latch.await(15, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError("MainWindow shell lifecycle failed", failure.get());
        }
    }

    @Test
    void adminNavigationEntriesLoadContentScreens() throws Exception {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Parent root = navigationService.load(UiShellScreens.MAIN_SCREEN_ID);
                Scene scene = new Scene(root, 1280, 800);
                TmpTheme.apply(scene);
                TmpTheme.addStylesheet(scene, "com/tmp/ui/shell/screen/main/MainWindow.css");
                root.applyCss();
                root.layout();

                StackPane contentArea = (StackPane) root.lookup("#contentArea");
                for (NavigationItem item : mainWindowViewModel.navigationItems()) {
                    mainWindowViewModel.selectNavigation(item.navigationId());
                    assertNotNull(
                            mainWindowViewModel.contentProperty().get(),
                            "content must load for " + item.navigationId());
                    contentArea.getChildren().clear();
                    contentArea.getChildren().add(mainWindowViewModel.contentProperty().get());
                }
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertEquals(true, latch.await(30, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError("navigation content smoke failed", failure.get());
        }
    }
}
