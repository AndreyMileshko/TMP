package com.tmp.ui.shell.screen.roleadmin;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.PermissionSummary;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.RoleAlreadyAssignedException;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.RoleInUseException;
import com.tmp.security.api.RoleSummary;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserSummary;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;

/**
 * Role administration ViewModel: permission target-set editing and user assignment via desired
 * checkbox + Apply.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class RoleAdministrationViewModel {

    static final int USER_SEARCH_LIMIT = 30;

    private final RoleAdministrationService roles;
    private final UserAdministrationService users;
    private final AuthorizationService authorization;
    private final ObservableList<RoleSummary> roleList = FXCollections.observableArrayList();
    private final ObservableList<PermissionSummary> permissionCatalogue = FXCollections.observableArrayList();
    private final ObservableSet<PermissionId> actualPermissions = FXCollections.observableSet();
    private final ObservableSet<PermissionId> desiredPermissions = FXCollections.observableSet();
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final StringProperty userSearchQuery = new SimpleStringProperty("");
    private final ObservableList<UserSummary> userSearchResults = FXCollections.observableArrayList();
    private final ObjectProperty<UserSummary> selectedUser = new SimpleObjectProperty<>();
    private final BooleanProperty actualRoleAssigned = new SimpleBooleanProperty(false);
    private final BooleanProperty desiredRoleAssigned = new SimpleBooleanProperty(false);
    private final BooleanProperty canCreate = new SimpleBooleanProperty(false);
    private final BooleanProperty canUpdate = new SimpleBooleanProperty(false);
    private final BooleanProperty canDelete = new SimpleBooleanProperty(false);
    private final BooleanProperty canAssignRole = new SimpleBooleanProperty(false);
    private final BooleanProperty canManageRolePermissions = new SimpleBooleanProperty(false);
    private final BooleanProperty hasSelectedRole = new SimpleBooleanProperty(false);
    private final BooleanProperty permissionsDirty = new SimpleBooleanProperty(false);
    private final BooleanProperty assignmentDirty = new SimpleBooleanProperty(false);
    private RoleSummary selected;
    private RoleId selectedRoleId;

    public RoleAdministrationViewModel(
            RoleAdministrationService roles,
            UserAdministrationService users,
            AuthorizationService authorization) {
        this.roles = Objects.requireNonNull(roles, "roles");
        this.users = Objects.requireNonNull(users, "users");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        desiredRoleAssigned.addListener((obs, old, value) -> recomputeAssignmentDirty());
        refreshPermissions();
        refresh();
    }

    public ObservableList<RoleSummary> roleList() {
        return roleList;
    }

    public ObservableList<PermissionSummary> permissionCatalogue() {
        return permissionCatalogue;
    }

    public ObservableSet<PermissionId> desiredPermissions() {
        return desiredPermissions;
    }

    public ObservableSet<PermissionId> actualPermissions() {
        return actualPermissions;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public StringProperty userSearchQueryProperty() {
        return userSearchQuery;
    }

    public ObservableList<UserSummary> userSearchResults() {
        return userSearchResults;
    }

    public ObjectProperty<UserSummary> selectedUserProperty() {
        return selectedUser;
    }

    public BooleanProperty actualRoleAssignedProperty() {
        return actualRoleAssigned;
    }

    public BooleanProperty desiredRoleAssignedProperty() {
        return desiredRoleAssigned;
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

    public BooleanProperty permissionsDirtyProperty() {
        return permissionsDirty;
    }

    public BooleanProperty assignmentDirtyProperty() {
        return assignmentDirty;
    }

    public RoleId selectedRoleId() {
        return selectedRoleId;
    }

    public RoleSummary selectedRole() {
        return selected;
    }

    public boolean hasUnsavedPermissionChanges() {
        return permissionsDirty.get();
    }

    /**
     * @return {@code false} when selection was refused because unsaved permission changes need a
     *     caller decision
     */
    public boolean select(RoleSummary summary) {
        return select(summary, false);
    }

    public boolean select(RoleSummary summary, boolean discardUnsavedPermissions) {
        if (summary == null) {
            return false;
        }
        if (!discardUnsavedPermissions
                && selectedRoleId != null
                && !selectedRoleId.equals(summary.id())
                && permissionsDirty.get()) {
            return false;
        }
        this.selected = summary;
        this.selectedRoleId = summary.id();
        hasSelectedRole.set(true);
        replacePermissionSets(summary.permissionIds());
        resetAssignmentUi();
        return true;
    }

    public void clearSelection() {
        this.selected = null;
        this.selectedRoleId = null;
        hasSelectedRole.set(false);
        actualPermissions.clear();
        desiredPermissions.clear();
        permissionsDirty.set(false);
        resetAssignmentUi();
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
                        .ifPresentOrElse(role -> select(role, true), this::clearSelection);
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
            statusMessage.set(RoleAdministrationMessages.ROLE_CREATED);
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
            statusMessage.set(RoleAdministrationMessages.ROLE_UPDATED);
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
            statusMessage.set(RoleAdministrationMessages.ROLE_DELETED);
        });
    }

    public void setDesiredPermission(PermissionId permissionId, boolean granted) {
        Objects.requireNonNull(permissionId, "permissionId");
        if (granted) {
            desiredPermissions.add(permissionId);
        } else {
            desiredPermissions.remove(permissionId);
        }
        recomputePermissionsDirty();
    }

    public void setDesiredPermissionsInGroup(List<PermissionId> permissionIds, boolean granted) {
        Objects.requireNonNull(permissionIds, "permissionIds");
        if (granted) {
            desiredPermissions.addAll(permissionIds);
        } else {
            desiredPermissions.removeAll(permissionIds);
        }
        recomputePermissionsDirty();
    }

    public boolean isPermissionDesired(PermissionId permissionId) {
        return desiredPermissions.contains(permissionId);
    }

    public void applyPermissions() {
        if (selectedRoleId == null) {
            errorMessage.set(RoleAdministrationMessages.SELECT_ROLE);
            return;
        }
        if (!permissionsDirty.get()) {
            return;
        }
        RoleId id = selectedRoleId;
        Set<PermissionId> target = Set.copyOf(desiredPermissions);
        runAction(() -> {
            RoleSummary updated = roles.setRolePermissions(id, target);
            replaceRoleInList(updated);
            select(updated, true);
            statusMessage.set(RoleAdministrationMessages.PERMISSIONS_APPLIED);
        });
    }

    public void searchUsers(String query) {
        userSearchQuery.set(query == null ? "" : query);
        userSearchResults.clear();
        String q = userSearchQuery.get().trim();
        if (q.isEmpty()) {
            return;
        }
        try {
            userSearchResults.setAll(users.searchUsers(q, USER_SEARCH_LIMIT));
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(safeMessage(ex));
        }
    }

    public void selectUser(UserSummary user) {
        selectedUser.set(user);
        userSearchResults.clear();
        if (user != null) {
            userSearchQuery.set(formatUserLabel(user));
            reloadAssignmentState();
        } else {
            actualRoleAssigned.set(false);
            desiredRoleAssigned.set(false);
            recomputeAssignmentDirty();
        }
    }

    public void clearSelectedUser() {
        selectUser(null);
        userSearchQuery.set("");
    }

    public void applyRoleAssignment() {
        if (selectedRoleId == null) {
            errorMessage.set(RoleAdministrationMessages.SELECT_ROLE);
            return;
        }
        UserSummary user = selectedUser.get();
        if (user == null) {
            errorMessage.set(RoleAdministrationMessages.SELECT_USER);
            return;
        }
        if (!assignmentDirty.get()) {
            return;
        }
        RoleId roleId = selectedRoleId;
        UserId userId = user.id();
        boolean wantAssigned = desiredRoleAssigned.get();
        runAction(() -> {
            if (wantAssigned) {
                roles.assignRole(userId, roleId);
                statusMessage.set(RoleAdministrationMessages.ROLE_ASSIGNED);
            } else {
                roles.revokeRole(userId, roleId);
                statusMessage.set(RoleAdministrationMessages.ROLE_REVOKED);
            }
            reloadAssignmentState();
        });
    }

    public void togglePermission(PermissionId permissionId, boolean grant) {
        setDesiredPermission(permissionId, grant);
        applyPermissions();
    }

    private void reloadAssignmentState() {
        UserSummary user = selectedUser.get();
        if (user == null || selectedRoleId == null || !canAssignRole.get()) {
            actualRoleAssigned.set(false);
            desiredRoleAssigned.set(false);
            recomputeAssignmentDirty();
            return;
        }
        try {
            Set<RoleId> assigned = roles.listRolesForUser(user.id());
            boolean hasRole = assigned.contains(selectedRoleId);
            actualRoleAssigned.set(hasRole);
            desiredRoleAssigned.set(hasRole);
            recomputeAssignmentDirty();
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(safeMessage(ex));
        }
    }

    private void resetAssignmentUi() {
        selectedUser.set(null);
        userSearchQuery.set("");
        userSearchResults.clear();
        actualRoleAssigned.set(false);
        desiredRoleAssigned.set(false);
        recomputeAssignmentDirty();
    }

    private void replacePermissionSets(Set<PermissionId> permissions) {
        Set<PermissionId> copy = Set.copyOf(permissions);
        actualPermissions.clear();
        actualPermissions.addAll(copy);
        desiredPermissions.clear();
        desiredPermissions.addAll(copy);
        permissionsDirty.set(false);
    }

    private void recomputePermissionsDirty() {
        permissionsDirty.set(!desiredPermissions.equals(actualPermissions));
    }

    private void recomputeAssignmentDirty() {
        UserSummary user = selectedUser.get();
        if (user == null) {
            assignmentDirty.set(false);
            return;
        }
        assignmentDirty.set(desiredRoleAssigned.get() != actualRoleAssigned.get());
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

    private void refreshPermissions() {
        canCreate.set(authorization.hasPermission(SecurityPermissions.ROLES_CREATE));
        canUpdate.set(authorization.hasPermission(SecurityPermissions.ROLES_UPDATE));
        canDelete.set(authorization.hasPermission(SecurityPermissions.ROLES_DELETE));
        canAssignRole.set(authorization.hasPermission(SecurityPermissions.ROLES_ASSIGN));
        canManageRolePermissions.set(authorization.hasPermission(SecurityPermissions.PERMISSIONS_ASSIGN));
    }

    private void runAction(Runnable action) {
        errorMessage.set("");
        statusMessage.set("");
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

    static String formatUserLabel(UserSummary user) {
        String login = user.login().value();
        String display = user.displayName().value();
        if (display == null || display.isBlank() || display.equalsIgnoreCase(login)) {
            return login;
        }
        return display + " (" + login + ")";
    }

    private static String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank()
                ? RoleAdministrationMessages.OPERATION_FAILED
                : message;
    }
}
