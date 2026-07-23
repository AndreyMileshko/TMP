package com.tmp.ui.shell;

import com.tmp.security.api.AuthenticationService;
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
        Scene scene = new Scene(root, 960, 640);
        addStylesheet(scene, "com/tmp/ui/shell/screen/login/LoginScreen.css");
        addStylesheet(scene, "com/tmp/ui/shell/screen/main/MainWindow.css");
        addStylesheet(scene, "com/tmp/ui/shell/screen/accessdenied/AccessDeniedScreen.css");
        addStylesheet(scene, "com/tmp/ui/shell/screen/useradmin/UserAdministrationScreen.css");
        entryPoint.sceneNavigator().attach(scene);
        stage.setTitle(WINDOW_TITLE);
        stage.setScene(scene);
        stage.show();
    }

    private static void addStylesheet(Scene scene, String classpathLocation) {
        var resource = JavaFxShellApplication.class.getClassLoader().getResource(classpathLocation);
        if (resource != null) {
            scene.getStylesheets().add(resource.toExternalForm());
        }
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
