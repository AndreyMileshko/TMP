package com.tmp.ui.shell.screen.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.navigation.NavigationService;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import com.tmp.ui.shell.navigation.ShellNavEntry;
import com.tmp.ui.shell.navigation.ShellNavigationCatalogue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MainWindowViewModelTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void filtersNavigationByRequiredPermissions() {
        FakeCatalogue catalogue = new FakeCatalogue();
        catalogue.entries.add(ShellNavEntry.of("nav.granted", "Granted", "view.granted", 1, List.of("sec.item.a")));
        catalogue.entries.add(ShellNavEntry.of("nav.denied", "Denied", "view.denied", 2, List.of("sec.item.b")));
        catalogue.entries.add(ShellNavEntry.of("nav.free", "Free", "view.free", 3, List.of()));

        FakeAuthz authz = new FakeAuthz(Set.of(PermissionId.of("sec.item.a")));
        NavigationService navigation = NavigationServices.createDefault();

        MainWindowViewModel viewModel =
                new MainWindowViewModel(catalogue, authz, new FakeAuthn(), navigation);
        assertEquals(2, viewModel.navigationItems().size());
        assertEquals("nav.granted", viewModel.navigationItems().get(0).navigationId());
        assertEquals("nav.free", viewModel.navigationItems().get(1).navigationId());
    }

    @Test
    void selectNavigationLoadsMatchingView() {
        FakeCatalogue catalogue = new FakeCatalogue();
        catalogue.entries.add(ShellNavEntry.of("nav.free", "Free", "view.free", 1, List.of()));
        NavigationService navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                "view.free",
                "fxml/fixture-screen.fxml",
                () -> new com.tmp.ui.shell.navigation.FixtureViewModel("loaded")));
        MainWindowViewModel viewModel =
                new MainWindowViewModel(catalogue, new FakeAuthz(Set.of()), new FakeAuthn(), navigation);
        viewModel.selectNavigation("nav.free");
        Parent content = viewModel.contentProperty().get();
        assertTrue(content != null);
    }

    @Test
    void logoutDelegatesAndRunsCallback() {
        AtomicBoolean loggedOut = new AtomicBoolean();
        AtomicBoolean after = new AtomicBoolean();
        FakeAuthn authn = new FakeAuthn() {
            @Override
            public void logout() {
                loggedOut.set(true);
            }
        };
        MainWindowViewModel viewModel = new MainWindowViewModel(
                new FakeCatalogue(), new FakeAuthz(Set.of()), authn, NavigationServices.createDefault());
        viewModel.setAfterLogout(() -> after.set(true));
        viewModel.logout();
        assertTrue(loggedOut.get());
        assertTrue(after.get());
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
