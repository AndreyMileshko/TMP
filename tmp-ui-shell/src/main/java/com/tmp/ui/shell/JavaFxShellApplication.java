package com.tmp.ui.shell;

import com.tmp.security.api.AuthenticationService;
import com.tmp.ui.shell.theme.TmpTheme;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class JavaFxShellApplication extends Application {

    public static final String WINDOW_TITLE = "TOP Manufacturing Platform";

    @Override
    public void start(Stage stage) {
        UiShellEntryPoint entryPoint = JavaFxShellLauncher.entryPoint();
        if (entryPoint == null) {
            throw new IllegalStateException("UiShellEntryPoint was not provided to JavaFxShellLauncher");
        }
        Parent root = entryPoint.navigationService().load(entryPoint.initialScreenId());
        Scene scene = new Scene(root);
        TmpTheme.apply(scene);
        TmpTheme.addStylesheet(scene, "com/tmp/ui/shell/screen/login/LoginScreen.css");
        TmpTheme.addStylesheet(scene, "com/tmp/ui/shell/screen/main/MainWindow.css");
        TmpTheme.addStylesheet(scene, "com/tmp/ui/shell/screen/accessdenied/AccessDeniedScreen.css");
        SceneNavigator sceneNavigator = entryPoint.sceneNavigator();
        sceneNavigator.attach(scene);
        sceneNavigator.attachStage(stage);
        stage.setTitle(WINDOW_TITLE);
        stage.setScene(scene);
        ShellWindowProfile.apply(stage, ShellWindowProfile.forScreenId(entryPoint.initialScreenId()));
        stage.show();
    }

    @Override
    public void stop() {
        AuthenticationService authenticationService = JavaFxShellLauncher.authenticationService();
        if (authenticationService != null && authenticationService.isAuthenticated()) {
            authenticationService.logout();
        }
        Runnable callback = JavaFxShellLauncher.onStopCallback();
        if (callback != null) {
            callback.run();
        }
    }
}
