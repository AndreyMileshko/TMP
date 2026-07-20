package com.tmp.ui.shell;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public final class EmptyMainShell {

    public static final String WINDOW_TITLE = "TOP Manufacturing Platform";

    private EmptyMainShell() {
    }

    public static void attach(Stage stage) {
        attach(stage, "");
    }

    public static void attach(Stage stage, String platformStatusText) {
        stage.setTitle(WINDOW_TITLE);
        stage.setScene(new javafx.scene.Scene(createRoot(platformStatusText), 960, 640));
        stage.show();
    }

    public static Parent createRoot() {
        return createRoot("");
    }

    public static Parent createRoot(String platformStatusText) {
        BorderPane root = new BorderPane();
        root.setCenter(new StackPane());

        if (platformStatusText != null && !platformStatusText.isBlank()) {
            Label statusLabel = new Label(platformStatusText);
            statusLabel.getStyleClass().add("platform-status-label");
            statusLabel.setPadding(new Insets(6, 10, 6, 10));
            root.setBottom(statusLabel);
        }

        return root;
    }
}
