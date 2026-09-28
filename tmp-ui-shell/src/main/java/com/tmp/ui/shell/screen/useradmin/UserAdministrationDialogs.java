package com.tmp.ui.shell.screen.useradmin;

import com.tmp.security.api.RoleId;
import com.tmp.security.api.UserSummary;
import com.tmp.ui.shell.screen.useradmin.UserAdministrationViewModel.UserDetailsSnapshot;
import com.tmp.ui.shell.screen.useradmin.UserSecurityPresentation.EffectivePermissionGroup;
import com.tmp.ui.shell.screen.useradmin.UserSecurityPresentation.EffectivePermissionItem;
import com.tmp.ui.shell.screen.useradmin.UserSecurityPresentation.RoleAssignmentItem;
import com.tmp.ui.shell.theme.TmpTheme;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/**
 * Modal dialogs for user administration. No Spring dependencies.
 */
final class UserAdministrationDialogs {

    private UserAdministrationDialogs() {
    }

    record UserFormResult(String login, String displayName) {
    }

    static Optional<UserFormResult> showCreateDialog(Window owner, boolean enabled) {
        return prepareCreateDialog(owner, enabled).showAndWait();
    }

    static Dialog<UserFormResult> prepareCreateDialog(Window owner, boolean enabled) {
        return buildUserFormDialog(owner, "Новый пользователь", "Создать", enabled, null, null);
    }

    static Optional<UserFormResult> showEditDialog(
            Window owner, boolean enabled, UserSummary user) {
        return prepareEditDialog(owner, enabled, user).showAndWait();
    }

    static Dialog<UserFormResult> prepareEditDialog(
            Window owner, boolean enabled, UserSummary user) {
        return buildUserFormDialog(
                owner,
                "Редактирование пользователя",
                "Сохранить",
                enabled,
                user.login().value(),
                user.displayName().value());
    }

    static void showUserDetailsDialog(
            Window owner,
            UserDetailsSnapshot initial,
            Function<Set<RoleId>, UserDetailsSnapshot> applyRoles) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Пользователь");
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        TmpTheme.apply(dialogPane);
        dialogPane.setMinWidth(520);
        dialogPane.setPrefWidth(560);
        dialogPane.setPrefHeight(640);

        ButtonType closeType = new ButtonType("Закрыть", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().add(closeType);
        Button closeButton = (Button) dialogPane.lookupButton(closeType);
        closeButton.getStyleClass().add("tmp-button-secondary");

        Label loginValue = new Label();
        Label nameValue = new Label();
        HBox statusBox = new HBox();
        VBox rolesBox = new VBox(6);
        TreeView<String> permissionsTree = new TreeView<>();
        permissionsTree.setShowRoot(false);
        permissionsTree.setFocusTraversable(false);
        Label rolesEmptyLabel = new Label(UserSecurityPresentation.NO_ROLES);
        rolesEmptyLabel.getStyleClass().add("tmp-text-muted");
        Label permissionsEmptyLabel = new Label(UserSecurityPresentation.NO_PERMISSIONS);
        permissionsEmptyLabel.getStyleClass().add("tmp-text-muted");
        Button applyRolesButton = new Button("Применить роли");
        applyRolesButton.getStyleClass().add("tmp-button-action");
        Map<RoleId, CheckBox> roleChecks = new HashMap<>();

        Label loginLabel = formLabel("Логин");
        Label nameLabel = formLabel("Имя");
        Label statusLabel = formLabel("Статус");
        Label rolesTitle = formLabel("Роли");
        Label permissionsTitle = formLabel("Фактические права");

        GridPane header = new GridPane();
        header.getStyleClass().add("tmp-form");
        header.add(loginLabel, 0, 0);
        header.add(loginValue, 1, 0);
        header.add(nameLabel, 0, 1);
        header.add(nameValue, 1, 1);
        header.add(statusLabel, 0, 2);
        header.add(statusBox, 1, 2);
        GridPane.setHgrow(loginValue, Priority.ALWAYS);
        GridPane.setHgrow(nameValue, Priority.ALWAYS);

        VBox rolesSection = new VBox(8, rolesTitle, rolesBox, rolesEmptyLabel, applyRolesButton);
        VBox permissionsSection = new VBox(8, permissionsTitle, permissionsTree, permissionsEmptyLabel);
        VBox.setVgrow(permissionsTree, Priority.ALWAYS);

        VBox content = new VBox(16, header, rolesSection, permissionsSection);
        content.setPadding(new Insets(8, 8, 0, 8));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        dialogPane.setContent(scroll);

        final UserDetailsSnapshot[] current = {initial};
        Runnable refreshUi = () -> bindUserDetails(
                current[0],
                loginValue,
                nameValue,
                statusBox,
                rolesBox,
                rolesEmptyLabel,
                permissionsTree,
                permissionsEmptyLabel,
                roleChecks,
                applyRolesButton);
        applyRolesButton.setOnAction(e -> {
            Set<RoleId> desired = new HashSet<>();
            for (Map.Entry<RoleId, CheckBox> entry : roleChecks.entrySet()) {
                if (entry.getValue().isSelected()) {
                    desired.add(entry.getKey());
                }
            }
            UserDetailsSnapshot updated = applyRoles.apply(desired);
            if (updated != null) {
                current[0] = updated;
                refreshUi.run();
            }
        });
        refreshUi.run();

        dialog.showAndWait();
    }

