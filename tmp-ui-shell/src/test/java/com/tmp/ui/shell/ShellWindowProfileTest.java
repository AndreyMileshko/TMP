package com.tmp.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import com.tmp.ui.shell.navigation.FixtureViewModel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShellWindowProfileTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void loginScreenUsesCompactProfile() {
        assertEquals(ShellWindowProfile.Kind.COMPACT, ShellWindowProfile.forScreenId(UiShellEntryPoint.LOGIN_SCREEN_ID));
    }

    @Test
    void mainScreenUsesDesktopProfile() {
        assertEquals(ShellWindowProfile.Kind.DESKTOP, ShellWindowProfile.forScreenId(UiShellScreens.MAIN_SCREEN_ID));
    }

    @Test
    void compactProfileDimensionsAreBelowDesktopMinimum() {
        assertTrue(ShellWindowProfile.LOGIN_WIDTH < ShellWindowProfile.MAIN_MIN_WIDTH);
        assertTrue(ShellWindowProfile.LOGIN_HEIGHT < ShellWindowProfile.MAIN_MIN_HEIGHT);
        assertTrue(ShellWindowProfile.LOGIN_MIN_WIDTH < ShellWindowProfile.MAIN_MIN_WIDTH);
        assertTrue(ShellWindowProfile.LOGIN_MIN_HEIGHT < ShellWindowProfile.MAIN_MIN_HEIGHT);
    }

    @Test
    void sceneNavigatorAppliesWindowProfileLifecycle() throws Exception {
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                UiShellEntryPoint.LOGIN_SCREEN_ID,
                "fxml/fixture-screen.fxml",
                () -> new FixtureViewModel("login")));
        navigation.register(new ScreenRegistration(
                UiShellScreens.MAIN_SCREEN_ID,
                "fxml/fixture-screen.fxml",
                () -> new FixtureViewModel("main")));

        SceneNavigator navigator = new SceneNavigator(navigation);
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                Stage stage = new Stage();
                Scene scene = new Scene(new StackPane(new Label("seed")));
                navigator.attach(scene);
                navigator.attachStage(stage);
                stage.setScene(scene);

                navigator.show(UiShellEntryPoint.LOGIN_SCREEN_ID);
                assertFalse(stage.isMaximized());
                assertTrue(stage.getWidth() < ShellWindowProfile.MAIN_MIN_WIDTH);
                assertTrue(stage.getMinWidth() < ShellWindowProfile.MAIN_MIN_WIDTH);

                navigator.show(UiShellScreens.MAIN_SCREEN_ID);
                assertFalse(stage.isMaximized());
                assertTrue(stage.getMinWidth() >= ShellWindowProfile.MAIN_MIN_WIDTH);
                assertTrue(stage.getMinHeight() >= ShellWindowProfile.MAIN_MIN_HEIGHT);
                assertTrue(stage.getWidth() >= ShellWindowProfile.MAIN_MIN_WIDTH);

                stage.setMaximized(true);
                navigator.show(UiShellEntryPoint.LOGIN_SCREEN_ID);
                assertFalse(stage.isMaximized());
                assertTrue(stage.getWidth() < ShellWindowProfile.MAIN_MIN_WIDTH);

                stageRef.set(stage);
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
        Platform.runLater(() -> stageRef.get().close());
    }
}
