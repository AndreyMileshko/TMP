package com.tmp.ui.shell;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared JavaFX toolkit bootstrap for unit tests in this module.
 */
public final class JavaFxTestSupport {

    private JavaFxTestSupport() {
    }

    public static void ensureToolkit() {
        try {
            javafx.application.Platform.startup(() -> {
            });
        } catch (IllegalStateException alreadyStarted) {
            // Toolkit already initialized in this JVM (common when Surefire reuses the fork).
        }
        javafx.application.Platform.setImplicitExit(false);
    }

    public static void runOnFxThread(Runnable action) throws InterruptedException {
        if (javafx.application.Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        javafx.application.Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("FX action did not complete in time");
        }
        if (error.get() != null) {
            if (error.get() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(error.get());
        }
    }
}
