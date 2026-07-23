package com.tmp.ui.shell.screen.accessdenied;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SecuredOperationDemo;
import com.tmp.security.api.SessionSummary;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ShellNavEntry;
import com.tmp.ui.shell.navigation.ShellNavigationCatalogue;
import com.tmp.ui.shell.screen.main.MainWindowViewModel;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Proof: hiding a nav item in the UI does not replace authorization —
 * a direct secured call for the same permission still throws.
 */
class AccessDeniedBypassPreventionTest {

    private static final PermissionId REQUIRED = PermissionId.of("security.audit.view");
    private static final String NAV_ID = "security.nav.audit";

    @Test
    void hiddenNavigationDoesNotBypassDirectAuthorizationCheck() {
        ShellNavigationCatalogue catalogue = () -> List.of(
                ShellNavEntry.of(NAV_ID, "Audit", "security.view.audit", 1, List.of(REQUIRED.value())));

        FakeAuthz authz = new FakeAuthz(Set.of()); // user lacks REQUIRED
        MainWindowViewModel mainWindow = new MainWindowViewModel(
                catalogue, authz, new NoopAuthn(), NavigationServices.createDefault());

        assertTrue(
                mainWindow.navigationItems().stream().noneMatch(item -> NAV_ID.equals(item.navigationId())),
                "navigation item must be hidden without permission");

        SecuredOperationDemo demo = new SecuredOperationDemo(authz);
        assertThrows(AccessDeniedException.class, () -> demo.performSecuredOperation(REQUIRED));
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
            if (!hasPermission(permissionId)) {
                throw new AccessDeniedException("Access denied for permission: " + permissionId.value());
            }
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.copyOf(granted);
        }
    }

    private static final class NoopAuthn implements AuthenticationService {
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
}
