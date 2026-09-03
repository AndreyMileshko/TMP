package com.tmp.ui.shell.screen.orderspecificationeditor;

import com.tmp.ui.shell.theme.TmpTheme;
import java.util.Optional;
import javafx.geometry.Insets;
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

/** TMP-themed dialogs for specification line add/edit/delete. */
final class SpecificationLineDialogs {

    private SpecificationLineDialogs() {}

    static Optional<SpecificationLineRow> showAddDialog(Window owner) {
        return showLineDialog(owner, "Добавить материал", "Добавить", SpecificationLineRow.blank());
    }

    static Optional<SpecificationLineRow> showEditDialog(Window owner, SpecificationLineRow existing) {
        return showLineDialog(owner, "Изменить материал", "Сохранить", existing.copy());
    }

    static boolean showDeleteConfirmation(Window owner) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Удаление строки");
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        TmpTheme.apply(dialogPane);
        dialogPane.getStyleClass().add("tmp-dialog");
        dialogPane.setMinWidth(420);

        ButtonType deleteType = new ButtonType("Удалить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(deleteType, cancelType);

        Label title = new Label("Удалить выбранную строку спецификации?");
        title.getStyleClass().add("tmp-dialog-title");
        title.setWrapText(true);

        VBox content = new VBox(8, title);
        content.setPadding(new Insets(8, 8, 0, 8));
        dialogPane.setContent(content);

        Button deleteButton = (Button) dialogPane.lookupButton(deleteType);
        Button cancelButton = (Button) dialogPane.lookupButton(cancelType);
        deleteButton.getStyleClass().add("tmp-button-danger");
        cancelButton.getStyleClass().add("tmp-button-secondary");

        dialog.setResultConverter(button -> Boolean.valueOf(button == deleteType));
        return dialog.showAndWait().orElse(Boolean.FALSE);
    }

    private static Optional<SpecificationLineRow> showLineDialog(
            Window owner, String titleText, String submitLabel, SpecificationLineRow initial) {
        Dialog<SpecificationLineRow> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(titleText);
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        TmpTheme.apply(dialogPane);
        dialogPane.getStyleClass().add("tmp-dialog");
        dialogPane.setMinWidth(460);

        ButtonType okType = new ButtonType(submitLabel, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(okType, cancelType);

        TextField materialCode = new TextField(initial.materialCode());
        TextField materialName = new TextField(initial.materialName());
        TextField color = new TextField(initial.color());
        TextField lengthMm = new TextField(initial.lengthMm());
        TextField lineQuantity = new TextField(initial.lineQuantity());
        TextField unitOfMeasure = new TextField(initial.unitOfMeasure());
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("tmp-text-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setWrapText(true);

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.add(new Label("Артикул"), 0, 0);
        form.add(materialCode, 1, 0);
        form.add(new Label("Наименование"), 0, 1);
        form.add(materialName, 1, 1);
        form.add(new Label("Цвет"), 0, 2);
        form.add(color, 1, 2);
        form.add(new Label("Длина мм"), 0, 3);
        form.add(lengthMm, 1, 3);
        form.add(new Label("Количество"), 0, 4);
        form.add(lineQuantity, 1, 4);
        form.add(new Label("Ед.изм."), 0, 5);
        form.add(unitOfMeasure, 1, 5);
        GridPane.setHgrow(materialCode, Priority.ALWAYS);
        GridPane.setHgrow(materialName, Priority.ALWAYS);

        Label title = new Label(titleText);
        title.getStyleClass().add("tmp-dialog-title");
        VBox content = new VBox(12, title, form, errorLabel);
        content.setPadding(new Insets(8, 8, 0, 8));
        dialogPane.setContent(content);

        Button okButton = (Button) dialogPane.lookupButton(okType);
        Button cancelButton = (Button) dialogPane.lookupButton(cancelType);
        okButton.getStyleClass().add("tmp-button-primary");
        cancelButton.getStyleClass().add("tmp-button-secondary");

        okButton.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                event -> {
                    SpecificationLineRow row =
                            new SpecificationLineRow(
                                    materialCode.getText(),
                                    materialName.getText(),
                                    color.getText(),
                                    lengthMm.getText(),
                                    lineQuantity.getText(),
                                    unitOfMeasure.getText());
                    try {
                        row.requireValid();
                        errorLabel.setVisible(false);
                        errorLabel.setManaged(false);
                        errorLabel.setText("");
                    } catch (RuntimeException ex) {
                        errorLabel.setText(
                                ex.getMessage() == null || ex.getMessage().isBlank()
                                        ? "Проверьте заполнение полей"
                                        : ex.getMessage());
                        errorLabel.setVisible(true);
                        errorLabel.setManaged(true);
                        event.consume();
                    }
                });

        dialog.setResultConverter(
                button -> {
                    if (button != okType) {
                        return null;
                    }
                    return new SpecificationLineRow(
                            materialCode.getText(),
                            materialName.getText(),
                            color.getText(),
                            lengthMm.getText(),
                            lineQuantity.getText(),
                            unitOfMeasure.getText());
                });
        return dialog.showAndWait();
    }
}
