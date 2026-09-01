package com.tmp.ui.shell;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Screen-aware window geometry for the JavaFX shell. Controllers MUST NOT manage stage size.
 */
public final class ShellWindowProfile {

    public static final double LOGIN_WIDTH = 420;
    public static final double LOGIN_HEIGHT = 420;
    public static final double LOGIN_MIN_WIDTH = 380;
    public static final double LOGIN_MIN_HEIGHT = 380;

    public static final double MAIN_WIDTH = 1280;
    public static final double MAIN_HEIGHT = 800;
    public static final double MAIN_MIN_WIDTH = 1024;
    public static final double MAIN_MIN_HEIGHT = 700;

    public enum Kind {
        COMPACT,
        DESKTOP
    }

    private ShellWindowProfile() {
    }

    public static Kind forScreenId(String screenId) {
        if (UiShellEntryPoint.LOGIN_SCREEN_ID.equals(screenId)) {
            return Kind.COMPACT;
        }
        return Kind.DESKTOP;
    }

    public static void apply(Stage stage, Kind kind) {
        if (kind == Kind.COMPACT) {
            applyCompact(stage);
            return;
        }
        applyDesktop(stage);
    }

    public static void applyCompact(Stage stage) {
        stage.setMaximized(false);
        stage.setMinWidth(LOGIN_MIN_WIDTH);
        stage.setMinHeight(LOGIN_MIN_HEIGHT);
        stage.setWidth(LOGIN_WIDTH);
        stage.setHeight(LOGIN_HEIGHT);
        centerOnScreen(stage);
    }

    public static void applyDesktop(Stage stage) {
        stage.setMaximized(false);
        stage.setMinWidth(MAIN_MIN_WIDTH);
        stage.setMinHeight(MAIN_MIN_HEIGHT);
        stage.setWidth(MAIN_WIDTH);
        stage.setHeight(MAIN_HEIGHT);
    }

    private static void centerOnScreen(Stage stage) {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMinX() + (bounds.getWidth() - stage.getWidth()) / 2);
        stage.setY(bounds.getMinY() + (bounds.getHeight() - stage.getHeight()) / 2);
    }
}
