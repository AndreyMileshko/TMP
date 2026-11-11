package com.tmp.ui.shell.screen.reference;

import com.tmp.reference.api.ColorDto;
import com.tmp.reference.api.So7ColorMappingDto;
import com.tmp.ui.shell.theme.TmpTheme;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.StringConverter;

/** Modal dialogs for SO7 mapping create / edit / delete (TMP UI Standard). */
final class So7MappingDialogSupport {

    @FunctionalInterface
    interface CreateMappingAction {
        String apply(String sourceText, UUID insideColorId, UUID outsideColorId);
    }

    @FunctionalInterface
    interface UpdatePairAction {
        String apply(UUID insideColorId, UUID outsideColorId);
    }

    private So7MappingDialogSupport() {}

    static boolean showCreateMappingDialog(
            Window owner, List<ColorDto> activeColors, CreateMappingAction createAction) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Новое соответствие СО7");
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        TmpTheme.apply(dialogPane);
        dialogPane.setMinWidth(480);

        ButtonType createType = new ButtonType("Добавить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(createType, cancelType);

        TextField sourceField = new TextField();
        sourceField.setPromptText("Исходное обозначение СО7");
        sourceField.setTextFormatter(
                new TextFormatter<>(
                        change -> change.getControlNewText().length() <= 64 ? change : null));

        ComboBox<ColorDto> insideCombo = createSearchableColorCombo(activeColors);
        ComboBox<ColorDto> outsideCombo = createSearchableColorCombo(activeColors);

        Label sourceLabel = formLabel("Цвет СО7");
        Label insideLabel = formLabel("Внутри TMP");
        Label outsideLabel = formLabel("Снаружи TMP");
        Label errorLabel = errorLabel(dialogPane);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(sourceLabel, 0, 0);
        grid.add(sourceField, 0, 1);
        grid.add(insideLabel, 0, 2);
        grid.add(insideCombo, 0, 3);
        grid.add(outsideLabel, 0, 4);
        grid.add(outsideCombo, 0, 5);
        grid.add(errorLabel, 0, 6);
        GridPane.setHgrow(sourceField, Priority.ALWAYS);
        GridPane.setHgrow(insideCombo, Priority.ALWAYS);
        GridPane.setHgrow(outsideCombo, Priority.ALWAYS);

        VBox content = new VBox(8, grid);
        content.setPadding(new Insets(8, 8, 0, 8));
        dialogPane.setContent(content);

        AtomicReference<Boolean> success = new AtomicReference<>(Boolean.FALSE);
        Button createButton = (Button) dialogPane.lookupButton(createType);
        createButton.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                event -> {
                    ColorDto inside = resolveSelectedColor(insideCombo);
                    ColorDto outside = resolveSelectedColor(outsideCombo);
                    String validation = validatePairSelection(sourceField.getText(), inside, outside);
                    if (validation != null) {
                        event.consume();
                        showInlineError(errorLabel, dialog, validation);
                        return;
                    }
                    String error =
                            createAction.apply(
                                    sourceField.getText(), inside.id(), outside.id());
                    if (error == null) {
                        success.set(Boolean.TRUE);
                        return;
                    }
                    event.consume();
                    showInlineError(errorLabel, dialog, error);
                });

