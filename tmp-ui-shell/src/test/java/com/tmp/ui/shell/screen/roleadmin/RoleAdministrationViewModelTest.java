package com.tmp.ui.shell.screen.roleadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.PermissionSummary;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.RoleSummary;
import com.tmp.security.api.PasswordResetResult;
import com.tmp.security.api.UserCreationResult;
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

    @Test
    void updateSelectedShowsExactRussianSuccessMessage() {
        FakeRoles roles = new FakeRoles();
        RoleSummary existing = new RoleSummary(
                RoleId.generate(), "Ops", "ops", Set.of(), 0L,
                Instant.parse("2026-07-23T04:00:00Z"), Instant.parse("2026-07-23T04:00:00Z"));
        roles.roles.add(existing);
        RoleAdministrationViewModel viewModel = new RoleAdministrationViewModel(
                roles, new EmptyUsers(), new AllowAll());
        viewModel.select(existing);
        viewModel.nameInputProperty().set("Ops Updated");
        viewModel.updateSelected();
        assertEquals(RoleAdministrationMessages.ROLE_UPDATED, viewModel.statusMessageProperty().get());
        assertFalse(viewModel.statusMessageProperty().get().contains("Р'"));
        assertEquals("Роль успешно изменена.", viewModel.statusMessageProperty().get());
    }

    @Test
    void selectRoleRequiredMessageIsValidUtf8Russian() {
        RoleAdministrationViewModel viewModel = new RoleAdministrationViewModel(
                new FakeRoles(), new EmptyUsers(), new AllowAll());
        viewModel.updateSelected();
        assertEquals(RoleAdministrationMessages.SELECT_ROLE, viewModel.errorMessageProperty().get());
        assertEquals("Выберите роль", viewModel.errorMessageProperty().get());
    }

    @Test
    void togglePermissionKeepsSelectedRoleIdWithoutFullListReplacement() {
        FakeRoles roles = new FakeRoles();
        RoleSummary existing = new RoleSummary(
                RoleId.generate(), "Security Administrator", "admin", Set.of(), 0L,
                Instant.parse("2026-07-23T04:00:00Z"), Instant.parse("2026-07-23T04:00:00Z"));
        roles.roles.add(existing);
        RoleAdministrationViewModel viewModel = new RoleAdministrationViewModel(
                roles, new EmptyUsers(), new AllowAll());
        viewModel.select(existing);
        int listCallsBeforeToggle = roles.listRolesCalls;

        viewModel.togglePermission(SecurityPermissions.USERS_VIEW, true);

        assertEquals(existing.id(), viewModel.selectedRoleId());
        assertTrue(viewModel.isPermissionGrantedOnSelected(SecurityPermissions.USERS_VIEW));
        assertEquals(1, viewModel.roleList().size());
        assertEquals(existing.id(), viewModel.roleList().get(0).id());
        assertEquals(listCallsBeforeToggle, roles.listRolesCalls);
    }

    private static class FakeRoles implements RoleAdministrationService {
        private final List<RoleSummary> roles = new ArrayList<>();
        private int listRolesCalls;

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
            RoleSummary current = roles.stream().filter(r -> r.id().equals(roleId)).findFirst().orElseThrow();
            java.util.HashSet<PermissionId> next = new java.util.HashSet<>(current.permissionIds());
            next.add(permissionId);
            RoleSummary updated = new RoleSummary(
                    current.id(),
                    current.name(),
                    current.description(),
                    next,
                    current.version() + 1,
                    current.createdAt(),
                    Instant.parse("2026-07-23T05:00:00Z"));
            roles.set(roles.indexOf(current), updated);
            return updated;
        }

        @Override
        public RoleSummary revokePermissionFromRole(RoleId roleId, PermissionId permissionId) {
            RoleSummary current = roles.stream().filter(r -> r.id().equals(roleId)).findFirst().orElseThrow();
            java.util.HashSet<PermissionId> next = new java.util.HashSet<>(current.permissionIds());
            next.remove(permissionId);
            RoleSummary updated = new RoleSummary(
                    current.id(),
                    current.name(),
                    current.description(),
                    next,
                    current.version() + 1,
                    current.createdAt(),
                    Instant.parse("2026-07-23T05:00:00Z"));
            roles.set(roles.indexOf(current), updated);
            return updated;
        }

        @Override
        public void deleteRole(RoleId roleId) {
            roles.removeIf(r -> r.id().equals(roleId));
        }

        @Override
        public List<RoleSummary> listRoles() {
            listRolesCalls++;
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
        public UserCreationResult createUser(Login login, DisplayName displayName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserSummary updateUser(UserId userId, Login login, DisplayName newDisplayName) {
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
        public PasswordResetResult requestPasswordReset(UserId targetUserId) {
            throw new UnsupportedOperationException();
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
