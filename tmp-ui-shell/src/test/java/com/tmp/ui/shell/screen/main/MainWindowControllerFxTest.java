package com.tmp.ui.shell.screen.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.Login;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import com.tmp.ui.shell.navigation.ShellNavEntry;
import com.tmp.ui.shell.theme.TmpTheme;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MainWindowControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void logoutButtonInvokesViewModelLogout() throws Exception {
        AtomicBoolean loggedOut = new AtomicBoolean();
        MainWindowViewModel viewModel = new MainWindowViewModel(
                new MainWindowViewModelTestSupport.EmptyCatalogue(),
                new MainWindowViewModelTestSupport.AllowAllAuthz(),
                new MainWindowViewModelTestSupport.RecordingAuthn(loggedOut),
                NavigationServices.createDefault());

        JavaFxTestSupport.runOnFxThread(() -> {
            Parent root = loadMainWindow(viewModel);
            Button logout = (Button) root.lookup("#logoutButton");
            logout.fire();
        });

        assertTrue(loggedOut.get());
    }

    @Test
    void fxmlContainsModernShellStructureWithoutLegacyStatusBar() throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThread(() -> {
            MainWindowViewModel viewModel = createViewModelWithSession("admin");
            rootRef.set(loadMainWindow(viewModel));
        });

        Parent root = rootRef.get();
        assertNotNull(root.lookup("#navigationList"));
        assertNotNull(root.lookup("#contentArea"));
        assertNotNull(root.lookup("#logoutButton"));
        assertNotNull(root.lookup("#userLoginLabel"));
        assertNotNull(root.lookup("#userAvatarLabel"));
        assertNull(root.lookup("#statusLabel"));
        assertNotNull(root.lookup(".main-window-top"));
        assertNotNull(root.lookup(".main-window-sidebar"));
        assertNotNull(root.lookup(".main-window-nav-section-title"));
    }

    @Test
    void currentUserLoginAndInitialAreBoundFromViewModel() throws Exception {
        AtomicReference<String> loginText = new AtomicReference<>();
        AtomicReference<String> avatarText = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThread(() -> {
            MainWindowViewModel viewModel = createViewModelWithSession("admin");
            Parent root = loadMainWindow(viewModel);
            loginText.set(((Label) root.lookup("#userLoginLabel")).getText());
            avatarText.set(((Label) root.lookup("#userAvatarLabel")).getText());
        });

        assertEquals("admin", loginText.get());
        assertEquals("A", avatarText.get());
    }

    @Test
    void navigationSelectionLoadsContentIntoContentArea() throws Exception {
        AtomicReference<Integer> childCount = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThread(() -> {
            MainWindowViewModel viewModel = createViewModelWithNavEntry();
            Parent root = loadMainWindow(viewModel);
            ListView<?> navigationList = (ListView<?>) root.lookup("#navigationList");
            navigationList.getSelectionModel().select(0);
            StackPane contentArea = (StackPane) root.lookup("#contentArea");
            childCount.set(contentArea.getChildren().size());
        });

        assertEquals(1, childCount.get());
    }

    @Test
    void layoutAtRepresentativeSizesDoesNotThrow() throws Exception {
        layoutAtSize(1024, 700);
        layoutAtSize(1600, 900);
    }

    private static void layoutAtSize(double width, double height) throws Exception {
        AtomicReference<Double> contentWidth = new AtomicReference<>();
        AtomicReference<Double> contentHeight = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThread(() -> {
            MainWindowViewModel viewModel = createViewModelWithSession("admin");
            Parent root = loadMainWindowRoot(viewModel);
            Stage stage = new Stage();
            Scene scene = new Scene(root, width, height);
            TmpTheme.apply(scene);
            TmpTheme.addStylesheet(scene, "com/tmp/ui/shell/screen/main/MainWindow.css");
            stage.setScene(scene);
            root.applyCss();
            root.layout();

            StackPane contentArea = (StackPane) root.lookup("#contentArea");
            assertTrue(root.lookup(".main-window-top").isVisible());
            assertTrue(((Button) root.lookup("#logoutButton")).isVisible());
            assertTrue(contentArea.getWidth() > 0);
            assertTrue(contentArea.getHeight() > 0);
            contentWidth.set(contentArea.getWidth());
            contentHeight.set(contentArea.getHeight());
            stage.close();
        });

        assertTrue(contentWidth.get() > 0);
        assertTrue(contentHeight.get() > 0);
    }

    private static MainWindowViewModel createViewModelWithSession(String login) {
        SessionSummary session = new SessionSummary(
                SessionId.of(UUID.randomUUID()),
                UserId.of(UUID.randomUUID()),
                Login.of(login),
                Instant.parse("2026-07-23T04:00:00Z"));
        return new MainWindowViewModel(
                new MainWindowViewModelTestSupport.EmptyCatalogue(),
                new MainWindowViewModelTestSupport.AllowAllAuthz(),
                new MainWindowViewModelTestSupport.SessionAuthn(session),
                NavigationServices.createDefault());
    }

    private static MainWindowViewModel createViewModelWithNavEntry() {
        MainWindowViewModelTestSupport.SingleEntryCatalogue catalogue =
                new MainWindowViewModelTestSupport.SingleEntryCatalogue(
                        ShellNavEntry.of("nav.test", "Test", "view.test", 1, List.of()));
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                "view.test",
                "fxml/fixture-screen.fxml",
                () -> new com.tmp.ui.shell.navigation.FixtureViewModel("loaded")));
        SessionSummary session = new SessionSummary(
                SessionId.of(UUID.randomUUID()),
                UserId.of(UUID.randomUUID()),
                Login.of("admin"),
                Instant.parse("2026-07-23T04:00:00Z"));
        return new MainWindowViewModel(
                catalogue,
                new MainWindowViewModelTestSupport.AllowAllAuthz(),
                new MainWindowViewModelTestSupport.SessionAuthn(session),
                navigation);
    }

    private static Parent loadMainWindow(MainWindowViewModel viewModel) {
        Parent root = loadMainWindowRoot(viewModel);
        Scene scene = new Scene(root, 1280, 800);
        TmpTheme.apply(scene);
        TmpTheme.addStylesheet(scene, "com/tmp/ui/shell/screen/main/MainWindow.css");
        root.applyCss();
        root.layout();
        return root;
    }

    private static Parent loadMainWindowRoot(MainWindowViewModel viewModel) {
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                "main",
                "com/tmp/ui/shell/screen/main/MainWindow.fxml",
                () -> viewModel));
        return navigation.load("main");
    }
}
