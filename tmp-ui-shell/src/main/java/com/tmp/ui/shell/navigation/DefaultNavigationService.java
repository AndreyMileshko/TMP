package com.tmp.ui.shell.navigation;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/**
 * Default in-memory screen registry and FXML loader.
 */
final class DefaultNavigationService implements NavigationService {

    private final Map<String, ScreenRegistration> registrations = new ConcurrentHashMap<>();

    @Override
    public void register(ScreenRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        ScreenRegistration previous = registrations.putIfAbsent(registration.screenId(), registration);
        if (previous != null) {
            throw new IllegalStateException("Duplicate screen id: " + registration.screenId());
        }
    }

    @Override
    public Parent load(String screenId) {
        Objects.requireNonNull(screenId, "screenId");
        ScreenRegistration registration = registrations.get(screenId);
        if (registration == null) {
            throw new IllegalArgumentException("Unknown screen id: " + screenId);
        }
        URL resource = DefaultNavigationService.class.getClassLoader()
                .getResource(registration.fxmlClasspathResource());
        if (resource == null) {
            throw new IllegalStateException(
                    "FXML resource not found on classpath: " + registration.fxmlClasspathResource());
        }
        FXMLLoader loader = new FXMLLoader(resource);
        try {
            Parent root = loader.load();
            Object controller = loader.getController();
            if (controller instanceof ViewModelAware<?> aware) {
                @SuppressWarnings("unchecked")
                ViewModelAware<Object> typed = (ViewModelAware<Object>) aware;
                typed.setViewModel(registration.viewModelSupplier().get());
            }
            return root;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load FXML for screen: " + screenId, ex);
        }
    }
}
