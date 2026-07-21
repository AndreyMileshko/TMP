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
        attach(stage, platformStatusText, "");
    }

    public static void attach(Stage stage, String platformStatusText, String centerContentText) {
        stage.setTitle(WINDOW_TITLE);
        stage.setScene(new javafx.scene.Scene(createRoot(platformStatusText, centerContentText), 960, 640));
        stage.show();
    }

    public static Parent createRoot() {
        return createRoot("");
    }

    public static Parent createRoot(String platformStatusText) {
        return createRoot(platformStatusText, "");
    }

    public static Parent createRoot(String platformStatusText, String centerContentText) {
        BorderPane root = new BorderPane();
        StackPane center = new StackPane();
        if (centerContentText != null && !centerContentText.isBlank()) {
            Label contentLabel = new Label(centerContentText);
            contentLabel.getStyleClass().add("document-shell-content");
            contentLabel.setWrapText(true);
            contentLabel.setPadding(new Insets(20));
            center.getChildren().add(contentLabel);
        }
        root.setCenter(center);

        if (platformStatusText != null && !platformStatusText.isBlank()) {
            Label statusLabel = new Label(platformStatusText);
            statusLabel.getStyleClass().add("platform-status-label");
            statusLabel.setPadding(new Insets(6, 10, 6, 10));
            root.setBottom(statusLabel);
        }

        return root;
    }
}
