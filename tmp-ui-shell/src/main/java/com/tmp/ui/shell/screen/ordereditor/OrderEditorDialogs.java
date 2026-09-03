package com.tmp.ui.shell.screen.ordereditor;

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
 * Confirmations for irreversible order actions.
 */
final class OrderEditorDialogs {

    private OrderEditorDialogs() {}

    static Optional<Boolean> confirmTransferToWork(Window owner, String title, String hint) {
        return prepareTransferDialog(owner, title, hint).showAndWait();
    }

    static Dialog<Boolean> prepareTransferDialog(Window owner, String title, String hint) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Передать в работу");
        dialog.setHeaderText(null);

        DialogPane pane = dialog.getDialogPane();
        TmpTheme.apply(pane);
        pane.setMinWidth(420);

        ButtonType transfer = new ButtonType("Передать в работу", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(transfer, cancel);

        Label question = new Label(title);
        question.setWrapText(true);
        Label immutability = new Label(hint);
        immutability.setWrapText(true);
        VBox content = new VBox(8, question, immutability);
        pane.setContent(content);

        dialog.setResultConverter(type -> type == transfer);
        return dialog;
    }

    static Optional<Boolean> confirmCancelOrder(Window owner, String orderNumber) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Отменить заказ");
        DialogPane pane = dialog.getDialogPane();
        TmpTheme.apply(pane);
        ButtonType confirm = new ButtonType("Отменить заказ", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(confirm, cancel);
        String text =
                orderNumber == null || orderNumber.isBlank()
                        ? "Отменить заказ?"
                        : "Отменить заказ №" + orderNumber + "?";
        Label body = new Label(text);
        body.setWrapText(true);
        pane.setContent(body);
        dialog.setResultConverter(type -> type == confirm);
        return dialog.showAndWait();
    }
}
