package com.tmp.ui.shell.navigation;

import javafx.scene.Parent;

/**
 * Screen registry and FXML loader for the desktop shell.
 */
public interface NavigationService {

    void register(ScreenRegistration registration);

    Parent load(String screenId);
}
