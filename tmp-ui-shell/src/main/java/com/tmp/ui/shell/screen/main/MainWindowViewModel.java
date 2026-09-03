package com.tmp.ui.shell.screen.main;

import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.navigation.NavigationService;
import com.tmp.ui.shell.navigation.ShellHistoryEntry;
import com.tmp.ui.shell.navigation.ShellNavEntry;
import com.tmp.ui.shell.navigation.ShellNavigationCatalogue;
import com.tmp.ui.shell.navigation.ShellNavigationHistory;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Parent;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"}, justification = "JavaFX ViewModel/Controller intentionally expose observable properties and retain ViewModel for FXML wiring")
/**
 * Main window ViewModel: permission-filtered navigation, content swap, session history, logout.
 */
public final class MainWindowViewModel {

    private final ShellNavigationCatalogue navigationCatalogue;
    private final AuthorizationService authorizationService;
    private final AuthenticationService authenticationService;
    private final NavigationService navigationService;
    private final ShellNavigationHistory history = new ShellNavigationHistory();
    private final ObservableList<NavigationItem> navigationItems = FXCollections.observableArrayList();
    private final ObjectProperty<Parent> content = new SimpleObjectProperty<>();
    private final StringProperty currentUserLogin = new SimpleStringProperty("—");
    private final StringProperty currentUserInitial = new SimpleStringProperty("—");
    private final BooleanProperty canGoBack = new SimpleBooleanProperty(false);
    private final BooleanProperty canGoForward = new SimpleBooleanProperty(false);
    private Runnable afterLogout = () -> {
    };
    private Consumer<String> onAccessDenied = message -> {
    };
    private Consumer<String> onSidebarScreen = screenId -> {
    };
    private UserId lastUserId;

    public MainWindowViewModel(
            ShellNavigationCatalogue navigationCatalogue,
            AuthorizationService authorizationService,
            AuthenticationService authenticationService,
            NavigationService navigationService) {
        this.navigationCatalogue = Objects.requireNonNull(navigationCatalogue, "navigationCatalogue");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService");
        this.authenticationService = Objects.requireNonNull(authenticationService, "authenticationService");
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        refreshShellState();
    }

    public void setAfterLogout(Runnable afterLogout) {
        this.afterLogout = Objects.requireNonNull(afterLogout, "afterLogout");
    }

    public void setOnAccessDenied(Consumer<String> onAccessDenied) {
        this.onAccessDenied = Objects.requireNonNull(onAccessDenied, "onAccessDenied");
    }

