package com.tmp.ui.shell.screen.main;

import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.navigation.NavigationService;
import com.tmp.ui.shell.navigation.ShellNavEntry;
import com.tmp.ui.shell.navigation.ShellNavigationCatalogue;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Parent;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"}, justification = "JavaFX ViewModel/Controller intentionally expose observable properties and retain ViewModel for FXML wiring")
/**
 * Main window ViewModel: permission-filtered navigation, content swap, logout.
 */
public final class MainWindowViewModel {

    private final ShellNavigationCatalogue navigationCatalogue;
    private final AuthorizationService authorizationService;
    private final AuthenticationService authenticationService;
    private final NavigationService navigationService;
    private final ObservableList<NavigationItem> navigationItems = FXCollections.observableArrayList();
    private final ObjectProperty<Parent> content = new SimpleObjectProperty<>();
    private Runnable afterLogout = () -> {
    };
    private Consumer<String> onAccessDenied = message -> {
    };

    public MainWindowViewModel(
            ShellNavigationCatalogue navigationCatalogue,
            AuthorizationService authorizationService,
            AuthenticationService authenticationService,
            NavigationService navigationService) {
        this.navigationCatalogue = Objects.requireNonNull(navigationCatalogue, "navigationCatalogue");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService");
        this.authenticationService = Objects.requireNonNull(authenticationService, "authenticationService");
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        refreshNavigation();
    }

    public void setAfterLogout(Runnable afterLogout) {
        this.afterLogout = Objects.requireNonNull(afterLogout, "afterLogout");
    }

    public void setOnAccessDenied(Consumer<String> onAccessDenied) {
        this.onAccessDenied = Objects.requireNonNull(onAccessDenied, "onAccessDenied");
    }

    public void showAccessDenied(String message) {
        onAccessDenied.accept(message);
    }

    public ObservableList<NavigationItem> navigationItems() {
        return navigationItems;
    }

    public ObjectProperty<Parent> contentProperty() {
        return content;
    }

    public void refreshNavigation() {
        List<NavigationItem> visible = navigationCatalogue.entries().stream()
                .sorted(Comparator.comparingInt(ShellNavEntry::order))
                .filter(this::isVisible)
                .map(item -> new NavigationItem(item.navigationId(), item.displayName(), item.viewId()))
                .toList();
        navigationItems.setAll(visible);
    }

    public void selectNavigation(String navigationId) {
        Objects.requireNonNull(navigationId, "navigationId");
        Optional<NavigationItem> selected = navigationItems.stream()
                .filter(item -> item.navigationId().equals(navigationId))
                .findFirst();
        if (selected.isEmpty()) {
            return;
        }
        try {
            content.set(navigationService.load(selected.get().viewId()));
        } catch (IllegalArgumentException unknownScreen) {
            content.set(null);
        }
    }

    public void logout() {
        authenticationService.logout();
        afterLogout.run();
    }

    private boolean isVisible(ShellNavEntry item) {
        for (String required : item.requiredPermissionIds()) {
            if (!authorizationService.hasPermission(PermissionId.of(required))) {
                return false;
            }
        }
        return true;
    }
}
