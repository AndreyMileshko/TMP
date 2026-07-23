package com.tmp.ui.shell.screen.roleadmin;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
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
    private final StringProperty nameInput = new SimpleStringProperty("");
    private final StringProperty descriptionInput = new SimpleStringProperty("");
    private final StringProperty assignLoginInput = new SimpleStringProperty("");
    private final BooleanProperty canCreate = new SimpleBooleanProperty(false);
    private final BooleanProperty canUpdate = new SimpleBooleanProperty(false);
    private final BooleanProperty canDelete = new SimpleBooleanProperty(false);
    private final BooleanProperty canAssign = new SimpleBooleanProperty(false);
    private RoleSummary selected;

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

    public StringProperty nameInputProperty() {
        return nameInput;
    }

    public StringProperty descriptionInputProperty() {
        return descriptionInput;
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

    public BooleanProperty canAssignProperty() {
        return canAssign;
    }

    public void select(RoleSummary summary) {
        this.selected = summary;
        if (summary != null) {
            nameInput.set(summary.name());
            descriptionInput.set(summary.description());
            selectedRolePermissions.setAll(summary.permissionIds());
        } else {
            selectedRolePermissions.clear();
        }
    }

    public void refresh() {
        errorMessage.set("");
        try {
            roleList.setAll(roles.listRoles());
            permissionCatalogue.setAll(roles.listAllPermissionDefinitions());
            if (selected != null) {
                roleList.stream()
                        .filter(r -> r.id().equals(selected.id()))
                        .findFirst()
                        .ifPresentOrElse(this::select, () -> select(null));
            }
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(safeMessage(ex));
        }
        refreshPermissions();
    }

    public void createRole() {
        runAction(() -> {
            roles.createRole(nameInput.get(), descriptionInput.get());
            refresh();
        });
    }

    public void updateSelected() {
        if (selected == null) {
            errorMessage.set("Р’С‹Р±РµСЂРёС‚Рµ СЂРѕР»СЊ");
            return;
        }
        RoleId id = selected.id();
        runAction(() -> {
            roles.updateRole(id, nameInput.get(), descriptionInput.get());
            refresh();
        });
    }

    public void deleteSelected() {
        if (selected == null) {
            errorMessage.set("Р’С‹Р±РµСЂРёС‚Рµ СЂРѕР»СЊ");
            return;
        }
        RoleId id = selected.id();
        runAction(() -> {
            roles.deleteRole(id);
            selected = null;
            refresh();
        });
    }

    public void togglePermission(PermissionId permissionId, boolean grant) {
        if (selected == null) {
            errorMessage.set("Р’С‹Р±РµСЂРёС‚Рµ СЂРѕР»СЊ");
            return;
        }
        RoleId id = selected.id();
        runAction(() -> {
            if (grant) {
                roles.grantPermissionToRole(id, permissionId);
            } else {
                roles.revokePermissionFromRole(id, permissionId);
            }
            refresh();
        });
    }

    public void assignRoleToLogin() {
        if (selected == null) {
            errorMessage.set("Р’С‹Р±РµСЂРёС‚Рµ СЂРѕР»СЊ");
            return;
        }
        RoleId roleId = selected.id();
        runAction(() -> {
            UserId userId = findUserIdByLogin(assignLoginInput.get());
            roles.assignRole(userId, roleId);
            refresh();
        });
    }

    public void revokeRoleFromLogin() {
        if (selected == null) {
            errorMessage.set("Р’С‹Р±РµСЂРёС‚Рµ СЂРѕР»СЊ");
            return;
        }
        RoleId roleId = selected.id();
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
                .orElseThrow(() -> new IllegalArgumentException("РџРѕР»СЊР·РѕРІР°С‚РµР»СЊ РЅРµ РЅР°Р№РґРµРЅ: " + loginText));
    }

    private void refreshPermissions() {
        canCreate.set(authorization.hasPermission(SecurityPermissions.ROLES_CREATE));
        canUpdate.set(authorization.hasPermission(SecurityPermissions.ROLES_UPDATE));
        canDelete.set(authorization.hasPermission(SecurityPermissions.ROLES_DELETE));
        canAssign.set(authorization.hasPermission(SecurityPermissions.ROLES_ASSIGN)
                || authorization.hasPermission(SecurityPermissions.PERMISSIONS_ASSIGN));
    }

    private void runAction(Runnable action) {
        errorMessage.set("");
        try {
            action.run();
        } catch (AccessDeniedException | RoleInUseException | IllegalArgumentException ex) {
            errorMessage.set(ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(safeMessage(ex));
        }
    }

    private static String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "РћРїРµСЂР°С†РёСЏ РЅРµ РІС‹РїРѕР»РЅРµРЅР°" : message;
    }
}
