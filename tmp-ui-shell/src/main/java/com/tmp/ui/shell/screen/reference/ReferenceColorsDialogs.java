package com.tmp.ui.shell.screen.reference;

import com.tmp.ui.shell.theme.TmpTheme;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
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
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/** Modal dialogs for Reference Colors (TMP UI Standard conventions). */
final class ReferenceColorsDialogs {

    private ReferenceColorsDialogs() {}

    /**
     * Shows create-color dialog. {@code createAction} returns {@code null} on success or an error
     * message to keep the dialog open.
     */
    static boolean showCreateColorDialog(Window owner, Function<String, String> createAction) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Новый цвет TMP");
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        TmpTheme.apply(dialogPane);
        dialogPane.setMinWidth(420);

        ButtonType createType = new ButtonType("Добавить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(createType, cancelType);

        TextField codeField = new TextField();
        codeField.setPromptText("Код цвета");
        codeField.setTextFormatter(
                new TextFormatter<>(
                        change -> change.getControlNewText().length() <= 31 ? change : null));

        Label codeLabel = new Label("Код цвета");
        codeLabel.getStyleClass().add("tmp-form-label");
        Label errorLabel = new Label();
        errorLabel.setWrapText(true);
        errorLabel.getStyleClass().add("tmp-text-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setMaxWidth(Double.MAX_VALUE);
        errorLabel.maxWidthProperty().bind(dialogPane.widthProperty().subtract(40));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(codeLabel, 0, 0);
        grid.add(codeField, 0, 1);
        GridPane.setHgrow(codeField, Priority.ALWAYS);
        grid.add(errorLabel, 0, 2);

        VBox content = new VBox(8, grid);
        content.setPadding(new Insets(8, 8, 0, 8));
        dialogPane.setContent(content);

        AtomicReference<Boolean> success = new AtomicReference<>(Boolean.FALSE);
        Button createButton = (Button) dialogPane.lookupButton(createType);
        createButton.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                event -> {
                    String error = createAction.apply(codeField.getText());
                    if (error == null) {
                        success.set(Boolean.TRUE);
                        return;
                    }
                    event.consume();
                    errorLabel.setText(error);
                    errorLabel.setVisible(true);
                    errorLabel.setManaged(true);
                    resizeDialogToFit(dialog);
                });

        dialog.setResultConverter(button -> button == createType ? success.get() : Boolean.FALSE);
        Platform.runLater(codeField::requestFocus);
        return dialog.showAndWait().orElse(Boolean.FALSE);
    }

    static boolean showArchiveConfirmation(Window owner, String code, long activeMappings) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Архивирование цвета");
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        TmpTheme.apply(dialogPane);
        dialogPane.setMinWidth(460);

        ButtonType archiveType = new ButtonType("Архивировать", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(archiveType, cancelType);

        Label title = new Label("Архивировать цвет «" + code + "»?");
        title.getStyleClass().add("tmp-dialog-title");
        title.setWrapText(true);
        Label impact =
                new Label(
                        "Будут отключены активные соответствия СО7, где этот цвет указан внутри или"
                                + " снаружи: "
                                + activeMappings
                                + ".");
        impact.setWrapText(true);
        impact.getStyleClass().add("tmp-text-muted");
        Label note =
                new Label(
                        "Физическое удаление цвета недоступно. Код цвета после создания не"
                                + " изменяется.");
        note.setWrapText(true);
        note.getStyleClass().add("tmp-text-muted");

        VBox content = new VBox(8, title, impact, note);
        content.setPadding(new Insets(8, 8, 0, 8));
        dialogPane.setContent(content);

        dialog.setResultConverter(button -> button == archiveType);
        return dialog.showAndWait().orElse(Boolean.FALSE);
    }

    static boolean showRestoreConfirmation(Window owner, String code) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Восстановление цвета");
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        TmpTheme.apply(dialogPane);
        dialogPane.setMinWidth(460);

        ButtonType restoreType = new ButtonType("Восстановить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(restoreType, cancelType);

        Label title = new Label("Восстановить цвет «" + code + "»?");
        title.getStyleClass().add("tmp-dialog-title");
        title.setWrapText(true);
        Label note =
                new Label(
                        "Цвет снова станет доступен для новых соответствий. Ранее отключённые"
                                + " соответствия СО7 автоматически не включаются.");
        note.setWrapText(true);
        note.getStyleClass().add("tmp-text-muted");

        VBox content = new VBox(8, title, note);
        content.setPadding(new Insets(8, 8, 0, 8));
        dialogPane.setContent(content);

        dialog.setResultConverter(button -> button == restoreType);
        return dialog.showAndWait().orElse(Boolean.FALSE);
    }

    static boolean showMatchingSettingConfirmation(Window owner, boolean enable) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Автосопоставление цветов СО7");
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        TmpTheme.apply(dialogPane);
        dialogPane.setMinWidth(480);

        ButtonType okType =
                new ButtonType(enable ? "Включить" : "Выключить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(okType, cancelType);

        Label title =
                new Label(
                        enable
                                ? "Включить автоматическое сопоставление цветов СО7?"
                                : "Выключить автоматическое сопоставление цветов СО7?");
        title.getStyleClass().add("tmp-dialog-title");
        title.setWrapText(true);
        Label note =
                new Label("Настройка действует для всех будущих импортов всех пользователей.");
        note.setWrapText(true);
        note.getStyleClass().add("tmp-text-muted");

        VBox content = new VBox(8, title, note);
        content.setPadding(new Insets(8, 8, 0, 8));
        dialogPane.setContent(content);

        dialog.setResultConverter(button -> button == okType);
        return dialog.showAndWait().orElse(Boolean.FALSE);
    }

    private static void resizeDialogToFit(Dialog<?> dialog) {
        Runnable resize =
                () -> {
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
                };
        if (Platform.isFxApplicationThread()) {
            resize.run();
            Platform.runLater(resize);
            return;
        }
        Platform.runLater(resize);
    }
}