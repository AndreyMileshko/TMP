package com.tmp.ui.shell;

import javafx.application.Application;

public final class JavaFxShellLauncher {

    private static Runnable onStopCallback;
    private static String platformStatusText = "";

    private JavaFxShellLauncher() {
    }

    public static void launch(Runnable onStopCallback) {
        launch(onStopCallback, "");
    }

    public static void launch(Runnable onStopCallback, String platformStatusText) {
        JavaFxShellLauncher.onStopCallback = onStopCallback;
        JavaFxShellLauncher.platformStatusText = platformStatusText == null ? "" : platformStatusText;
        Application.launch(JavaFxShellApplication.class);
    }

    static Runnable onStopCallback() {
        return onStopCallback;
    }

    static String platformStatusText() {
        return platformStatusText;
    }
}