        dialog.setResultConverter(button -> button == createType ? success.get() : Boolean.FALSE);
        Platform.runLater(sourceField::requestFocus);
        return dialog.showAndWait().orElse(Boolean.FALSE);
    }

    static boolean showEditPairDialog(
            Window owner,
            So7ColorMappingDto mapping,
            List<ColorDto> activeColors,
            UpdatePairAction updateAction) {
        Objects.requireNonNull(mapping, "mapping");
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Изменить пару соответствия");
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        TmpTheme.apply(dialogPane);
        dialogPane.setMinWidth(480);

        ButtonType saveType = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(saveType, cancelType);

        Label sourceValue = new Label(mapping.sourceText());
        sourceValue.setWrapText(true);
        ComboBox<ColorDto> insideCombo = createSearchableColorCombo(activeColors);
        ComboBox<ColorDto> outsideCombo = createSearchableColorCombo(activeColors);
        selectColorIfPresent(insideCombo, activeColors, mapping.insideColorId(), mapping.insideCode());
        selectColorIfPresent(
                outsideCombo, activeColors, mapping.outsideColorId(), mapping.outsideCode());

        Label sourceLabel = formLabel("Цвет СО7");
        Label insideLabel = formLabel("Внутри TMP");
        Label outsideLabel = formLabel("Снаружи TMP");
        Label errorLabel = errorLabel(dialogPane);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(sourceLabel, 0, 0);
        grid.add(sourceValue, 0, 1);
        grid.add(insideLabel, 0, 2);
        grid.add(insideCombo, 0, 3);
        grid.add(outsideLabel, 0, 4);
        grid.add(outsideCombo, 0, 5);
        grid.add(errorLabel, 0, 6);
        GridPane.setHgrow(insideCombo, Priority.ALWAYS);
        GridPane.setHgrow(outsideCombo, Priority.ALWAYS);

        VBox content = new VBox(8, grid);
        content.setPadding(new Insets(8, 8, 0, 8));
        dialogPane.setContent(content);

        AtomicReference<Boolean> success = new AtomicReference<>(Boolean.FALSE);
        Button saveButton = (Button) dialogPane.lookupButton(saveType);
        saveButton.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                event -> {
                    ColorDto inside = resolveSelectedColor(insideCombo);
                    ColorDto outside = resolveSelectedColor(outsideCombo);
                    String validation = validatePairSelection("ok", inside, outside);
                    if (validation != null) {
                        event.consume();
                        showInlineError(errorLabel, dialog, validation);
                        return;
                    }
                    String error = updateAction.apply(inside.id(), outside.id());
                    if (error == null) {
                        success.set(Boolean.TRUE);
                        return;
                    }
                    event.consume();
                    showInlineError(errorLabel, dialog, error);
                });

        dialog.setResultConverter(button -> button == saveType ? success.get() : Boolean.FALSE);
        Platform.runLater(insideCombo::requestFocus);
        return dialog.showAndWait().orElse(Boolean.FALSE);
    }

    static boolean showDeleteConfirmation(Window owner, String sourceText) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Удаление соответствия СО7");
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        TmpTheme.apply(dialogPane);
        dialogPane.setMinWidth(480);

        ButtonType deleteType = new ButtonType("Удалить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(deleteType, cancelType);

        Label title = new Label("Удалить соответствие «" + sourceText + "»?");
        title.getStyleClass().add("tmp-dialog-title");
        title.setWrapText(true);
        Label note =
                new Label(
                        "Запись будет удалена физически, и исходный текст СО7 снова можно будет"
                                + " сопоставить. Цвета каталога TMP не удаляются.");
        note.setWrapText(true);
        note.getStyleClass().add("tmp-text-muted");

        VBox content = new VBox(8, title, note);
        content.setPadding(new Insets(8, 8, 0, 8));
        dialogPane.setContent(content);

        dialog.setResultConverter(button -> button == deleteType);
        return dialog.showAndWait().orElse(Boolean.FALSE);
    }

    static ComboBox<ColorDto> createSearchableColorCombo(List<ColorDto> activeColors) {
        ObservableList<ColorDto> source = FXCollections.observableArrayList(activeColors);
        FilteredList<ColorDto> filtered = new FilteredList<>(source, color -> true);
        ComboBox<ColorDto> combo = new ComboBox<>(filtered);
        combo.setEditable(true);
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setPromptText("Поиск цвета TMP");
        combo.setVisibleRowCount(12);
        combo.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(ColorDto color) {
                        return color == null ? "" : color.code();
                    }

                    @Override
                    public ColorDto fromString(String text) {
                        if (text == null || text.isBlank()) {
                            return null;
                        }
                        String trimmed = text.trim();
                        for (ColorDto color : source) {
                            if (color.code().equalsIgnoreCase(trimmed)) {
                                return color;
                            }
                        }
                        return null;
                    }
                });
        combo.setCellFactory(
                list ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(ColorDto item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(empty || item == null ? null : item.code());
                            }
                        });
        combo.setButtonCell(
                new ListCell<>() {
                    @Override
                    protected void updateItem(ColorDto item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : item.code());
                    }
                });

        TextField editor = combo.getEditor();
        editor.textProperty()
                .addListener(
                        (obs, oldText, newText) -> {
                            ColorDto selected = combo.getSelectionModel().getSelectedItem();
                            if (selected != null
                                    && Objects.equals(selected.code(), newText)) {
                                filtered.setPredicate(color -> true);
                                return;
                            }
                            String needle =
                                    newText == null
                                            ? ""
                                            : newText.trim().toLowerCase(Locale.ROOT);
                            filtered.setPredicate(
                                    color ->
                                            needle.isEmpty()
                                                    || color.code()
                                                            .toLowerCase(Locale.ROOT)
                                                            .contains(needle));
                            if (!combo.isShowing() && combo.isFocused()) {
                                combo.show();
                            }
                        });
        return combo;
    }

    private static void selectColorIfPresent(
            ComboBox<ColorDto> combo,
            List<ColorDto> activeColors,
            UUID colorId,
            String fallbackCode) {
        for (ColorDto color : activeColors) {
            if (color.id().equals(colorId)) {
                combo.getSelectionModel().select(color);
                combo.getEditor().setText(color.code());
                return;
            }
        }
        // Archived side: keep code visible but require choosing an active color to save.
        if (fallbackCode != null && !fallbackCode.isBlank()) {
            combo.getEditor().setText(fallbackCode);
            combo.getSelectionModel().clearSelection();
        }
    }

    private static ColorDto resolveSelectedColor(ComboBox<ColorDto> combo) {
        ColorDto selected = combo.getSelectionModel().getSelectedItem();
        if (selected != null) {
            return selected;
        }
        return combo.getConverter().fromString(combo.getEditor().getText());
    }

    private static String validatePairSelection(
            String sourceText, ColorDto inside, ColorDto outside) {
        if (sourceText == null || sourceText.isBlank()) {
            return "Укажите исходное обозначение СО7.";
        }
        if (inside == null) {
            return "Выберите цвет внутри TMP.";
        }
        if (outside == null) {
            return "Выберите цвет снаружи TMP.";
        }
        return null;
    }

    private static Label formLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("tmp-form-label");
        return label;
    }

    private static Label errorLabel(DialogPane dialogPane) {
        Label errorLabel = new Label();
        errorLabel.setWrapText(true);
        errorLabel.getStyleClass().add("tmp-text-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setMaxWidth(Double.MAX_VALUE);
        errorLabel.maxWidthProperty().bind(dialogPane.widthProperty().subtract(40));
        return errorLabel;
    }

    private static void showInlineError(Label errorLabel, Dialog<?> dialog, String error) {
        errorLabel.setText(error);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        resizeDialogToFit(dialog);
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
