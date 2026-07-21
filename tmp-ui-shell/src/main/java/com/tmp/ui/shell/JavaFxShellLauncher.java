package com.tmp.ui.shell;

import javafx.application.Application;

public final class JavaFxShellLauncher {

    private static Runnable onStopCallback;
    private static String platformStatusText = "";
    private static String centerContentText = "";

    private JavaFxShellLauncher() {
    }

    public static void launch(Runnable onStopCallback) {
        launch(onStopCallback, "");
    }

    public static void launch(Runnable onStopCallback, String platformStatusText) {
        launch(onStopCallback, platformStatusText, "");
    }

    public static void launch(Runnable onStopCallback, String platformStatusText, String centerContentText) {
        JavaFxShellLauncher.onStopCallback = onStopCallback;
        JavaFxShellLauncher.platformStatusText = platformStatusText == null ? "" : platformStatusText;
        JavaFxShellLauncher.centerContentText = centerContentText == null ? "" : centerContentText;
        Application.launch(JavaFxShellApplication.class);
    }

    static Runnable onStopCallback() {
        return onStopCallback;
    }

    static String platformStatusText() {
        return platformStatusText;
    }

    static String centerContentText() {
        return centerContentText;
    }
}
