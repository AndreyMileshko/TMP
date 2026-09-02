package com.tmp.ui.shell.screen.roleadmin;

import com.tmp.security.api.RoleSummary;
import com.tmp.ui.shell.theme.TmpTheme;
import java.util.Optional;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/**
 * Modal dialogs for role administration. No Spring dependencies.
 */
final class RoleAdministrationDialogs {

    private RoleAdministrationDialogs() {
    }

    record RoleFormResult(String name, String description) {
    }

    static Optional<RoleFormResult> showCreateDialog(Window owner, boolean enabled) {
        return prepareCreateDialog(owner, enabled).showAndWait();
    }

    static Dialog<RoleFormResult> prepareCreateDialog(Window owner, boolean enabled) {
        return buildRoleFormDialog(owner, "Новая роль", "Создать", enabled, null, null);
    }

    static Optional<RoleFormResult> showEditDialog(Window owner, boolean enabled, RoleSummary role) {
        return prepareEditDialog(owner, enabled, role).showAndWait();
    }

    static Dialog<RoleFormResult> prepareEditDialog(Window owner, boolean enabled, RoleSummary role) {
        return buildRoleFormDialog(
                owner,
                "Редактирование роли",
                "Сохранить",
                enabled,
                role.name(),
                role.description());
    }

    static boolean showDeleteConfirmation(Window owner, RoleSummary role) {
        return prepareDeleteConfirmation(owner, role).showAndWait().orElse(Boolean.FALSE);
    }

    static Dialog<Boolean> prepareDeleteConfirmation(Window owner, RoleSummary role) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Удаление роли");
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        TmpTheme.apply(dialogPane);
        dialogPane.setMinWidth(420);

        ButtonType deleteType = new ButtonType("Удалить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(deleteType, cancelType);

        Label title = new Label("Удалить роль «" + role.name() + "»?");
        title.getStyleClass().add("tmp-dialog-title");
        title.setWrapText(true);
        Label instruction = new Label(
                "Это действие удалит роль, если она не используется.");
        instruction.setWrapText(true);
        instruction.getStyleClass().add("tmp-text-muted");

        VBox content = new VBox(8, title, instruction);
        content.setPadding(new Insets(8, 8, 0, 8));
        dialogPane.setContent(content);

        Button deleteButton = (Button) dialogPane.lookupButton(deleteType);
        Button cancelButton = (Button) dialogPane.lookupButton(cancelType);
        deleteButton.getStyleClass().add("tmp-button-danger");
        cancelButton.getStyleClass().add("tmp-button-secondary");

        dialog.setResultConverter(button -> Boolean.valueOf(button == deleteType));
        return dialog;
    }

    private static Dialog<RoleFormResult> buildRoleFormDialog(
            Window owner,
            String title,
            String submitLabel,
            boolean enabled,
            String initialName,
            String initialDescription) {
        Dialog<RoleFormResult> dialog = new Dialog<>();
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

        TextField nameField = new TextField(initialName == null ? "" : initialName);
        TextField descriptionField = new TextField(initialDescription == null ? "" : initialDescription);

        Label nameLabel = formLabel("Имя *");
        Label descriptionLabel = formLabel("Описание");
        Label errorLabel = new Label();
        errorLabel.setWrapText(true);
        errorLabel.getStyleClass().add("tmp-text-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setMaxWidth(Double.MAX_VALUE);
        errorLabel.maxWidthProperty().bind(dialogPane.widthProperty().subtract(40));

        GridPane grid = new GridPane();
        grid.getStyleClass().add("tmp-form");
        grid.add(nameLabel, 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(descriptionLabel, 0, 1);
        grid.add(descriptionField, 1, 1);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(descriptionField, Priority.ALWAYS);

        VBox content = new VBox(12);
        content.setPadding(new Insets(8, 8, 0, 8));
        content.getChildren().addAll(grid, errorLabel);
        dialogPane.setContent(content);

        Button submitButton = (Button) dialogPane.lookupButton(submitType);
        Button cancelButton = (Button) dialogPane.lookupButton(cancelType);
        submitButton.getStyleClass().add("tmp-button-action");
        cancelButton.getStyleClass().add("tmp-button-secondary");
        submitButton.setDisable(!enabled);
        nameField.setDisable(!enabled);
        descriptionField.setDisable(!enabled);

        submitButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            clearInlineError(dialog, errorLabel);
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                showInlineError(dialog, errorLabel, "Заполните имя роли.");
                event.consume();
            }
        });

        dialog.setResultConverter(button -> {
            if (button != submitType) {
                return null;
            }
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                return null;
            }
            return new RoleFormResult(name, descriptionField.getText().trim());
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
