package com.tmp.ui.shell.screen.useradmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.PermissionSummary;
import com.tmp.security.api.PasswordResetResult;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.RoleSummary;
import com.tmp.security.api.UserCreationResult;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserSummary;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.ui.shell.screen.useradmin.UserAdministrationViewModel.UserDetailsSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserAdministrationViewModelTest {

    private static final Instant TS = Instant.parse("2026-07-23T04:00:00Z");
    private static final RoleId ROLE_A = RoleId.of(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final RoleId ROLE_B = RoleId.of(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    private static final PermissionId PERM_1 = PermissionId.of("warehouse.stock.view");
    private static final PermissionId PERM_2 = PermissionId.of("order.order.view");

    @Test
    void refreshLoadsUsers() {
        FakeUsers service = new FakeUsers();
        service.users.add(summary("a", "Alice"));
        UserAdministrationViewModel viewModel =
                new UserAdministrationViewModel(service, new FakeRoles(), new FakeAuthz(allUserPermissions()));
        viewModel.refresh();
        assertEquals(1, viewModel.userList().size());
        assertEquals("a", viewModel.userList().get(0).login().value());
    }

    @Test
    void refreshShowsAssignedRoleNamesInList() {
        FakeUsers users = new FakeUsers();
        UserSummary eugene = summary("Евгений", "Skorik");
        users.users.add(eugene);
        FakeRoles roles = new FakeRoles();
        roles.roleCatalogue.add(role("Кладовщик", ROLE_A, Set.of(PERM_1)));
        roles.roleCatalogue.add(role("Менеджер", ROLE_B, Set.of(PERM_2)));
        roles.assignments.put(eugene.id(), Set.of(ROLE_A));

        UserAdministrationViewModel viewModel =
                new UserAdministrationViewModel(users, roles, new FakeAuthz(allAdminPermissions()));
        viewModel.refresh();

        assertEquals("Кладовщик", viewModel.rolesLabelFor(eugene));
    }

    @Test
    void openUserDetailsUnionsEffectivePermissionsFromAllRoles() {
        FakeUsers users = new FakeUsers();
        UserSummary user = summary("eugene", "Skorik");
        users.users.add(user);
        FakeRoles roles = new FakeRoles();
        roles.roleCatalogue.add(role("Role A", ROLE_A, Set.of(PERM_1)));
        roles.roleCatalogue.add(role("Role B", ROLE_B, Set.of(PERM_2)));
        roles.assignments.put(user.id(), Set.of(ROLE_A, ROLE_B));
        roles.permissionCatalogue.add(new PermissionSummary(PERM_1, "Просмотр склада", "", true));
        roles.permissionCatalogue.add(new PermissionSummary(PERM_2, "Просмотр", "", true));

        UserAdministrationViewModel viewModel =
                new UserAdministrationViewModel(users, roles, new FakeAuthz(allAdminPermissions()));
        viewModel.refresh();

        Optional<UserDetailsSnapshot> details = viewModel.openUserDetails(user);
        assertTrue(details.isPresent());
        Set<PermissionId> granted = new HashSet<>();
        details.get().permissionGroups().forEach(group -> group.permissions().forEach(item -> {
            if (item.granted()) {
                granted.add(item.permissionId());
            }
        }));
        assertEquals(Set.of(PERM_1, PERM_2), granted);
        assertEquals(2, details.get().roles().stream().filter(r -> r.assigned()).count());
    }

    @Test
    void openUserDetailsHidesRevokedPermission() {
        FakeUsers users = new FakeUsers();
        UserSummary user = summary("eugene", "Skorik");
        users.users.add(user);
        FakeRoles roles = new FakeRoles();
        roles.roleCatalogue.add(role("Role A", ROLE_A, Set.of(PERM_1, PERM_2)));
        roles.assignments.put(user.id(), Set.of(ROLE_A));
        roles.revokedOverrides.put(user.id(), new HashSet<>(Set.of(PERM_2)));
        roles.permissionCatalogue.add(new PermissionSummary(PERM_1, "Просмотр склада", "", true));
        roles.permissionCatalogue.add(new PermissionSummary(PERM_2, "Перемещение", "", true));

        UserAdministrationViewModel viewModel =
                new UserAdministrationViewModel(users, roles, new FakeAuthz(allAdminPermissions()));
        viewModel.refresh();

        Optional<UserDetailsSnapshot> details = viewModel.openUserDetails(user);
        assertTrue(details.isPresent());
        Map<PermissionId, Boolean> byId = new HashMap<>();
        details.get().permissionGroups().forEach(group -> group.permissions().forEach(item ->
                byId.put(item.permissionId(), item.granted())));
        assertEquals(Boolean.TRUE, byId.get(PERM_1));
        assertEquals(Boolean.FALSE, byId.get(PERM_2));
    }

    @Test
    void applyRoleAssignmentsUsesExistingAssignRevoke() {
        FakeUsers users = new FakeUsers();
        UserSummary user = summary("eugene", "Skorik");
        users.users.add(user);
        FakeRoles roles = new FakeRoles();
        roles.roleCatalogue.add(role("Кладовщик", ROLE_A, Set.of(PERM_1)));
        roles.roleCatalogue.add(role("Менеджер", ROLE_B, Set.of(PERM_2)));
        roles.assignments.put(user.id(), new HashSet<>(Set.of(ROLE_A)));

        UserAdministrationViewModel viewModel =
                new UserAdministrationViewModel(users, roles, new FakeAuthz(allAdminPermissions()));
        viewModel.refresh();

        assertTrue(viewModel.applyRoleAssignments(user, Set.of(ROLE_B)));
        assertEquals(Set.of(ROLE_B), roles.assignments.get(user.id()));
        assertEquals(1, roles.assignCalls);
        assertEquals(1, roles.revokeCalls);
        assertEquals("Менеджер", viewModel.rolesLabelFor(user));
    }

    @Test
    void createDelegatesWithoutPassword() {
        FakeUsers service = new FakeUsers();
        UserAdministrationViewModel viewModel =
                new UserAdministrationViewModel(service, new FakeRoles(), new FakeAuthz(allUserPermissions()));
        viewModel.createUser("bob", "Bob");
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
                new UserAdministrationViewModel(service, new FakeRoles(), new FakeAuthz(Set.of()));
        viewModel.refresh();
        assertTrue(viewModel.errorMessageProperty().get().contains("Access denied"));
        assertFalse(viewModel.errorMessageProperty().get().contains("at "));
        assertFalse(viewModel.canCreateProperty().get());
    }

    @Test
    void showDeletedToggleFiltersDeletedUsers() {
        FakeUsers service = new FakeUsers();
        service.users.add(summary("active", "Active"));
        service.users.add(withStatus(summary("deleted", "Deleted"), "DELETED"));
        UserAdministrationViewModel viewModel =
                new UserAdministrationViewModel(service, new FakeRoles(), new FakeAuthz(allUserPermissions()));
        viewModel.refresh();

        assertFalse(viewModel.showDeletedProperty().get());
        assertEquals(2, viewModel.userList().size());
        assertEquals(1, viewModel.filteredUserList().size());
        assertEquals("active", viewModel.filteredUserList().get(0).login().value());

        viewModel.showDeletedProperty().set(true);
        assertEquals(2, viewModel.filteredUserList().size());

        viewModel.showDeletedProperty().set(false);
        assertEquals(1, viewModel.filteredUserList().size());
    }

    @Test
    void softDeleteHidesUserWhenShowDeletedOff() {
        FakeUsers service = new FakeUsers();
        UserSummary active = summary("active", "Active");
        service.users.add(active);
        UserAdministrationViewModel viewModel =
                new UserAdministrationViewModel(service, new FakeRoles(), new FakeAuthz(allUserPermissions()));
        viewModel.refresh();
        assertEquals(1, viewModel.filteredUserList().size());

        viewModel.deleteUser(active);
        assertEquals(1, viewModel.userList().size());
        assertEquals("DELETED", viewModel.userList().get(0).status());
        assertEquals(0, viewModel.filteredUserList().size());

        viewModel.showDeletedProperty().set(true);
        assertEquals(1, viewModel.filteredUserList().size());
        assertEquals("DELETED", viewModel.filteredUserList().get(0).status());
    }

    @Test
    void deletedUserCannotBeEdited() {
        UserAdministrationViewModel viewModel =
                new UserAdministrationViewModel(new FakeUsers(), new FakeRoles(), new FakeAuthz(allUserPermissions()));
        UserSummary deleted = summary("gone", "Gone");
        deleted = new UserSummary(
                deleted.id(),
                deleted.login(),
                deleted.displayName(),
                "DELETED",
                deleted.version(),
                deleted.createdAt(),
                deleted.updatedAt());
        assertFalse(viewModel.canEdit(deleted));
        assertFalse(viewModel.canDeleteUser(deleted));
        assertFalse(viewModel.canReset(deleted));
    }

    private static Set<PermissionId> allUserPermissions() {
        return Set.of(
                SecurityPermissions.USERS_VIEW,
                SecurityPermissions.USERS_CREATE,
                SecurityPermissions.USERS_UPDATE,
                SecurityPermissions.USERS_DELETE,
                SecurityPermissions.USERS_RESET_PASSWORD);
    }

    private static Set<PermissionId> allAdminPermissions() {
        Set<PermissionId> permissions = new HashSet<>(allUserPermissions());
        permissions.add(SecurityPermissions.ROLES_VIEW);
        permissions.add(SecurityPermissions.ROLES_ASSIGN);
        return permissions;
    }

    private static UserSummary withStatus(UserSummary user, String status) {
        return new UserSummary(
                user.id(),
                user.login(),
                user.displayName(),
                status,
                user.version(),
                user.createdAt(),
                user.updatedAt());
    }

    private static UserSummary summary(String login, String name) {
        return new UserSummary(
                UserId.generate(),
                Login.of(login),
                DisplayName.of(name),
                "ACTIVE",
                0L,
                TS,
                TS);
    }

    private static RoleSummary role(String name, RoleId id, Set<PermissionId> permissions) {
        return new RoleSummary(id, name, "", permissions, 0L, TS, TS);
    }

    private static class FakeUsers implements UserAdministrationService {
        private final List<UserSummary> users = new ArrayList<>();

        @Override
        public UserCreationResult createUser(Login login, DisplayName displayName) {
            UserSummary created = summary(login.value(), displayName.value());
            users.add(created);
            return new UserCreationResult(created, "TEST-CODE");
        }

        @Override
        public UserSummary updateUser(UserId userId, Login login, DisplayName newDisplayName) {
            return users.stream().filter(u -> u.id().equals(userId)).findFirst().orElseThrow();
        }

        @Override
        public UserSummary deleteUser(UserId userId) {
            UserSummary found = users.stream().filter(u -> u.id().equals(userId)).findFirst().orElseThrow();
            UserSummary deleted = withStatus(found, "DELETED");
            users.set(users.indexOf(found), deleted);
            return deleted;
        }

        @Override
        public List<UserSummary> listUsers(int pageIndex, int pageSize, String statusFilter) {
            return List.copyOf(users);
        }

        @Override
        public List<UserSummary> searchUsers(String query, int limit) {
            String normalized = query == null ? "" : query.trim().toLowerCase();
            if (normalized.isEmpty() || limit < 1) {
                return List.of();
            }
            return users.stream()
                    .filter(u -> "ACTIVE".equals(u.status()))
                    .filter(u -> u.login().value().toLowerCase().contains(normalized)
                            || u.displayName().value().toLowerCase().contains(normalized))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void changeOwnPassword(char[] currentPassword, char[] newPassword) {
        }

        @Override
        public PasswordResetResult requestPasswordReset(UserId targetUserId) {
            UserSummary user =
                    users.stream().filter(u -> u.id().equals(targetUserId)).findFirst().orElseThrow();
            return new PasswordResetResult(user, "RESET-CODE");
        }
    }

    private static final class FakeRoles implements RoleAdministrationService {
        private final List<RoleSummary> roleCatalogue = new ArrayList<>();
        private final List<PermissionSummary> permissionCatalogue = new ArrayList<>();
        private final Map<UserId, Set<RoleId>> assignments = new HashMap<>();
        private final Map<UserId, Set<PermissionId>> grantedOverrides = new HashMap<>();
        private final Map<UserId, Set<PermissionId>> revokedOverrides = new HashMap<>();
        private int assignCalls;
        private int revokeCalls;

        @Override
        public RoleSummary createRole(String name, String description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RoleSummary updateRole(RoleId roleId, String name, String description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RoleSummary grantPermissionToRole(RoleId roleId, PermissionId permissionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RoleSummary revokePermissionFromRole(RoleId roleId, PermissionId permissionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RoleSummary setRolePermissions(RoleId roleId, Set<PermissionId> targetPermissions) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteRole(RoleId roleId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RoleSummary> listRoles() {
            return List.copyOf(roleCatalogue);
        }

        @Override
        public void assignRole(UserId userId, RoleId roleId) {
            assignCalls++;
            assignments.computeIfAbsent(userId, id -> new HashSet<>()).add(roleId);
        }

        @Override
        public void revokeRole(UserId userId, RoleId roleId) {
            revokeCalls++;
            Set<RoleId> assigned = assignments.get(userId);
            if (assigned != null) {
                assigned.remove(roleId);
            }
        }

        @Override
        public Set<RoleId> listRolesForUser(UserId userId) {
            return Set.copyOf(assignments.getOrDefault(userId, Set.of()));
        }

        @Override
        public Set<PermissionId> listEffectivePermissionsForUser(UserId userId) {
            Set<PermissionId> effective = new HashSet<>();
            for (RoleId roleId : listRolesForUser(userId)) {
                roleCatalogue.stream()
                        .filter(role -> role.id().equals(roleId))
                        .findFirst()
                        .ifPresent(role -> effective.addAll(role.permissionIds()));
            }
            Set<PermissionId> revoked = revokedOverrides.getOrDefault(userId, Set.of());
            effective.removeAll(revoked);
            Set<PermissionId> granted = grantedOverrides.getOrDefault(userId, Set.of());
            effective.addAll(granted);
            return Set.copyOf(effective);
        }

        @Override
        public void grantIndividualPermission(UserId userId, PermissionId permissionId) {
            grantedOverrides.computeIfAbsent(userId, id -> new HashSet<>()).add(permissionId);
            Set<PermissionId> revoked = revokedOverrides.get(userId);
            if (revoked != null) {
                revoked.remove(permissionId);
            }
        }

        @Override
        public void revokeIndividualPermission(UserId userId, PermissionId permissionId) {
            revokedOverrides.computeIfAbsent(userId, id -> new HashSet<>()).add(permissionId);
            Set<PermissionId> granted = grantedOverrides.get(userId);
            if (granted != null) {
                granted.remove(permissionId);
            }
        }

        @Override
        public void removeOverride(UserId userId, PermissionId permissionId) {
            Set<PermissionId> granted = grantedOverrides.get(userId);
            if (granted != null) {
                granted.remove(permissionId);
            }
            Set<PermissionId> revoked = revokedOverrides.get(userId);
            if (revoked != null) {
                revoked.remove(permissionId);
            }
        }

        @Override
        public List<PermissionSummary> listAllPermissionDefinitions() {
            return List.copyOf(permissionCatalogue);
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