    private static void bindUserDetails(
            UserDetailsSnapshot snapshot,
            Label loginValue,
            Label nameValue,
            HBox statusBox,
            VBox rolesBox,
            Label rolesEmptyLabel,
            TreeView<String> permissionsTree,
            Label permissionsEmptyLabel,
            Map<RoleId, CheckBox> roleChecks,
            Button applyRolesButton) {
        UserSummary user = snapshot.user();
        loginValue.setText(user.login().value());
        nameValue.setText(user.displayName().value());
        statusBox.getChildren().setAll(UserStatusPresentation.createStatusBadge(user.status()));

        roleChecks.clear();
        rolesBox.getChildren().clear();
        boolean hasRolesCatalogue = !snapshot.roles().isEmpty();
        rolesEmptyLabel.setVisible(!hasRolesCatalogue);
        rolesEmptyLabel.setManaged(!hasRolesCatalogue);
        rolesBox.setVisible(hasRolesCatalogue);
        rolesBox.setManaged(hasRolesCatalogue);
        for (RoleAssignmentItem item : snapshot.roles()) {
            CheckBox check = new CheckBox(item.name());
            check.setSelected(item.assigned());
            check.setDisable(!snapshot.roleAssignmentEditable());
            roleChecks.put(item.roleId(), check);
            rolesBox.getChildren().add(check);
        }
        if (!hasRolesCatalogue) {
            rolesEmptyLabel.setText(UserSecurityPresentation.NO_ROLES);
        }
        applyRolesButton.setVisible(snapshot.roleAssignmentEditable() && hasRolesCatalogue);
        applyRolesButton.setManaged(snapshot.roleAssignmentEditable() && hasRolesCatalogue);

        List<EffectivePermissionGroup> groups = snapshot.permissionGroups();
        boolean hasPermissions = !groups.isEmpty();
        permissionsEmptyLabel.setVisible(!hasPermissions);
        permissionsEmptyLabel.setManaged(!hasPermissions);
        permissionsTree.setVisible(hasPermissions);
        permissionsTree.setManaged(hasPermissions);
        if (!hasPermissions) {
            permissionsTree.setRoot(null);
            permissionsEmptyLabel.setText(UserSecurityPresentation.NO_PERMISSIONS);
            return;
        }
        TreeItem<String> root = new TreeItem<>("root");
        root.setExpanded(true);
        for (EffectivePermissionGroup group : groups) {
            TreeItem<String> groupItem = new TreeItem<>(group.displayName());
            groupItem.setExpanded(true);
            for (EffectivePermissionItem permission : group.permissions()) {
                String mark = permission.granted() ? "✓ " : "✕ ";
                groupItem.getChildren().add(new TreeItem<>(mark + permission.displayName()));
            }
            root.getChildren().add(groupItem);
        }
        permissionsTree.setRoot(root);
    }

