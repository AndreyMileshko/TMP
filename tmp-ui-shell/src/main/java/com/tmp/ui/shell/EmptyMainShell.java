package com.tmp.ui.shell;

import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public final class EmptyMainShell {

    public static final String WINDOW_TITLE = "TOP Manufacturing Platform";

    private EmptyMainShell() {
    }

    public static void attach(Stage stage) {
        stage.setTitle(WINDOW_TITLE);
        stage.setScene(new javafx.scene.Scene(createRoot(), 960, 640));
        stage.show();
    }

    public static Parent createRoot() {
        return new StackPane();
    }
}
