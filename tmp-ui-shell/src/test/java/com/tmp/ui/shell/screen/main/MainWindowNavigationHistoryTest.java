package com.tmp.ui.shell.screen.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.navigation.NavigationService;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import com.tmp.ui.shell.navigation.ShellHistoryEntry;
import com.tmp.ui.shell.navigation.ShellNavEntry;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MainWindowNavigationHistoryTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void backForwardAndBranching() {
        NavigationService navigation = NavigationServices.createDefault();
        register(navigation, "list");
        register(navigation, "order");
        register(navigation, "item");
        register(navigation, "other");
        MainWindowViewModel viewModel = new MainWindowViewModel(
                new MainWindowViewModelTestSupport.EmptyCatalogue(),
                new MainWindowViewModelTestSupport.AllowAllAuthz(),
                new MainWindowViewModelTestSupport.FakeAuthn(),
                navigation);
        viewModel.navigate(ShellHistoryEntry.of("list", null, () -> {}));
        viewModel.navigate(ShellHistoryEntry.of("order", null, () -> {}));
        viewModel.navigate(ShellHistoryEntry.of("item", null, () -> {}));
        viewModel.goBack();
        viewModel.goBack();
        assertTrue(viewModel.canGoForwardProperty().get());
        viewModel.goForward();
        viewModel.goForward();
        assertFalse(viewModel.canGoForwardProperty().get());
        viewModel.goBack();
        viewModel.navigate(ShellHistoryEntry.of("other", null, () -> {}));
        assertFalse(viewModel.canGoForwardProperty().get());
    }

    @Test
    void logoutClearsHistory() {
        NavigationService navigation = NavigationServices.createDefault();
        register(navigation, "list");
        register(navigation, "order");
        MainWindowViewModel viewModel = new MainWindowViewModel(
                new MainWindowViewModelTestSupport.EmptyCatalogue(),
                new MainWindowViewModelTestSupport.AllowAllAuthz(),
                new MainWindowViewModelTestSupport.FakeAuthn(),
                navigation);
        viewModel.navigate(ShellHistoryEntry.of("list", null, () -> {}));
        viewModel.navigate(ShellHistoryEntry.of("order", null, () -> {}));
        viewModel.logout();
        assertFalse(viewModel.canGoBackProperty().get());
        assertFalse(viewModel.canGoForwardProperty().get());
        assertTrue(viewModel.historyForTest().current().isEmpty());
    }

    @Test
    void logoutClearsSelectedNavigationAndContent() {
        MainWindowViewModelTestSupport.SingleEntryCatalogue catalogue =
                new MainWindowViewModelTestSupport.SingleEntryCatalogue(
                        ShellNavEntry.of("nav.warehouse", "Склад", "view.warehouse", 1, List.of()));
        NavigationService navigation = NavigationServices.createDefault();
        register(navigation, "view.warehouse");
        MainWindowViewModel viewModel = new MainWindowViewModel(
                catalogue,
                new MainWindowViewModelTestSupport.AllowAllAuthz(),
                new MainWindowViewModelTestSupport.FakeAuthn(),
                navigation);
        viewModel.selectNavigation("nav.warehouse");
        assertTrue(viewModel.selectedNavigationProperty().get() != null);
        assertTrue(viewModel.contentProperty().get() != null);

        viewModel.logout();

        assertTrue(viewModel.selectedNavigationProperty().get() == null);
        assertTrue(viewModel.contentProperty().get() == null);
        assertTrue(viewModel.historyForTest().current().isEmpty());
    }

    @Test
    void refreshShellStateOnUserChangeClearsPriorSessionSelectionAndContent() {
        MainWindowViewModelTestSupport.SingleEntryCatalogue catalogue =
                new MainWindowViewModelTestSupport.SingleEntryCatalogue(
                        ShellNavEntry.of("nav.warehouse", "Склад", "view.warehouse", 1, List.of()));
        NavigationService navigation = NavigationServices.createDefault();
        register(navigation, "view.warehouse");
        MainWindowViewModelTestSupport.StatefulSessionAuthn authn =
                new MainWindowViewModelTestSupport.StatefulSessionAuthn();
        authn.setSession(sessionFor("storekeeper"));
        MainWindowViewModel viewModel = new MainWindowViewModel(
                catalogue,
                new MainWindowViewModelTestSupport.AllowAllAuthz(),
                authn,
                navigation);
        viewModel.refreshShellState();
        viewModel.selectNavigation("nav.warehouse");
        assertTrue(viewModel.contentProperty().get() != null);

        authn.setSession(sessionFor("master"));
        viewModel.refreshShellState();

        assertTrue(viewModel.selectedNavigationProperty().get() == null);
        assertTrue(viewModel.contentProperty().get() == null);
        assertTrue(viewModel.historyForTest().current().isEmpty());
    }

    @Test
    void reselectSameNavigationAfterLogoutReloadsFreshContent() {
        MainWindowViewModelTestSupport.SingleEntryCatalogue catalogue =
                new MainWindowViewModelTestSupport.SingleEntryCatalogue(
                        ShellNavEntry.of("nav.warehouse", "Склад", "view.warehouse", 1, List.of()));
        NavigationService navigation = NavigationServices.createDefault();
        register(navigation, "view.warehouse");
        MainWindowViewModel viewModel = new MainWindowViewModel(
                catalogue,
                new MainWindowViewModelTestSupport.AllowAllAuthz(),
                new MainWindowViewModelTestSupport.FakeAuthn(),
                navigation);
        viewModel.selectNavigation("nav.warehouse");
        Parent first = viewModel.contentProperty().get();
        assertTrue(first != null);

        viewModel.logout();
        viewModel.selectNavigation("nav.warehouse");
        Parent second = viewModel.contentProperty().get();
        assertTrue(second != null);
        assertTrue(first != second);
    }

    @Test
    void warehousePermissionStillEnforcedAfterLogoutLoginCycle() {
        MainWindowViewModelTestSupport.SingleEntryCatalogue catalogue =
                new MainWindowViewModelTestSupport.SingleEntryCatalogue(
                        ShellNavEntry.of(
                                "nav.warehouse",
                                "Склад",
                                UiShellScreens.WAREHOUSE_WORKSPACE_SCREEN_ID,
                                1,
                                List.of(UiShellScreens.WAREHOUSE_VIEW_PERMISSION)));
        NavigationService navigation = NavigationServices.createDefault();
        register(navigation, UiShellScreens.WAREHOUSE_WORKSPACE_SCREEN_ID);
        GrantingAuthz authz =
                new GrantingAuthz(java.util.Set.of(PermissionId.of(UiShellScreens.WAREHOUSE_VIEW_PERMISSION)));
        MainWindowViewModel viewModel = new MainWindowViewModel(
                catalogue,
                authz,
                new MainWindowViewModelTestSupport.FakeAuthn(),
                navigation);
        viewModel.refreshNavigation();
        assertEquals(1, viewModel.navigationItems().size());
        viewModel.selectNavigation("nav.warehouse");
        assertTrue(viewModel.contentProperty().get() != null);

        viewModel.logout();
        authz.denyWarehouseView();
        viewModel.refreshNavigation();
        assertEquals(0, viewModel.navigationItems().size());
        AtomicInteger denied = new AtomicInteger();
        viewModel.setOnAccessDenied(message -> denied.incrementAndGet());
        viewModel.navigate(
                ShellHistoryEntry.of(
                        UiShellScreens.WAREHOUSE_WORKSPACE_SCREEN_ID,
                        UiShellScreens.WAREHOUSE_VIEW_PERMISSION,
                        () -> {}));
        assertEquals(1, denied.get());
        assertTrue(viewModel.contentProperty().get() == null);
    }

    private static com.tmp.security.api.SessionSummary sessionFor(String login) {
        return new com.tmp.security.api.SessionSummary(
                com.tmp.security.api.SessionId.of(java.util.UUID.randomUUID()),
                com.tmp.security.api.UserId.of(java.util.UUID.randomUUID()),
                com.tmp.security.api.Login.of(login),
                java.time.Instant.parse("2026-07-23T04:00:00Z"));
    }

    @Test
    void historyCannotBypassPermission() {
        NavigationService navigation = NavigationServices.createDefault();
        register(navigation, UiShellScreens.ORDER_LIST_SCREEN_ID);
        register(navigation, UiShellScreens.ORDER_EDITOR_SCREEN_ID);
        MainWindowViewModelTestSupport.AllowAllAuthz authz = new MainWindowViewModelTestSupport.AllowAllAuthz();
        MainWindowViewModel viewModel = new MainWindowViewModel(
                new MainWindowViewModelTestSupport.EmptyCatalogue(),
                authz,
                new MainWindowViewModelTestSupport.FakeAuthn(),
                navigation);
        AtomicInteger editorRestores = new AtomicInteger();
        viewModel.navigate(
                ShellHistoryEntry.of(
                        UiShellScreens.ORDER_LIST_SCREEN_ID,
                        UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION,
                        () -> {}));
        viewModel.navigate(
                ShellHistoryEntry.of(
                        UiShellScreens.ORDER_EDITOR_SCREEN_ID,
                        UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION,
                        editorRestores::incrementAndGet));
        assertEquals(1, editorRestores.get());
        editorRestores.set(0);
        authz.denyAll();
        viewModel.goBack();
        assertEquals(0, editorRestores.get());
        assertTrue(viewModel.historyForTest().current().isPresent());
        assertEquals(
                UiShellScreens.ORDER_EDITOR_SCREEN_ID,
                viewModel.historyForTest().current().orElseThrow().screenId());
    }

    @Test
    void itemCreateRouteDeniedForViewOnlyUser() {
        NavigationService navigation = NavigationServices.createDefault();
        register(navigation, UiShellScreens.ORDER_ITEM_EDITOR_SCREEN_ID);
        GrantingAuthz authz =
                new GrantingAuthz(
                        java.util.Set.of(PermissionId.of(UiShellScreens.ORDER_ITEM_VIEW_PERMISSION)));
        MainWindowViewModel viewModel =
                new MainWindowViewModel(
                        new MainWindowViewModelTestSupport.EmptyCatalogue(),
                        authz,
                        new MainWindowViewModelTestSupport.FakeAuthn(),
                        navigation);
        AtomicInteger restores = new AtomicInteger();
        AtomicInteger denied = new AtomicInteger();
        viewModel.setOnAccessDenied(message -> denied.incrementAndGet());
        viewModel.navigate(
                ShellHistoryEntry.of(
                        UiShellScreens.ORDER_ITEM_EDITOR_SCREEN_ID,
                        UiShellScreens.ORDER_ITEM_CREATE_PERMISSION,
                        restores::incrementAndGet));
        assertEquals(0, restores.get());
        assertEquals(1, denied.get());
    }

    @Test
    void itemCreateRouteAllowedWithCreatePermission() {
        NavigationService navigation = NavigationServices.createDefault();
        register(navigation, UiShellScreens.ORDER_ITEM_EDITOR_SCREEN_ID);
        GrantingAuthz authz =
                new GrantingAuthz(
                        java.util.Set.of(PermissionId.of(UiShellScreens.ORDER_ITEM_CREATE_PERMISSION)));
        MainWindowViewModel viewModel =
                new MainWindowViewModel(
                        new MainWindowViewModelTestSupport.EmptyCatalogue(),
                        authz,
                        new MainWindowViewModelTestSupport.FakeAuthn(),
                        navigation);
        AtomicInteger restores = new AtomicInteger();
        viewModel.navigate(
                ShellHistoryEntry.of(
                        UiShellScreens.ORDER_ITEM_EDITOR_SCREEN_ID,
                        UiShellScreens.ORDER_ITEM_CREATE_PERMISSION,
                        restores::incrementAndGet));
        assertEquals(1, restores.get());
        assertEquals(
                UiShellScreens.ORDER_ITEM_CREATE_PERMISSION,
                viewModel.historyForTest().current().orElseThrow().requiredPermission());
    }

    @Test
    void specificationRouteAllowedForViewWithoutCreate() {
        NavigationService navigation = NavigationServices.createDefault();
        register(navigation, UiShellScreens.ORDER_ITEM_SPECIFICATION_EDITOR_SCREEN_ID);
        GrantingAuthz authz =
                new GrantingAuthz(
                        java.util.Set.of(
                                PermissionId.of(UiShellScreens.ORDER_ITEM_VIEW_PERMISSION),
                                PermissionId.of(UiShellScreens.ORDER_SPECIFICATION_VIEW_PERMISSION)));
        MainWindowViewModel viewModel =
                new MainWindowViewModel(
                        new MainWindowViewModelTestSupport.EmptyCatalogue(),
                        authz,
                        new MainWindowViewModelTestSupport.FakeAuthn(),
                        navigation);
        AtomicInteger restores = new AtomicInteger();
        AtomicInteger denied = new AtomicInteger();
        viewModel.setOnAccessDenied(message -> denied.incrementAndGet());
        viewModel.navigate(
                ShellHistoryEntry.of(
                        UiShellScreens.ORDER_ITEM_SPECIFICATION_EDITOR_SCREEN_ID,
                        UiShellScreens.ORDER_SPECIFICATION_VIEW_PERMISSION,
                        restores::incrementAndGet));
        assertEquals(1, restores.get());
        assertEquals(0, denied.get());
    }

    private static final class GrantingAuthz implements com.tmp.security.api.AuthorizationService {
        private final java.util.Set<PermissionId> granted;
        private boolean denyWarehouseView;

        private GrantingAuthz(java.util.Set<PermissionId> granted) {
            this.granted = new java.util.HashSet<>(granted);
        }

        private void denyWarehouseView() {
            this.denyWarehouseView = true;
            granted.remove(PermissionId.of(UiShellScreens.WAREHOUSE_VIEW_PERMISSION));
        }

        @Override
        public boolean hasPermission(PermissionId permissionId) {
            if (denyWarehouseView
                    && UiShellScreens.WAREHOUSE_VIEW_PERMISSION.equals(permissionId.value())) {
                return false;
            }
            return granted.contains(permissionId);
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
        }

        @Override
        public java.util.Set<PermissionId> effectivePermissions() {
            return java.util.Set.copyOf(granted);
        }
    }

    @Test
    void sidebarSameScreenDoesNotDuplicateHistory() {
        MainWindowViewModelTestSupport.SingleEntryCatalogue catalogue =
                new MainWindowViewModelTestSupport.SingleEntryCatalogue(
                        ShellNavEntry.of("nav.orders", "Заказы", "view.orders", 1, List.of()));
        NavigationService navigation = NavigationServices.createDefault();
        register(navigation, "view.orders");
        MainWindowViewModel viewModel = new MainWindowViewModel(
                catalogue,
                new MainWindowViewModelTestSupport.AllowAllAuthz(),
                new MainWindowViewModelTestSupport.FakeAuthn(),
                navigation);
        viewModel.selectNavigation("nav.orders");
        viewModel.selectNavigation("nav.orders");
        assertFalse(viewModel.canGoBackProperty().get());
    }

    private static void register(NavigationService navigation, String screenId) {
        navigation.register(new ScreenRegistration(
                screenId,
                "fxml/fixture-screen.fxml",
                () -> new com.tmp.ui.shell.navigation.FixtureViewModel(screenId)));
    }
}
