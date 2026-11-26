package com.tmp.ui.shell.screen.orderimport.colors;

import com.tmp.reference.api.ColorDto;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.input.KeyCode;
import javafx.util.Callback;
import javafx.util.StringConverter;

/** Searchable catalog color editors for resolution table and toolbar (COL-041A). */
public final class ColorPairCellEditors {

    public enum Side {
        INSIDE,
        OUTSIDE
    }

    private ColorPairCellEditors() {}

    public static ComboBox<ColorDto> createSearchableColorCombo(List<ColorDto> activeColors) {
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

        var editor = combo.getEditor();
        editor.textProperty()
                .addListener(
                        (obs, oldText, newText) -> {
                            ColorDto selected = combo.getSelectionModel().getSelectedItem();
                            if (selected != null && Objects.equals(selected.code(), newText)) {
                                filtered.setPredicate(color -> true);
                                return;
                            }
                            String needle =
                                    newText == null ? "" : newText.trim().toLowerCase(Locale.ROOT);
                            filtered.setPredicate(
                                    color ->
                                            needle.isEmpty()
                                                    || color.code().toLowerCase(Locale.ROOT).contains(needle));
                            if (!combo.isShowing() && combo.isFocused()) {
                                combo.show();
                            }
                        });
        return combo;
    }

    public static Callback<TableColumn<ColorResolutionRow, String>, TableCell<ColorResolutionRow, String>> colorCellFactory(
            ColorResolutionDialogViewModel viewModel, Side side, Runnable onError) {
        return column -> new CatalogColorTableCell(viewModel, side, onError);
    }

    private static final class CatalogColorTableCell extends TableCell<ColorResolutionRow, String> {

        private final ColorResolutionDialogViewModel viewModel;
        private final Side side;
        private final Runnable onError;
        private ComboBox<ColorDto> combo;

        private CatalogColorTableCell(
                ColorResolutionDialogViewModel viewModel, Side side, Runnable onError) {
            this.viewModel = viewModel;
            this.side = side;
            this.onError = onError;
        }

        @Override
        public void startEdit() {
            if (!isEditable() || !isEditing()) {
                return;
            }
            super.startEdit();
            ColorResolutionRow row = getTableRow() == null ? null : getTableRow().getItem();
            if (row == null) {
                cancelEdit();
                return;
            }
            combo = createSearchableColorCombo(viewModel.session().activeColors());
            UUID initialId =
                    side == Side.INSIDE
                            ? draftOrCatalogInside(row)
                            : draftOrCatalogOutside(row);
            if (initialId != null) {
                selectColor(combo, initialId);
            }
            combo.setOnAction(event -> commitFromCombo(row));
            combo.setOnKeyReleased(
                    event -> {
                        if (event.getCode() == KeyCode.ESCAPE) {
                            viewModel.cancelDraft(row.id());
                            cancelEdit();
                            return;
                        }
                        if (event.getCode() == KeyCode.ENTER) {
                            commitFromCombo(row);
                        }
                    });
            setText(null);
            setGraphic(combo);
            combo.requestFocus();
        }

        {
            setOnMouseClicked(
                    event -> {
                        if (event.getClickCount() == 2 && !isEmpty() && !isEditing()) {
                            javafx.application.Platform.runLater(this::startEdit);
                        }
                    });
        }

        @Override
        public void cancelEdit() {
            ColorResolutionRow row = getTableRow() == null ? null : getTableRow().getItem();
            if (row != null) {
                viewModel.cancelDraft(row.id());
            }
            super.cancelEdit();
            cleanupAfterEdit();
        }

        @Override
        public void commitEdit(String newValue) {
            super.commitEdit(newValue);
            cleanupAfterEdit();
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            if (isEditing()) {
                return;
            }
            ColorResolutionRow row = getTableRow().getItem();
            String display = side == Side.INSIDE ? viewModel.insideDisplay(row) : viewModel.outsideDisplay(row);
            setText(display);
            setGraphic(null);
        }

        private void commitFromCombo(ColorResolutionRow row) {
            ColorDto selected = combo.getSelectionModel().getSelectedItem();
            if (selected == null) {
                selected = combo.getConverter().fromString(combo.getEditor().getText());
            }
            if (selected == null) {
                viewModel.cancelDraft(row.id());
                cancelEdit();
                return;
            }
            try {
                if (side == Side.INSIDE) {
                    viewModel.applyInsideColor(row.id(), selected.id());
                } else {
                    viewModel.applyOutsideColor(row.id(), selected.id());
                }
                commitEdit(selected.code());
            } catch (RuntimeException ex) {
                viewModel.cancelDraft(row.id());
                if (onError != null) {
                    onError.run();
                }
                cancelEdit();
            }
        }

        private void cleanupAfterEdit() {
            combo = null;
            setGraphic(null);
            ColorResolutionRow row = getTableRow() == null ? null : getTableRow().getItem();
            if (row != null) {
                String display = side == Side.INSIDE ? viewModel.insideDisplay(row) : viewModel.outsideDisplay(row);
                setText(display);
            }
        }

        private UUID draftOrCatalogInside(ColorResolutionRow row) {
            UUID draft = viewModel.draftInsideId(row.id());
            if (draft != null) {
                return draft;
            }
            return row.catalogPair() == null ? null : row.catalogPair().insideColorId();
        }

        private UUID draftOrCatalogOutside(ColorResolutionRow row) {
            UUID draft = viewModel.draftOutsideId(row.id());
            if (draft != null) {
                return draft;
            }
            return row.catalogPair() == null ? null : row.catalogPair().outsideColorId();
        }

        private static void selectColor(ComboBox<ColorDto> combo, UUID colorId) {
            ObservableList<ColorDto> items = combo.getItems();
            int sourceSize =
                    items instanceof FilteredList<?> filtered
                            ? filtered.getSource().size()
                            : items.size();
            for (int i = 0; i < sourceSize; i++) {
                ColorDto color =
                        items instanceof FilteredList<?> filtered
                                ? (ColorDto) filtered.getSource().get(i)
                                : items.get(i);
                if (color.id().equals(colorId)) {
                    combo.getSelectionModel().select(color);
                    combo.getEditor().setText(color.code());
                    return;
                }
            }
        }
    }
}