    public void setOnSidebarScreen(Consumer<String> onSidebarScreen) {
        this.onSidebarScreen = Objects.requireNonNull(onSidebarScreen, "onSidebarScreen");
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

    public StringProperty currentUserLoginProperty() {
        return currentUserLogin;
    }

    public StringProperty currentUserInitialProperty() {
        return currentUserInitial;
    }

    public BooleanProperty canGoBackProperty() {
        return canGoBack;
    }

    public BooleanProperty canGoForwardProperty() {
        return canGoForward;
    }

    /** Refreshes user presentation and permission-filtered navigation for the current session. */
    public void refreshShellState() {
        UserId current = authenticationService.currentSession().map(SessionSummary::userId).orElse(null);
        if (!Objects.equals(current, lastUserId)) {
            history.clear();
            lastUserId = current;
            updateHistoryFlags();
        }
        refreshCurrentUser();
        refreshNavigation();
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
        String viewId = selected.get().viewId();
        onSidebarScreen.accept(viewId);
        Optional<ShellHistoryEntry> current = history.current();
        if (current.isPresent() && viewId.equals(current.get().screenId())) {
            apply(current.get());
            return;
        }
        navigate(ShellHistoryEntry.of(viewId, requiredPermission(viewId), () -> {}));
    }

    /**
     * Loads a registered screen into the main content area without pushing history.
     * Used by history restore. Prefer {@link #navigate(ShellHistoryEntry)} for user navigation.
     */
    public void showScreen(String screenId) {
        Objects.requireNonNull(screenId, "screenId");
        try {
            content.set(navigationService.load(screenId));
        } catch (IllegalArgumentException unknownScreen) {
            content.set(null);
        }
    }

    public void navigate(ShellHistoryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (!isAllowed(entry)) {
            onAccessDenied.accept("Недостаточно прав для открытия экрана.");
            return;
        }
        history.navigate(entry);
        apply(entry);
    }

    public void replaceCurrent(ShellHistoryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        history.replaceCurrent(entry);
        updateHistoryFlags();
    }

    public void goBack() {
        history.goBack(this::isAllowed).ifPresentOrElse(this::apply, this::updateHistoryFlags);
    }

    public void goForward() {
        history.goForward(this::isAllowed).ifPresentOrElse(this::apply, this::updateHistoryFlags);
    }

    public void logout() {
        history.clear();
        lastUserId = null;
        updateHistoryFlags();
        authenticationService.logout();
        afterLogout.run();
    }

    ShellNavigationHistory historyForTest() {
        return history;
    }

    private void apply(ShellHistoryEntry entry) {
        entry.restore().run();
        showScreen(entry.screenId());
        updateHistoryFlags();
    }

    private void updateHistoryFlags() {
        canGoBack.set(history.canGoBack());
        canGoForward.set(history.canGoForward());
    }

    private boolean isAllowed(ShellHistoryEntry entry) {
        String permission = entry.requiredPermission();
        if (permission == null || permission.isBlank()) {
            return true;
        }
        return authorizationService.hasPermission(PermissionId.of(permission));
    }

    static String requiredPermission(String screenId) {
        if (screenId == null) {
            return null;
        }
        return switch (screenId) {
            case UiShellScreens.ORDER_LIST_SCREEN_ID, UiShellScreens.ORDER_EDITOR_SCREEN_ID ->
                    UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION;
            case UiShellScreens.ORDER_ITEM_LIST_SCREEN_ID, UiShellScreens.ORDER_ITEM_EDITOR_SCREEN_ID ->
                    UiShellScreens.ORDER_ITEM_VIEW_PERMISSION;
            case UiShellScreens.ORDER_ITEM_SPECIFICATION_EDITOR_SCREEN_ID ->
                    UiShellScreens.ORDER_SPECIFICATION_VIEW_PERMISSION;
            case UiShellScreens.ORDER_IMPORT_SCREEN_ID -> UiShellScreens.ORDER_CREATE_PERMISSION;
            case UiShellScreens.WAREHOUSE_WORKBENCH_SCREEN_ID ->
                    UiShellScreens.WAREHOUSE_VIEW_PERMISSION;
            case UiShellScreens.PRODUCTION_WORKBENCH_SCREEN_ID ->
                    UiShellScreens.PRODUCTION_VIEW_PERMISSION;
            case UiShellScreens.USER_ADMIN_SCREEN_ID -> "security.users.view";
            case UiShellScreens.ROLE_ADMIN_SCREEN_ID -> "security.roles.view";
            case UiShellScreens.AUDIT_SCREEN_ID -> "security.audit.view";
            default -> null;
        };
    }

    private boolean isVisible(ShellNavEntry item) {
        for (String required : item.requiredPermissionIds()) {
            if (!authorizationService.hasPermission(PermissionId.of(required))) {
                return false;
            }
        }
        return true;
    }

    private void refreshCurrentUser() {
        String login = authenticationService.currentSession()
                .map(session -> session.login().value())
                .filter(value -> !value.isBlank())
                .orElse("—");
        currentUserLogin.set(login);
        if ("—".equals(login)) {
            currentUserInitial.set("—");
            return;
        }
        currentUserInitial.set(String.valueOf(Character.toUpperCase(login.charAt(0))));
    }
}
