package com.tmp.ui.shell;

import javafx.application.Application;
import javafx.stage.Stage;

public class JavaFxShellApplication extends Application {

    @Override
    public void start(Stage stage) {
        EmptyMainShell.attach(stage, JavaFxShellLauncher.platformStatusText());
    }

    @Override
    public void stop() {
        Runnable callback = JavaFxShellLauncher.onStopCallback();
        if (callback != null) {
            callback.run();
        }
    }
}
