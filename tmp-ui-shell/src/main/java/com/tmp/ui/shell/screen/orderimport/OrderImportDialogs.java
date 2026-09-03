package com.tmp.ui.shell.screen.orderimport;

import com.tmp.ui.shell.theme.TmpTheme;
import java.util.Optional;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/**
 * Themed confirmation for order import (ACTIVE / immutable landing explained, semantics unchanged).
 */
final class OrderImportDialogs {

    private OrderImportDialogs() {}

    static Optional<Boolean> confirmImport(
            Window owner, String title, String body, String confirmLabel) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Импорт заказа");
        dialog.setHeaderText(null);

        DialogPane pane = dialog.getDialogPane();
        TmpTheme.apply(pane);
        pane.setMinWidth(440);

        ButtonType confirm = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(confirm, cancel);

        Label question = new Label(title);
        question.setWrapText(true);
        Label explanation = new Label(body);
        explanation.setWrapText(true);
        VBox content = new VBox(8, question, explanation);
        pane.setContent(content);

        dialog.setResultConverter(type -> type == confirm);
        return dialog.showAndWait();
    }
}
