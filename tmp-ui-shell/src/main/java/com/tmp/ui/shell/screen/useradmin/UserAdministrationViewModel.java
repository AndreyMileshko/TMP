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
 * User administration ViewModel. Permission buttons are cosmetic; enforcement stays in Security.
 */
public final class UserAdministrationViewModel {

    private final UserAdministrationService users;
    private final AuthorizationService authorization;
    private final ObservableList<UserSummary> userList = FXCollections.observableArrayList();
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty loginInput = new SimpleStringProperty("");
    private final StringProperty displayNameInput = new SimpleStringProperty("");
    private final StringProperty passwordInput = new SimpleStringProperty("");
    private final BooleanProperty canCreate = new SimpleBooleanProperty(false);
    private final BooleanProperty canUpdate = new SimpleBooleanProperty(false);
    private final BooleanProperty canDelete = new SimpleBooleanProperty(false);
    private final BooleanProperty canResetPassword = new SimpleBooleanProperty(false);
    private UserSummary selected;

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

    public StringProperty loginInputProperty() {
        return loginInput;
    }

    public StringProperty displayNameInputProperty() {
        return displayNameInput;
    }

    public StringProperty passwordInputProperty() {
        return passwordInput;
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

    public void select(UserSummary summary) {
        this.selected = summary;
        if (summary != null) {
            loginInput.set(summary.login().value());
            displayNameInput.set(summary.displayName().value());
        }
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

    public void createUser() {
        runAction(() -> {
            char[] password = passwordInput.get().toCharArray();
            try {
                users.createUser(Login.of(loginInput.get()), DisplayName.of(displayNameInput.get()), password);
            } finally {
                java.util.Arrays.fill(password, '\0');
                passwordInput.set("");
            }
            refresh();
        });
    }

    public void updateSelected() {
        if (selected == null) {
            errorMessage.set("Р’С‹Р±РµСЂРёС‚Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ");
            return;
        }
        UserId id = selected.id();
        runAction(() -> {
            users.updateUser(id, DisplayName.of(displayNameInput.get()));
            refresh();
        });
    }

    public void deleteSelected() {
        if (selected == null) {
            errorMessage.set("Р’С‹Р±РµСЂРёС‚Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ");
            return;
        }
        UserId id = selected.id();
        runAction(() -> {
            users.deleteUser(id);
            selected = null;
            refresh();
        });
    }

    public void resetPasswordSelected() {
        if (selected == null) {
            errorMessage.set("Р’С‹Р±РµСЂРёС‚Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ");
            return;
        }
        UserId id = selected.id();
        runAction(() -> {
            char[] password = passwordInput.get().toCharArray();
            try {
                users.resetPassword(id, password);
            } finally {
                java.util.Arrays.fill(password, '\0');
                passwordInput.set("");
            }
            refresh();
        });
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
            return "РћРїРµСЂР°С†РёСЏ РЅРµ РІС‹РїРѕР»РЅРµРЅР°";
        }
        return message;
    }
}
