package com.tmp.ui.shell.screen.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                "main",
                "com/tmp/ui/shell/screen/main/MainWindow.fxml",
                () -> viewModel));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Parent root = navigation.load("main");
                Stage stage = new Stage();
                stage.setScene(new Scene(root));
                Button logout = (Button) root.lookup("#logoutButton");
                logout.fire();
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Main window FX wiring failed", error.get());
        }
        assertTrue(loggedOut.get());
        assertEquals(true, loggedOut.get());
    }
}
