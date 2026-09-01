package com.tmp.ui.shell.screen.login;

import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Window;

/**
 * Self-service password setup dialog for first login and post-reset flows.
 */
final class PasswordSetupDialog {

    record PasswordSetupInput(char[] newPassword, char[] confirmPassword) {
    }

    private PasswordSetupDialog() {
    }

    static Optional<PasswordSetupInput> show(Window owner, String login) {
        Dialog<PasswordSetupInput> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Создание пароля");
        dialog.setHeaderText("Создание пароля для пользователя " + login);

        ButtonType saveType = new ButtonType("Сохранить пароль", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("Новый пароль");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Повторите пароль");
        Label errorLabel = new Label();
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 10, 0, 10));
        grid.add(new Label("Новый пароль"), 0, 0);
        grid.add(newPasswordField, 1, 0);
        grid.add(new Label("Повторите пароль"), 0, 1);
        grid.add(confirmPasswordField, 1, 1);
        grid.add(errorLabel, 0, 2, 2, 1);
        GridPane.setHgrow(newPasswordField, Priority.ALWAYS);
        GridPane.setHgrow(confirmPasswordField, Priority.ALWAYS);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != saveType) {
                return null;
            }
            char[] newPassword = newPasswordField.getText().toCharArray();
            char[] confirmPassword = confirmPasswordField.getText().toCharArray();
            newPasswordField.clear();
            confirmPasswordField.clear();
            return new PasswordSetupInput(newPassword, confirmPassword);
        });

        return dialog.showAndWait();
    }
}
