package com.tmp.ui.shell;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaFxShellSmokeTest {

    @BeforeAll
    static void initJavaFx() throws InterruptedException {
        CountDownLatch startupLatch = new CountDownLatch(1);
        Platform.startup(startupLatch::countDown);
        assertTrue(startupLatch.await(10, TimeUnit.SECONDS), "JavaFX platform must start for UI shell tests");
    }

    @Test
    void emptyShellDefinesWindowTitle() {
        assertEquals("TOP Manufacturing Platform", EmptyMainShell.WINDOW_TITLE);
    }

    @Test
    void createsEmptyRootOnFxThread() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Parent> root = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                root.set(EmptyMainShell.createRoot());
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "FX thread operation must complete");
        if (error.get() != null) {
            throw new AssertionError("Empty shell root creation failed", error.get());
        }
        assertNotNull(root.get(), "Empty shell root must be created");
    }
}
