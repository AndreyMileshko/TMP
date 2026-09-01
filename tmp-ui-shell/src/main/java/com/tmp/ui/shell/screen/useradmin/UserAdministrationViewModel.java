package com.tmp.ui.shell.screen.useradmin;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserSummary;
import com.tmp.security.api.SecurityPermissions;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"}, justification = "JavaFX ViewModel/Controller intentionally expose observable properties and retain ViewModel for FXML wiring")
/**
 * User administration ViewModel. Permission flags are cosmetic; enforcement stays in Security.
 */
public final class UserAdministrationViewModel {

    private final UserAdministrationService users;
    private final AuthorizationService authorization;
    private final ObservableList<UserSummary> userList = FXCollections.observableArrayList();
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final BooleanProperty canCreate = new SimpleBooleanProperty(false);
    private final BooleanProperty canUpdate = new SimpleBooleanProperty(false);
    private final BooleanProperty canDelete = new SimpleBooleanProperty(false);
    private final BooleanProperty canResetPassword = new SimpleBooleanProperty(false);

    public UserAdministrationViewModel(
            UserAdministrationService users, AuthorizationService authorization) {
        this.users = Objects.requireNonNull(users, "users");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        refreshPermissions();
        refresh();
    }

    public ObservableList<UserSummary> userList() {
        return userList;
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

    public void refresh() {
        errorMessage.set("");
        try {
            userList.setAll(users.listUsers(0, 100, null));
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(safeMessage(ex));
        }
        refreshPermissions();
    }

    public void createUser(String login, String displayName) {
        runAction(() -> {
            users.createUser(Login.of(login), DisplayName.of(displayName));
            refresh();
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

    public void requestPasswordReset(UserSummary selected) {
        if (selected == null) {
            return;
        }
        UserId id = selected.id();
        runAction(() -> {
            users.requestPasswordReset(id);
            refresh();
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

    private static boolean isActive(UserSummary user) {
        return "ACTIVE".equals(user.status());
    }

    private void refreshPermissions() {
        canCreate.set(authorization.hasPermission(SecurityPermissions.USERS_CREATE));
        canUpdate.set(authorization.hasPermission(SecurityPermissions.USERS_UPDATE));
        canDelete.set(authorization.hasPermission(SecurityPermissions.USERS_DELETE));
        canResetPassword.set(authorization.hasPermission(SecurityPermissions.USERS_RESET_PASSWORD));
    }

    private void runAction(Runnable action) {
        errorMessage.set("");
        try {
            action.run();
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(safeMessage(ex));
        }
    }

    private static String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Операция не выполнена";
        }
        return message;
    }
}
