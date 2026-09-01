package com.tmp.ui.shell.screen.login;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/**
 * Self-service user activation dialog: login, activation code, and new password.
 * Validation errors are shown inside the dialog; it stays open until success or cancel.
 */
final class UserActivationDialog {

    record ActivationInput(String login, String activationCode, char[] newPassword, char[] confirmPassword) {
    }

    private UserActivationDialog() {
    }

    /**
     * Shows the activation dialog. {@code onSubmit} returns empty on success or an error message
     * to display inside the dialog. Returns true when activation succeeded.
     */
    static boolean show(
            Window owner, String initialLogin, Function<ActivationInput, Optional<String>> onSubmit) {
        return createDialog(owner, initialLogin, onSubmit).showAndWait().orElse(Boolean.FALSE);
    }

    static Dialog<Boolean> createDialog(
            Window owner, String initialLogin, Function<ActivationInput, Optional<String>> onSubmit) {
        Objects.requireNonNull(onSubmit, "onSubmit");

        Dialog<Boolean> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Активация пользователя");
        dialog.setHeaderText("Активация пользователя");

        ButtonType saveType = new ButtonType("Сохранить пароль", ButtonBar.ButtonData.OK_DONE);
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialogPane.setMinWidth(420);

        TextField loginField = new TextField(initialLogin == null ? "" : initialLogin);
        loginField.setPromptText("Логин");
        TextField activationCodeField = new TextField();
        activationCodeField.setPromptText("Код активации");
        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("Новый пароль");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Повторите пароль");
        Label errorLabel = new Label();
        errorLabel.setWrapText(true);
        errorLabel.getStyleClass().add("login-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setMaxWidth(Double.MAX_VALUE);
        errorLabel.maxWidthProperty().bind(dialogPane.widthProperty().subtract(40));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Логин"), 0, 0);
        grid.add(loginField, 1, 0);
        grid.add(new Label("Код активации"), 0, 1);
        grid.add(activationCodeField, 1, 1);
        grid.add(new Label("Новый пароль"), 0, 2);
        grid.add(newPasswordField, 1, 2);
        grid.add(new Label("Повторите пароль"), 0, 3);
        grid.add(confirmPasswordField, 1, 3);
        GridPane.setHgrow(loginField, Priority.ALWAYS);
        GridPane.setHgrow(activationCodeField, Priority.ALWAYS);
        GridPane.setHgrow(newPasswordField, Priority.ALWAYS);
        GridPane.setHgrow(confirmPasswordField, Priority.ALWAYS);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10, 10, 0, 10));
        content.getChildren().addAll(grid, errorLabel);
        VBox.setVgrow(grid, Priority.NEVER);
        VBox.setVgrow(errorLabel, Priority.NEVER);
        dialogPane.setContent(content);

        Button saveButton = (Button) dialogPane.lookupButton(saveType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            clearError(dialog, errorLabel);
            String login = loginField.getText().trim();
            String code = activationCodeField.getText().trim();
            if (login.isEmpty() || code.isEmpty()) {
                showError(dialog, errorLabel, "Заполните логин и код активации");
                event.consume();
                return;
            }
            char[] newPassword = newPasswordField.getText().toCharArray();
            char[] confirmPassword = confirmPasswordField.getText().toCharArray();
            ActivationInput input = new ActivationInput(login, code, newPassword, confirmPassword);
            Optional<String> error = onSubmit.apply(input);
            newPasswordField.clear();
            confirmPasswordField.clear();
            if (error.isPresent()) {
                showError(dialog, errorLabel, error.get());
                event.consume();
            }
        });

        dialog.setResultConverter(button -> Boolean.valueOf(button == saveType));
        return dialog;
    }

    private static void clearError(Dialog<?> dialog, Label errorLabel) {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setText("");
        resizeDialogToFit(dialog);
    }

    private static void showError(Dialog<?> dialog, Label errorLabel, String message) {
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
