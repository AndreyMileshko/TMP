package com.tmp.ui.shell;

import com.tmp.ui.shell.navigation.NavigationService;
import java.util.Objects;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Swaps the JavaFX scene root by screen id. Attached once from {@link JavaFxShellApplication}.
 */
public final class SceneNavigator {

    private final NavigationService navigationService;
    private Scene scene;
    private Stage stage;

    public SceneNavigator(NavigationService navigationService) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
    }

    public void attach(Scene scene) {
        this.scene = Objects.requireNonNull(scene, "scene");
    }

    public void attachStage(Stage stage) {
        this.stage = Objects.requireNonNull(stage, "stage");
    }

    public void show(String screenId) {
        Objects.requireNonNull(screenId, "screenId");
        if (scene == null) {
            throw new IllegalStateException("SceneNavigator has not been attached to a Scene");
        }
        Parent root = navigationService.load(screenId);
        scene.setRoot(root);
        applyWindowProfile(screenId);
    }

    private void applyWindowProfile(String screenId) {
        if (stage == null) {
            return;
        }
        ShellWindowProfile.apply(stage, ShellWindowProfile.forScreenId(screenId));
    }
}
