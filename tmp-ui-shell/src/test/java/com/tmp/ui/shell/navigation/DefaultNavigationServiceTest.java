package com.tmp.ui.shell.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DefaultNavigationServiceTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void loadInjectsViewModelIntoAwareController() throws Exception {
        NavigationService navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                "fixture",
                "fxml/fixture-screen.fxml",
                () -> new FixtureViewModel("hello-vm")));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Parent> root = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                root.set(navigation.load("fixture"));
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("FXML load failed", error.get());
        }
        assertNotNull(root.get());
        Label label = (Label) root.get().lookup("#label");
        assertNotNull(label);
        assertEquals("hello-vm", label.getText());
    }

    @Test
    void duplicateScreenIdRejected() {
        NavigationService navigation = NavigationServices.createDefault();
        ScreenRegistration registration = new ScreenRegistration(
                "dup", "fxml/fixture-screen.fxml", () -> new FixtureViewModel("a"));
        navigation.register(registration);
        assertThrows(IllegalStateException.class, () -> navigation.register(registration));
    }
}
