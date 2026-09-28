package com.tmp.ui.shell.screen.useradmin;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.PasswordResetResult;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.PermissionSummary;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.RoleAlreadyAssignedException;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.RoleSummary;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserCreationResult;
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserSummary;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.ui.shell.screen.useradmin.UserSecurityPresentation.EffectivePermissionGroup;
import com.tmp.ui.shell.screen.useradmin.UserSecurityPresentation.RoleAssignmentItem;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX ViewModel/Controller intentionally expose observable properties and retain ViewModel for FXML wiring")
/**
 * User administration ViewModel. Permission flags are cosmetic; enforcement stays in Security.
 */
public final class UserAdministrationViewModel {

    private final UserAdministrationService users;
    private final RoleAdministrationService roles;
    private final AuthorizationService authorization;
    private final ObservableList<UserSummary> userList = FXCollections.observableArrayList();
    private final FilteredList<UserSummary> filteredUserList = new FilteredList<>(userList);
    private final Map<UserId, String> rolesLabelByUser = new HashMap<>();
    private final List<RoleSummary> roleCatalogue = new ArrayList<>();
    private final List<PermissionSummary> permissionCatalogue = new ArrayList<>();
    private final BooleanProperty showDeleted = new SimpleBooleanProperty(false);
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final BooleanProperty canCreate = new SimpleBooleanProperty(false);
    private final BooleanProperty canUpdate = new SimpleBooleanProperty(false);
    private final BooleanProperty canDelete = new SimpleBooleanProperty(false);
    private final BooleanProperty canResetPassword = new SimpleBooleanProperty(false);
    private final BooleanProperty canAssignRoles = new SimpleBooleanProperty(false);
    private final BooleanProperty canViewRoles = new SimpleBooleanProperty(false);
    private final BooleanProperty canInspectUserRoles = new SimpleBooleanProperty(false);

    public UserAdministrationViewModel(
            UserAdministrationService users,
            RoleAdministrationService roles,
            AuthorizationService authorization) {
        this.users = Objects.requireNonNull(users, "users");
        this.roles = Objects.requireNonNull(roles, "roles");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        filteredUserList.setPredicate(this::isVisibleInTable);
        showDeleted.addListener((obs, oldValue, newValue) -> filteredUserList.setPredicate(this::isVisibleInTable));
        refreshPermissions();
    }

    public ObservableList<UserSummary> userList() {
        return userList;
    }

    public ObservableList<UserSummary> filteredUserList() {
        return filteredUserList;
    }

    public BooleanProperty showDeletedProperty() {
        return showDeleted;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public BooleanProperty canCreateProperty() {
        return canCreate;
    }

    public BooleanProperty canUpdateProperty() {
        return canUpdate;
    }

    public BooleanProperty canDeleteProperty() {
        return canDelete;
    }

    public BooleanProperty canResetPasswordProperty() {
        return canResetPassword;
    }

    public BooleanProperty canAssignRolesProperty() {
        return canAssignRoles;
    }

    public BooleanProperty canInspectUserRolesProperty() {
        return canInspectUserRoles;
    }

    public String rolesLabelFor(UserSummary user) {
        if (user == null) {
            return "";
        }
        if (!canInspectUserRoles.get()) {
            return "";
        }
        return rolesLabelByUser.getOrDefault(user.id(), UserSecurityPresentation.NO_ROLES);
    }

    public void refresh() {
        errorMessage.set("");
        refreshPermissions();
        try {
            List<UserSummary> loaded = users.listUsers(0, 100, null);
            reloadRoleCatalogues();
            rolesLabelByUser.clear();
            if (canInspectUserRoles.get()) {
                for (UserSummary user : loaded) {
                    Set<RoleId> assigned = roles.listRolesForUser(user.id());
                    List<String> names = UserSecurityPresentation.roleNamesForUser(assigned, roleCatalogue);
                    rolesLabelByUser.put(user.id(), UserSecurityPresentation.formatRolesLabel(names));
                }
            }
            userList.setAll(loaded);
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(safeMessage(ex));
        }
    }

    public Optional<UserDetailsSnapshot> openUserDetails(UserSummary selected) {
        if (selected == null) {
            return Optional.empty();
        }
        errorMessage.set("");
        try {
            reloadRoleCatalogues();
            Set<RoleId> assigned = Set.of();
            if (canInspectUserRoles.get()) {
                assigned = roles.listRolesForUser(selected.id());
            }
            List<RoleAssignmentItem> roleItems =
                    UserSecurityPresentation.roleAssignmentItems(roleCatalogue, assigned);
            Set<PermissionId> effective = Set.of();
            if (canInspectUserRoles.get()) {
                effective = roles.listEffectivePermissionsForUser(selected.id());
            }
            List<EffectivePermissionGroup> permissionGroups =
                    UserSecurityPresentation.effectivePermissionGroups(permissionCatalogue, effective);
            return Optional.of(new UserDetailsSnapshot(
                    selected,
                    roleItems,
                    permissionGroups,
                    canAssignRoles.get() && "ACTIVE".equals(selected.status())));
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage());
            return Optional.empty();
        } catch (RuntimeException ex) {
            errorMessage.set(safeMessage(ex));
            return Optional.empty();
        }
    }

