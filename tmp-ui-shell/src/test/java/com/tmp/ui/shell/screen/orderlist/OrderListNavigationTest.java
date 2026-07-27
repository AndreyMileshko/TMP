package com.tmp.ui.shell.screen.orderlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.navigation.NavigationService;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import com.tmp.ui.shell.navigation.ShellNavEntry;
import com.tmp.ui.shell.navigation.ShellNavigationCatalogue;
import com.tmp.ui.shell.screen.main.MainWindowViewModel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrderListNavigationTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void orderNavigationVisibleWithOrderViewPermission() {
        MainWindowViewModel viewModel = mainWindowWithPermissions(
                Set.of(PermissionId.of(UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION)));
        assertEquals(1, viewModel.navigationItems().size());
        assertEquals(UiShellScreens.ORDER_LIST_NAVIGATION_ID, viewModel.navigationItems().get(0).navigationId());
        assertEquals("Заказы", viewModel.navigationItems().get(0).displayName());
        assertEquals(UiShellScreens.ORDER_LIST_SCREEN_ID, viewModel.navigationItems().get(0).viewId());
    }

    @Test
    void orderNavigationHiddenWithoutOrderViewPermission() {
        MainWindowViewModel viewModel = mainWindowWithPermissions(Set.of());
        assertTrue(viewModel.navigationItems().isEmpty());
    }

    @Test
    void selectingOrderNavigationOpensOrderListScreenId() {
        FakeCatalogue catalogue = new FakeCatalogue();
        catalogue.entries.add(orderNavEntry());
        NavigationService navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                UiShellScreens.ORDER_LIST_SCREEN_ID,
                UiShellScreens.ORDER_LIST_FXML,
                () -> new OrderListViewModel(new EmptyOrderQuery(), new FakeAuthorization())));
        MainWindowViewModel viewModel = new MainWindowViewModel(
                catalogue,
                new FakeAuthz(Set.of(PermissionId.of(UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION))),
                new FakeAuthn(),
                navigation);
        viewModel.selectNavigation(UiShellScreens.ORDER_LIST_NAVIGATION_ID);
        Parent content = viewModel.contentProperty().get();
        assertTrue(content != null);
    }

    private static MainWindowViewModel mainWindowWithPermissions(Set<PermissionId> granted) {
        FakeCatalogue catalogue = new FakeCatalogue();
        catalogue.entries.add(orderNavEntry());
        return new MainWindowViewModel(
                catalogue, new FakeAuthz(granted), new FakeAuthn(), NavigationServices.createDefault());
    }

    private static ShellNavEntry orderNavEntry() {
        return ShellNavEntry.of(
                UiShellScreens.ORDER_LIST_NAVIGATION_ID,
                "Заказы",
                UiShellScreens.ORDER_LIST_SCREEN_ID,
                40,
                List.of(UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION));
    }

    private static final class FakeAuthz implements AuthorizationService {
        private final Set<PermissionId> granted;

        private FakeAuthz(Set<PermissionId> granted) {
            this.granted = new HashSet<>(granted);
        }

        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return granted.contains(permissionId);
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.copyOf(granted);
        }
    }

    private static class FakeAuthn implements AuthenticationService {
        @Override
        public SessionSummary login(Login login, char[] password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void logout() {
        }

        @Override
        public Optional<SessionSummary> currentSession() {
            return Optional.empty();
        }

        @Override
        public boolean isAuthenticated() {
            return false;
        }
    }

    private static final class FakeCatalogue implements ShellNavigationCatalogue {
        private final List<ShellNavEntry> entries = new ArrayList<>();

        @Override
        public List<ShellNavEntry> entries() {
            return List.copyOf(entries);
        }
    }
}
