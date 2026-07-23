package com.tmp.ui.shell.screen.roleadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.PermissionSummary;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.RoleSummary;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserSummary;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.api.RoleInUseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleAdministrationViewModelTest {

    @Test
    void createRoleDelegates() {
        FakeRoles roles = new FakeRoles();
        RoleAdministrationViewModel viewModel = new RoleAdministrationViewModel(
                roles, new EmptyUsers(), new AllowAll());
        viewModel.nameInputProperty().set("Ops");
        viewModel.descriptionInputProperty().set("ops");
        viewModel.createRole();
        assertEquals(1, roles.roles.size());
        assertEquals("Ops", roles.roles.get(0).name());
    }

    @Test
    void deleteInUseSurfacesMessage() {
        FakeRoles roles = new FakeRoles() {
            @Override
            public void deleteRole(RoleId roleId) {
                throw new RoleInUseException("Role still assigned to users");
            }
        };
        RoleSummary existing = new RoleSummary(
                RoleId.generate(), "Ops", "", Set.of(), 0L,
                Instant.parse("2026-07-23T04:00:00Z"), Instant.parse("2026-07-23T04:00:00Z"));
        roles.roles.add(existing);
        RoleAdministrationViewModel viewModel = new RoleAdministrationViewModel(
                roles, new EmptyUsers(), new AllowAll());
        viewModel.select(existing);
        viewModel.deleteSelected();
        assertTrue(viewModel.errorMessageProperty().get().contains("assigned"));
        assertEquals(1, viewModel.roleList().size());
    }

    private static class FakeRoles implements RoleAdministrationService {
        private final List<RoleSummary> roles = new ArrayList<>();

        @Override
        public RoleSummary createRole(String name, String description) {
            RoleSummary created = new RoleSummary(
                    RoleId.generate(), name, description, Set.of(), 0L,
                    Instant.parse("2026-07-23T04:00:00Z"), Instant.parse("2026-07-23T04:00:00Z"));
            roles.add(created);
            return created;
        }

        @Override
        public RoleSummary updateRole(RoleId roleId, String name, String description) {
            return roles.get(0);
        }

        @Override
        public RoleSummary grantPermissionToRole(RoleId roleId, PermissionId permissionId) {
            return roles.get(0);
        }

        @Override
        public RoleSummary revokePermissionFromRole(RoleId roleId, PermissionId permissionId) {
            return roles.get(0);
        }

        @Override
        public void deleteRole(RoleId roleId) {
            roles.removeIf(r -> r.id().equals(roleId));
        }

        @Override
        public List<RoleSummary> listRoles() {
            return List.copyOf(roles);
        }

        @Override
        public void assignRole(UserId userId, RoleId roleId) {
        }

        @Override
        public void revokeRole(UserId userId, RoleId roleId) {
        }

        @Override
        public void grantIndividualPermission(UserId userId, PermissionId permissionId) {
        }

        @Override
        public void revokeIndividualPermission(UserId userId, PermissionId permissionId) {
        }

        @Override
        public void removeOverride(UserId userId, PermissionId permissionId) {
        }

        @Override
        public List<PermissionSummary> listAllPermissionDefinitions() {
            return List.of(new PermissionSummary(
                    SecurityPermissions.ROLES_VIEW, "View roles", "", true));
        }
    }

    private static final class EmptyUsers implements UserAdministrationService {
        @Override
        public UserSummary createUser(Login login, DisplayName displayName, char[] initialPassword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserSummary updateUser(UserId userId, DisplayName newDisplayName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserSummary deleteUser(UserId userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<UserSummary> listUsers(int pageIndex, int pageSize, String statusFilter) {
            return List.of();
        }

        @Override
        public void changeOwnPassword(char[] currentPassword, char[] newPassword) {
        }

        @Override
        public void resetPassword(UserId targetUserId, char[] newPassword) {
        }
    }

    private static final class AllowAll implements AuthorizationService {
        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return true;
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of();
        }
    }
}
