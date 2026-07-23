package com.tmp.ui.shell.screen.useradmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserSummary;
import com.tmp.security.api.SecurityPermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserAdministrationViewModelTest {

    @Test
    void refreshLoadsUsers() {
        FakeUsers service = new FakeUsers();
        service.users.add(summary("a", "Alice"));
        UserAdministrationViewModel viewModel =
                new UserAdministrationViewModel(service, new FakeAuthz(allUserPermissions()));
        assertEquals(1, viewModel.userList().size());
        assertEquals("a", viewModel.userList().get(0).login().value());
    }

    @Test
    void createDelegatesAndRefreshes() {
        FakeUsers service = new FakeUsers();
        UserAdministrationViewModel viewModel =
                new UserAdministrationViewModel(service, new FakeAuthz(allUserPermissions()));
        viewModel.loginInputProperty().set("bob");
        viewModel.displayNameInputProperty().set("Bob");
        viewModel.passwordInputProperty().set("secret-value");
        viewModel.createUser();
        assertEquals(1, service.users.size());
        assertEquals(1, viewModel.userList().size());
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    @Test
    void accessDeniedSurfacesMessageWithoutStackTrace() {
        FakeUsers service = new FakeUsers() {
            @Override
            public List<UserSummary> listUsers(int pageIndex, int pageSize, String statusFilter) {
                throw new AccessDeniedException("Access denied for permission: security.users.view");
            }
        };
        UserAdministrationViewModel viewModel =
                new UserAdministrationViewModel(service, new FakeAuthz(Set.of()));
        assertTrue(viewModel.errorMessageProperty().get().contains("Access denied"));
        assertFalse(viewModel.errorMessageProperty().get().contains("at "));
        assertFalse(viewModel.canCreateProperty().get());
    }

    private static Set<PermissionId> allUserPermissions() {
        return Set.of(
                SecurityPermissions.USERS_VIEW,
                SecurityPermissions.USERS_CREATE,
                SecurityPermissions.USERS_UPDATE,
                SecurityPermissions.USERS_DELETE,
                SecurityPermissions.USERS_RESET_PASSWORD);
    }

    private static UserSummary summary(String login, String name) {
        return new UserSummary(
                UserId.generate(),
                Login.of(login),
                DisplayName.of(name),
                "ACTIVE",
                0L,
                Instant.parse("2026-07-23T04:00:00Z"),
                Instant.parse("2026-07-23T04:00:00Z"));
    }

    private static class FakeUsers implements UserAdministrationService {
        private final List<UserSummary> users = new ArrayList<>();

        @Override
        public UserSummary createUser(Login login, DisplayName displayName, char[] initialPassword) {
            UserSummary created = summary(login.value(), displayName.value());
            users.add(created);
            return created;
        }

        @Override
        public UserSummary updateUser(UserId userId, DisplayName newDisplayName) {
            return users.stream().filter(u -> u.id().equals(userId)).findFirst().orElseThrow();
        }

        @Override
        public UserSummary deleteUser(UserId userId) {
            UserSummary found = users.stream().filter(u -> u.id().equals(userId)).findFirst().orElseThrow();
            users.remove(found);
            return found;
        }

        @Override
        public List<UserSummary> listUsers(int pageIndex, int pageSize, String statusFilter) {
            return List.copyOf(users);
        }

        @Override
        public void changeOwnPassword(char[] currentPassword, char[] newPassword) {
        }

        @Override
        public void resetPassword(UserId targetUserId, char[] newPassword) {
        }
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
}