    private static Dialog<UserFormResult> buildUserFormDialog(
            Window owner,
            String title,
            String submitLabel,
            boolean enabled,
            String initialLogin,
            String initialDisplayName) {
        Dialog<UserFormResult> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(title);
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        TmpTheme.apply(dialogPane);
        dialogPane.setMinWidth(420);

        ButtonType submitType = new ButtonType(submitLabel, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(submitType, cancelType);

        TextField loginField = new TextField(initialLogin == null ? "" : initialLogin);
        TextField nameField = new TextField(initialDisplayName == null ? "" : initialDisplayName);

        Label loginLabel = formLabel("Логин *");
        Label nameLabel = formLabel("Имя *");
        Label errorLabel = new Label();
        errorLabel.setWrapText(true);
        errorLabel.getStyleClass().add("tmp-text-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setMaxWidth(Double.MAX_VALUE);
        errorLabel.maxWidthProperty().bind(dialogPane.widthProperty().subtract(40));

        GridPane grid = new GridPane();
        grid.getStyleClass().add("tmp-form");
        grid.add(loginLabel, 0, 0);
        grid.add(loginField, 1, 0);
        grid.add(nameLabel, 0, 1);
        grid.add(nameField, 1, 1);
        GridPane.setHgrow(loginField, Priority.ALWAYS);
        GridPane.setHgrow(nameField, Priority.ALWAYS);

        VBox content = new VBox(12);
        content.setPadding(new Insets(8, 8, 0, 8));
        content.getChildren().addAll(grid, errorLabel);
        dialogPane.setContent(content);

        Button submitButton = (Button) dialogPane.lookupButton(submitType);
        Button cancelButton = (Button) dialogPane.lookupButton(cancelType);
        submitButton.getStyleClass().add("tmp-button-action");
        cancelButton.getStyleClass().add("tmp-button-secondary");
        submitButton.setDisable(!enabled);
        loginField.setDisable(!enabled);
        nameField.setDisable(!enabled);

        submitButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            clearInlineError(dialog, errorLabel);
            String login = loginField.getText().trim();
            String name = nameField.getText().trim();
            if (login.isEmpty() && name.isEmpty()) {
                showInlineError(dialog, errorLabel, "Заполните логин и имя.");
                event.consume();
                return;
            }
            if (login.isEmpty()) {
                showInlineError(dialog, errorLabel, "Заполните логин.");
                event.consume();
                return;
            }
            if (name.isEmpty()) {
                showInlineError(dialog, errorLabel, "Заполните имя.");
                event.consume();
            }
        });

        dialog.setResultConverter(button -> {
            if (button != submitType) {
                return null;
            }
            String login = loginField.getText().trim();
            String name = nameField.getText().trim();
            if (login.isEmpty() || name.isEmpty()) {
                return null;
            }
            return new UserFormResult(login, name);
        });

        return dialog;
    }

    static boolean confirmPasswordReset(Window owner, UserSummary user) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Сброс пароля");
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        TmpTheme.apply(dialogPane);
        dialogPane.setMinWidth(420);

        ButtonType resetType = new ButtonType("Сбросить пароль", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(resetType, cancelType);

        Label title = new Label("Сброс пароля пользователя");
        title.getStyleClass().add("tmp-dialog-title");
        Label details = new Label(
                "Пользователь: "
                        + user.login().value()
                        + " — "
                        + user.displayName().value());
        details.setWrapText(true);
        Label instruction = new Label(
                "Пароль будет сброшен. Пользователь получит новый код активации.");
        instruction.setWrapText(true);
        instruction.getStyleClass().add("tmp-text-muted");

        VBox content = new VBox(8, title, details, instruction);
        content.setPadding(new Insets(8, 8, 0, 8));
        dialogPane.setContent(content);

        Button resetButton = (Button) dialogPane.lookupButton(resetType);
        Button cancelButton = (Button) dialogPane.lookupButton(cancelType);
        resetButton.getStyleClass().add("tmp-button-action");
        cancelButton.getStyleClass().add("tmp-button-secondary");

        dialog.setResultConverter(button -> Boolean.valueOf(button == resetType));
        return dialog.showAndWait().orElse(Boolean.FALSE);
    }

    static void showUserCreatedDialog(Window owner, String login, String activationCode) {
        showActivationCodeDialog(
                owner,
                "Пользователь создан",
                "Логин: " + login,
                activationCode,
                "Код активации показывается один раз. Передайте этот код пользователю.");
    }

    static void showPasswordResetDialog(Window owner, String login, String activationCode) {
        showActivationCodeDialog(
                owner,
                "Пароль сброшен",
                "Пользователь: " + login,
                activationCode,
                "Код активации показывается один раз. Передайте новый код активации пользователю.");
    }

    private static void showActivationCodeDialog(
            Window owner, String title, String header, String activationCode, String footer) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(title);
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        TmpTheme.apply(dialogPane);
        dialogPane.setMinWidth(420);

