package com.tmp.ui.shell;

import com.tmp.security.api.AuthenticationService;
import javafx.application.Application;

public final class JavaFxShellLauncher {

    private static Runnable onStopCallback;
    private static UiShellEntryPoint entryPoint;
    private static AuthenticationService authenticationService;

    private JavaFxShellLauncher() {
    }

    public static void launch(
            Runnable onStopCallback, UiShellEntryPoint entryPoint, AuthenticationService authenticationService) {
        JavaFxShellLauncher.onStopCallback = onStopCallback;
        JavaFxShellLauncher.entryPoint = entryPoint;
        JavaFxShellLauncher.authenticationService = authenticationService;
        Application.launch(JavaFxShellApplication.class);
    }

    public static void launch(Runnable onStopCallback, UiShellEntryPoint entryPoint) {
        launch(onStopCallback, entryPoint, null);
    }

    static Runnable onStopCallback() {
        return onStopCallback;
    }

    static UiShellEntryPoint entryPoint() {
        return entryPoint;
    }

    static AuthenticationService authenticationService() {
        return authenticationService;
    }
}
