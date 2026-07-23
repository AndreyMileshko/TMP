package com.tmp.ui.shell;

import com.tmp.ui.shell.navigation.NavigationService;
import java.util.Objects;

/**
 * Hand-off from Spring bootstrap into the JavaFX Application subclass.
 */
public record UiShellEntryPoint(
        NavigationService navigationService, String initialScreenId, SceneNavigator sceneNavigator) {

    public static final String LOGIN_SCREEN_ID = "login";

    public UiShellEntryPoint {
        Objects.requireNonNull(navigationService, "navigationService");
        Objects.requireNonNull(initialScreenId, "initialScreenId");
        Objects.requireNonNull(sceneNavigator, "sceneNavigator");
        if (initialScreenId.isBlank()) {
            throw new IllegalArgumentException("initialScreenId must not be blank");
        }
    }
}