        ButtonType copyType = new ButtonType("Копировать код", ButtonBar.ButtonData.OTHER);
        ButtonType closeType = new ButtonType("Закрыть", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(copyType, closeType);

        Label headerLabel = new Label(header);
        headerLabel.setWrapText(true);
        Label codeLabel = new Label(activationCode);
        codeLabel.getStyleClass().addAll("tmp-badge", "tmp-badge-info");
        Label instruction = new Label(footer);
        instruction.setWrapText(true);
        instruction.getStyleClass().add("tmp-text-muted");

        VBox content = new VBox(12, headerLabel, codeLabel, instruction);
        content.setPadding(new Insets(8, 8, 0, 8));
        dialogPane.setContent(content);

        Button copyButton = (Button) dialogPane.lookupButton(copyType);
        Button closeButton = (Button) dialogPane.lookupButton(closeType);
        copyButton.getStyleClass().add("tmp-button-action");
        closeButton.getStyleClass().add("tmp-button-secondary");

        dialog.setResultConverter(button -> button);
        dialog.showAndWait().ifPresent(button -> {
            if (button == copyType) {
                javafx.scene.input.ClipboardContent clipboardContent = new javafx.scene.input.ClipboardContent();
                clipboardContent.putString(activationCode);
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(clipboardContent);
            }
        });
    }

    static Optional<String> showDeleteConfirmation(Window owner, UserSummary user) {
        return prepareDeleteConfirmation(owner, user).showAndWait().filter("УДАЛИТЬ"::equals);
    }

    static Dialog<String> prepareDeleteConfirmation(Window owner, UserSummary user) {
        Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Удаление пользователя");
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        TmpTheme.apply(dialogPane);
        dialogPane.setMinWidth(420);

        ButtonType deleteType = new ButtonType("Удалить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(deleteType, cancelType);

        Label title = new Label("Удаление пользователя");
        title.getStyleClass().add("tmp-dialog-title");
        Label details = new Label(
                "Логин: "
                        + user.login().value()
                        + "\nИмя: "
                        + user.displayName().value());
        details.setWrapText(true);
        Label instruction = new Label("Для подтверждения введите УДАЛИТЬ");
        instruction.setWrapText(true);
        TextField confirmationField = new TextField();
        confirmationField.setPromptText("УДАЛИТЬ");

        GridPane grid = new GridPane();
        grid.getStyleClass().add("tmp-form");
        grid.add(title, 0, 0, 2, 1);
        grid.add(details, 0, 1, 2, 1);
        grid.add(instruction, 0, 2, 2, 1);
        grid.add(confirmationField, 0, 3, 2, 1);
        GridPane.setHgrow(confirmationField, Priority.ALWAYS);

        VBox content = new VBox(8, grid);
        content.setPadding(new Insets(8, 8, 0, 8));
        dialogPane.setContent(content);

        Button deleteButton = (Button) dialogPane.lookupButton(deleteType);
        Button cancelButton = (Button) dialogPane.lookupButton(cancelType);
        deleteButton.getStyleClass().add("tmp-button-danger");
        cancelButton.getStyleClass().add("tmp-button-secondary");
        deleteButton.setDisable(true);
        confirmationField.textProperty().addListener(
                (obs, oldValue, newValue) -> deleteButton.setDisable(!"УДАЛИТЬ".equals(newValue)));

        dialog.setResultConverter(button -> {
            if (button != deleteType) {
                return null;
            }
            return confirmationField.getText();
        });

        return dialog;
    }

    private static Label formLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("tmp-form-label");
        return label;
    }

    private static void clearInlineError(Dialog<?> dialog, Label errorLabel) {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setText("");
        resizeDialogToFit(dialog);
    }

    private static void showInlineError(Dialog<?> dialog, Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        resizeDialogToFit(dialog);
    }

    private static void resizeDialogToFit(Dialog<?> dialog) {
        Runnable resize = () -> applyDialogResize(dialog);
        if (Platform.isFxApplicationThread()) {
            applyDialogResize(dialog);
            Platform.runLater(resize);
            return;
        }
        Platform.runLater(resize);
    }

    private static void applyDialogResize(Dialog<?> dialog) {
        DialogPane dialogPane = dialog.getDialogPane();
        if (dialogPane.getContent() instanceof Parent content) {
            content.applyCss();
            content.layout();
        }
        dialogPane.applyCss();
        dialogPane.layout();
        if (dialogPane.getScene() != null && dialogPane.getScene().getWindow() != null) {
            dialogPane.getScene().getWindow().sizeToScene();
        }
    }
}
