package com.tmp.ui.shell;

import javafx.application.Application;

public final class JavaFxShellLauncher {

    private static Runnable onStopCallback;

    private JavaFxShellLauncher() {
    }

    public static void launch(Runnable onStopCallback) {
        JavaFxShellLauncher.onStopCallback = onStopCallback;
        Application.launch(JavaFxShellApplication.class);
    }

    static Runnable onStopCallback() {
        return onStopCallback;
    }
}
