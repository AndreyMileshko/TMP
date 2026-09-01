package com.tmp.ui.shell.screen.useradmin;

import com.tmp.security.api.UserSummary;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
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
        return showUserFormDialog(owner, "Новый пользователь", "Создать", enabled, null, null);
    }

    static Optional<UserFormResult> showEditDialog(
            Window owner, boolean enabled, UserSummary user) {
        return showUserFormDialog(
                owner,
                "Редактирование пользователя",
                "Сохранить",
                enabled,
                user.login().value(),
                user.displayName().value());
    }

    private static Optional<UserFormResult> showUserFormDialog(
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

        ButtonType submitType = new ButtonType(submitLabel, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(submitType, ButtonType.CANCEL);

        TextField loginField = new TextField(initialLogin == null ? "" : initialLogin);
        loginField.setPromptText("Логин");
        TextField nameField = new TextField(initialDisplayName == null ? "" : initialDisplayName);
        nameField.setPromptText("Имя");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 10, 0, 10));
        grid.add(new Label("Логин"), 0, 0);
        grid.add(loginField, 1, 0);
        grid.add(new Label("Имя"), 0, 1);
        grid.add(nameField, 1, 1);
        GridPane.setHgrow(loginField, Priority.ALWAYS);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        dialog.getDialogPane().setContent(grid);

        Button submitButton = (Button) dialog.getDialogPane().lookupButton(submitType);
        submitButton.setDisable(!enabled);
        loginField.setDisable(!enabled);
        nameField.setDisable(!enabled);

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

        return dialog.showAndWait();
    }

    static boolean confirmPasswordReset(Window owner, UserSummary user) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setTitle("Сброс пароля");
        alert.setHeaderText(null);
        alert.setContentText(
                "Сбросить пароль пользователя "
                        + user.login().value()
                        + " — "
                        + user.displayName().value()
                        + "?\nПользователь получит новый код активации.");
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    static void showUserCreatedDialog(Window owner, String login, String activationCode) {
        showActivationCodeDialog(
                owner,
                "Пользователь создан",
                "Логин: " + login,
                activationCode,
                "Передайте этот код пользователю.");
    }

    static void showPasswordResetDialog(Window owner, String login, String activationCode) {
        showActivationCodeDialog(
                owner,
                "Пароль сброшен",
                "Пользователь: " + login,
                activationCode,
                "Передайте новый код активации пользователю.");
    }

    private static void showActivationCodeDialog(
            Window owner, String title, String header, String activationCode, String footer) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(
                "Код активации:\n" + activationCode + "\n\n" + footer);

        ButtonType copyType = new ButtonType("Копировать код", ButtonBar.ButtonData.OTHER);
        alert.getButtonTypes().setAll(copyType, ButtonType.CLOSE);
        alert.showAndWait().ifPresent(button -> {
            if (button == copyType) {
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(activationCode);
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            }
        });
    }

    static Optional<String> showDeleteConfirmation(Window owner, UserSummary user) {
        Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Удаление пользователя");
        dialog.setHeaderText(null);

        ButtonType deleteType = new ButtonType("Удалить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(deleteType, ButtonType.CANCEL);

        Label message = new Label(
                "Вы собираетесь удалить пользователя:\n"
                        + user.login().value()
                        + " — "
                        + user.displayName().value()
                        + "\n\nДля подтверждения введите:\n\nУДАЛИТЬ");
        message.setWrapText(true);
        TextField confirmationField = new TextField();
        confirmationField.setPromptText("УДАЛИТЬ");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.add(message, 0, 0, 2, 1);
        grid.add(confirmationField, 0, 1, 2, 1);
        GridPane.setHgrow(confirmationField, Priority.ALWAYS);
        dialog.getDialogPane().setContent(grid);

        Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteType);
        deleteButton.setDisable(true);
        confirmationField.textProperty().addListener(
                (obs, oldValue, newValue) -> deleteButton.setDisable(!"УДАЛИТЬ".equals(newValue)));

        dialog.setResultConverter(button -> {
            if (button != deleteType) {
                return null;
            }
            return confirmationField.getText();
        });

        return dialog.showAndWait().filter("УДАЛИТЬ"::equals);
    }
}
