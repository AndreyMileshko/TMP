package com.tmp.ui.shell;

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
    }
}