    public boolean applyRoleAssignments(UserSummary selected, Set<RoleId> desiredRoleIds) {
        if (selected == null || !canAssignRoles.get()) {
            return false;
        }
        Objects.requireNonNull(desiredRoleIds, "desiredRoleIds");
        UserId userId = selected.id();
        errorMessage.set("");
        try {
            Set<RoleId> actual = roles.listRolesForUser(userId);
            Set<RoleId> desired = Set.copyOf(desiredRoleIds);
            for (RoleId roleId : desired) {
                if (!actual.contains(roleId)) {
                    roles.assignRole(userId, roleId);
                }
            }
            for (RoleId roleId : actual) {
                if (!desired.contains(roleId)) {
                    roles.revokeRole(userId, roleId);
                }
            }
            refresh();
            return true;
        } catch (AccessDeniedException | RoleAlreadyAssignedException | IllegalArgumentException ex) {
            errorMessage.set(ex.getMessage());
            return false;
        } catch (RuntimeException ex) {
            errorMessage.set(safeMessage(ex));
            return false;
        }
    }

    public Optional<String> createUser(String login, String displayName) {
        return runActionWithResult(() -> {
            UserCreationResult result = users.createUser(Login.of(login), DisplayName.of(displayName));
            refresh();
            return result.activationCode();
        });
    }

    public void updateUser(UserSummary selected, String login, String displayName) {
        if (selected == null) {
            return;
        }
        UserId id = selected.id();
        runAction(() -> {
            users.updateUser(id, Login.of(login), DisplayName.of(displayName));
            refresh();
        });
    }

    public void deleteUser(UserSummary selected) {
        if (selected == null) {
            return;
        }
        UserId id = selected.id();
        runAction(() -> {
            users.deleteUser(id);
            refresh();
        });
    }

    public Optional<String> requestPasswordReset(UserSummary selected) {
        if (selected == null) {
            return Optional.empty();
        }
        UserId id = selected.id();
        return runActionWithResult(() -> {
            PasswordResetResult result = users.requestPasswordReset(id);
            refresh();
            return result.activationCode();
        });
    }

    public boolean canEdit(UserSummary user) {
        return user != null && isActive(user) && canUpdate.get();
    }

    public boolean canDeleteUser(UserSummary user) {
        return user != null && isActive(user) && canDelete.get();
    }

    public boolean canReset(UserSummary user) {
        return user != null && isActive(user) && canResetPassword.get();
    }

    private void reloadRoleCatalogues() {
        roleCatalogue.clear();
        permissionCatalogue.clear();
        if (!canViewRoles.get() && !canAssignRoles.get()) {
            return;
        }
        if (canViewRoles.get()) {
            roleCatalogue.addAll(roles.listRoles());
        }
        permissionCatalogue.addAll(roles.listAllPermissionDefinitions());
    }

    private static boolean isActive(UserSummary user) {
        return "ACTIVE".equals(user.status());
    }

    private boolean isVisibleInTable(UserSummary user) {
        return showDeleted.get() || !"DELETED".equals(user.status());
    }

    private void refreshPermissions() {
        canCreate.set(authorization.hasPermission(SecurityPermissions.USERS_CREATE));
        canUpdate.set(authorization.hasPermission(SecurityPermissions.USERS_UPDATE));
        canDelete.set(authorization.hasPermission(SecurityPermissions.USERS_DELETE));
        canResetPassword.set(authorization.hasPermission(SecurityPermissions.USERS_RESET_PASSWORD));
        canAssignRoles.set(authorization.hasPermission(SecurityPermissions.ROLES_ASSIGN));
        canViewRoles.set(authorization.hasPermission(SecurityPermissions.ROLES_VIEW));
        canInspectUserRoles.set(canAssignRoles.get() && canViewRoles.get());
    }

    private void runAction(Runnable action) {
        errorMessage.set("");
        try {
            action.run();
        } catch (AccessDeniedException | RoleAlreadyAssignedException | IllegalArgumentException ex) {
            errorMessage.set(ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(safeMessage(ex));
        }
    }

    private <T> Optional<T> runActionWithResult(java.util.concurrent.Callable<T> action) {
        errorMessage.set("");
        try {
            return Optional.ofNullable(action.call());
        } catch (AccessDeniedException | RoleAlreadyAssignedException | IllegalArgumentException ex) {
            errorMessage.set(ex.getMessage());
            return Optional.empty();
        } catch (RuntimeException ex) {
            errorMessage.set(safeMessage(ex));
            return Optional.empty();
        } catch (Exception ex) {
            errorMessage.set(safeMessage(ex));
            return Optional.empty();
        }
    }

    private static String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Операция не выполнена";
        }
        return message;
    }

    /**
     * Snapshot for the user details card (roles + effective permissions).
     */
    public record UserDetailsSnapshot(
            UserSummary user,
            List<RoleAssignmentItem> roles,
            List<EffectivePermissionGroup> permissionGroups,
            boolean roleAssignmentEditable) {
        public UserDetailsSnapshot {
            Objects.requireNonNull(user, "user");
            roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
            permissionGroups = List.copyOf(Objects.requireNonNull(permissionGroups, "permissionGroups"));
        }

        public Set<RoleId> assignedRoleIds() {
            Set<RoleId> ids = new HashSet<>();
            for (RoleAssignmentItem item : roles) {
                if (item.assigned()) {
                    ids.add(item.roleId());
                }
            }
            return Set.copyOf(ids);
        }
    }
}
