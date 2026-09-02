package com.tmp.ui.shell.screen.roleadmin;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.PermissionSummary;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.RoleAlreadyAssignedException;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.RoleSummary;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserSummary;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.api.RoleInUseException;
import java.util.Objects;
import java.util.Set;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"}, justification = "JavaFX ViewModel/Controller intentionally expose observable properties and retain ViewModel for FXML wiring")
/**
 * Role administration ViewModel.
 */
public final class RoleAdministrationViewModel {

    private final RoleAdministrationService roles;
    private final UserAdministrationService users;
    private final AuthorizationService authorization;
    private final ObservableList<RoleSummary> roleList = FXCollections.observableArrayList();
    private final ObservableList<PermissionSummary> permissionCatalogue = FXCollections.observableArrayList();
    private final ObservableList<PermissionId> selectedRolePermissions = FXCollections.observableArrayList();
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty assignLoginInput = new SimpleStringProperty("");
    private final BooleanProperty canCreate = new SimpleBooleanProperty(false);
    private final BooleanProperty canUpdate = new SimpleBooleanProperty(false);
    private final BooleanProperty canDelete = new SimpleBooleanProperty(false);
    private final BooleanProperty canAssignRole = new SimpleBooleanProperty(false);
    private final BooleanProperty canManageRolePermissions = new SimpleBooleanProperty(false);
    private final BooleanProperty hasSelectedRole = new SimpleBooleanProperty(false);
    private RoleSummary selected;
    private RoleId selectedRoleId;

    public RoleAdministrationViewModel(
            RoleAdministrationService roles,
            UserAdministrationService users,
            AuthorizationService authorization) {
        this.roles = Objects.requireNonNull(roles, "roles");
        this.users = Objects.requireNonNull(users, "users");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        refreshPermissions();
        refresh();
    }

    public ObservableList<RoleSummary> roleList() {
        return roleList;
    }

    public ObservableList<PermissionSummary> permissionCatalogue() {
        return permissionCatalogue;
    }

    public ObservableList<PermissionId> selectedRolePermissions() {
        return selectedRolePermissions;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty assignLoginInputProperty() {
        return assignLoginInput;
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

    public BooleanProperty canAssignRoleProperty() {
        return canAssignRole;
    }

    public BooleanProperty canManageRolePermissionsProperty() {
        return canManageRolePermissions;
    }

    public BooleanProperty hasSelectedRoleProperty() {
        return hasSelectedRole;
    }

    public RoleId selectedRoleId() {
        return selectedRoleId;
    }

    public RoleSummary selectedRole() {
        return selected;
    }

    public void select(RoleSummary summary) {
        if (summary == null) {
            return;
        }
        this.selected = summary;
        this.selectedRoleId = summary.id();
        hasSelectedRole.set(true);
        selectedRolePermissions.setAll(summary.permissionIds());
    }

    public void clearSelection() {
        this.selected = null;
        this.selectedRoleId = null;
        hasSelectedRole.set(false);
        selectedRolePermissions.clear();
    }

    public void refresh() {
        errorMessage.set("");
        RoleId previouslySelected = selectedRoleId;
        try {
            roleList.setAll(roles.listRoles());
            permissionCatalogue.setAll(roles.listAllPermissionDefinitions());
            if (previouslySelected != null) {
                roleList.stream()
                        .filter(r -> r.id().equals(previouslySelected))
                        .findFirst()
                        .ifPresentOrElse(this::select, this::clearSelection);
            }
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(safeMessage(ex));
        }
        refreshPermissions();
    }

    public void createRole(String name, String description) {
        runAction(() -> {
            roles.createRole(name, description);
            refresh();
        });
    }

    public void updateRole(RoleSummary role, String name, String description) {
        if (role == null) {
            errorMessage.set(RoleAdministrationMessages.SELECT_ROLE);
            return;
        }
        RoleId id = role.id();
        runAction(() -> {
            roles.updateRole(id, name, description);
            refresh();
        });
    }

    public void deleteRole(RoleSummary role) {
        if (role == null) {
            errorMessage.set(RoleAdministrationMessages.SELECT_ROLE);
            return;
        }
        RoleId id = role.id();
        runAction(() -> {
            roles.deleteRole(id);
            if (selectedRoleId != null && selectedRoleId.equals(id)) {
                clearSelection();
            }
            refresh();
        });
    }

    public void togglePermission(PermissionId permissionId, boolean grant) {
        if (selectedRoleId == null) {
            errorMessage.set(RoleAdministrationMessages.SELECT_ROLE);
            return;
        }
        RoleId id = selectedRoleId;
        runAction(() -> {
            RoleSummary updated = grant
                    ? roles.grantPermissionToRole(id, permissionId)
                    : roles.revokePermissionFromRole(id, permissionId);
            replaceRoleInList(updated);
            select(updated);
        });
    }

    private void replaceRoleInList(RoleSummary updated) {
        for (int i = 0; i < roleList.size(); i++) {
            if (roleList.get(i).id().equals(updated.id())) {
                roleList.set(i, updated);
                return;
            }
        }
        roleList.add(updated);
    }

    public void assignRoleToLogin() {
        if (selectedRoleId == null) {
            errorMessage.set(RoleAdministrationMessages.SELECT_ROLE);
            return;
        }
        RoleId roleId = selectedRoleId;
        runAction(() -> {
            UserId userId = findUserIdByLogin(assignLoginInput.get());
            roles.assignRole(userId, roleId);
            refresh();
        });
    }

    public void revokeRoleFromLogin() {
        if (selectedRoleId == null) {
            errorMessage.set(RoleAdministrationMessages.SELECT_ROLE);
            return;
        }
        RoleId roleId = selectedRoleId;
        runAction(() -> {
            UserId userId = findUserIdByLogin(assignLoginInput.get());
            roles.revokeRole(userId, roleId);
            refresh();
        });
    }

    public boolean isPermissionGrantedOnSelected(PermissionId permissionId) {
        return selectedRolePermissions.contains(permissionId);
    }

    private UserId findUserIdByLogin(String loginText) {
        return users.listUsers(0, 500, null).stream()
                .filter(u -> u.login().value().equalsIgnoreCase(loginText))
                .map(UserSummary::id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(RoleAdministrationMessages.userNotFound(loginText)));
    }

    private void refreshPermissions() {
        canCreate.set(authorization.hasPermission(SecurityPermissions.ROLES_CREATE));
        canUpdate.set(authorization.hasPermission(SecurityPermissions.ROLES_UPDATE));
        canDelete.set(authorization.hasPermission(SecurityPermissions.ROLES_DELETE));
        canAssignRole.set(authorization.hasPermission(SecurityPermissions.ROLES_ASSIGN));
        canManageRolePermissions.set(authorization.hasPermission(SecurityPermissions.PERMISSIONS_ASSIGN));
    }

    private void runAction(Runnable action) {
        errorMessage.set("");
        try {
            action.run();
        } catch (AccessDeniedException
                | RoleInUseException
                | RoleAlreadyAssignedException
                | IllegalArgumentException ex) {
            errorMessage.set(ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(safeMessage(ex));
        }
    }

    private static String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank()
                ? RoleAdministrationMessages.OPERATION_FAILED
                : message;
    }
}
