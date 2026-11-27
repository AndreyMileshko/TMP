package com.tmp.ui.shell.screen.orderimport.colors;

import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.reference.api.ColorDto;
import com.tmp.ui.shell.theme.TmpTheme;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Window;

/** Modal unified SO7 color resolution window (COL-041A). */
public final class ColorResolutionDialog {

    private static final String DIALOG_STYLESHEET =
            "com/tmp/ui/shell/screen/orderimport/colors/tmp-color-resolution-dialog.css";
    private static final double SCREEN_WIDTH_FRACTION = 0.90;
    private static final double SCREEN_HEIGHT_FRACTION = 0.85;
    private static final double DIALOG_MIN_WIDTH = 720;
    private static final double DIALOG_MIN_HEIGHT = 480;

    /** Outcome of the dialog: apply with rebuilt batches or cancel without changes. */
    @SuppressFBWarnings(
            value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
            justification = "rebuilt batches are immutable copies from ColorResolutionSession.rebuild()")
    public record Result(boolean applied, ColorResolutionSession session, List<OrderImportBatch> rebuiltBatches) {}

    private ColorResolutionDialog() {}

    public static Optional<Result> show(Window owner, ColorResolutionSession initialSession) {
        Objects.requireNonNull(initialSession, "initialSession");
        ColorResolutionDialogViewModel viewModel = new ColorResolutionDialogViewModel(initialSession);

        Dialog<Result> dialog = new Dialog<>();
        if (owner != null && owner.getScene() != null) {
            dialog.initOwner(owner);
        }
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Разрешение цветов СО7");
        dialog.setHeaderText(null);
        dialog.setResizable(true);

        DialogPane dialogPane = dialog.getDialogPane();
        TmpTheme.apply(dialogPane);
        dialogPane.getStyleClass().add("tmp-dialog");
        dialogPane.setMinWidth(DIALOG_MIN_WIDTH);
        dialogPane.setMinHeight(DIALOG_MIN_HEIGHT);
        applyDialogStylesheet(dialogPane);

        ButtonType applyType = new ButtonType("Применить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(applyType, cancelType);

        Label conflictWarning = new Label(viewModel.mappingSaveConflictWarning());
        conflictWarning.getStyleClass().add("tmp-text-warning");
        conflictWarning.setWrapText(true);
        conflictWarning.setVisible(false);
        conflictWarning.setManaged(false);

        TableView<ColorResolutionRow> table = new TableView<>();
        table.getStyleClass().add("tmp-color-resolution-table");
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(36);
        table.setPlaceholder(new Region());
        table.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.SINGLE);
        table.setRowFactory(tv -> colorResolutionRow(viewModel));

        ComboBox<ColorDto> massInside = ColorPairCellEditors.createSearchableColorCombo(initialSession.activeColors());
        ComboBox<ColorDto> massOutside = ColorPairCellEditors.createSearchableColorCombo(initialSession.activeColors());
        massInside.setPrefWidth(160);
        massOutside.setPrefWidth(160);
        massInside.setMinWidth(140);
        massOutside.setMinWidth(140);

        Button applyPairHighlighted = new Button("Применить пару к выделенным");
        Button applyPairBySource = new Button("Применить пару по тексту СО7");
        applyPairHighlighted.getStyleClass().add("tmp-button-primary");
        applyPairBySource.getStyleClass().add("tmp-button-primary");
        applyPairHighlighted.setTooltip(
                new Tooltip("Применить выбранную пару каталога TMP ко всем строкам, отмеченным чекбоксом"));
        applyPairBySource.setTooltip(
                new Tooltip(
                        "Применить пару ко всем выделенным строкам с одинаковым исходным текстом СО7"));

        Button keepSourceHighlighted = new Button("Оставить исходный текст");
        Button keepSourceAll = new Button("Оставить исходный для всех");
        Button customColorButton = new Button("Свой цвет");
        keepSourceHighlighted.getStyleClass().add("tmp-button-secondary");
        keepSourceAll.getStyleClass().add("tmp-button-secondary");
        customColorButton.getStyleClass().add("tmp-button-secondary");
        keepSourceAll.setTooltip(
                new Tooltip("Использовать исходный текст СО7 для всех строк, которые ещё требуют выбора"));
        customColorButton.setTooltip(new Tooltip("Задать произвольный итоговый цвет для выделенных строк"));

        TextField searchField = new TextField();
        searchField.setPromptText("Поиск по тексту СО7");
        searchField.setPrefWidth(220);
        searchField.setMinWidth(160);

        ToggleButton filterNeeds = new ToggleButton("Требуют выбора");
        ToggleButton filterAll = new ToggleButton("Все");
        filterNeeds.setSelected(true);
        filterNeeds.getStyleClass().add("tmp-button-secondary");
        filterAll.getStyleClass().add("tmp-button-secondary");
        filterNeeds.setMinWidth(Region.USE_PREF_SIZE);
        filterAll.setMinWidth(Region.USE_PREF_SIZE);
        filterNeeds.setMaxWidth(Double.MAX_VALUE);
        filterAll.setMaxWidth(Double.MAX_VALUE);
        ToggleGroup filterGroup = new ToggleGroup();
        filterNeeds.setToggleGroup(filterGroup);
        filterAll.setToggleGroup(filterGroup);

        Label emptyFilterLabel = new Label("Нет строк по текущему фильтру");
        emptyFilterLabel.getStyleClass().add("tmp-text-muted");
        StackPane tableHost = new StackPane(table, emptyFilterLabel);
        StackPane.setAlignment(emptyFilterLabel, Pos.CENTER);
        VBox.setVgrow(tableHost, Priority.ALWAYS);

        Label sessionCounters = new Label();
        sessionCounters.getStyleClass().add("tmp-col-res-counters");
        HBox legend =
                new HBox(
                        16,
                        legendItem("tmp-col-res-legend-swatch-matched", "Сопоставлено"),
                        legendItem("tmp-col-res-legend-swatch-needs", "Требует выбора"),
                        legendItem("tmp-col-res-legend-swatch-manual", "Изменено вручную"));

        HBox metaRow = new HBox(24, sessionCounters, legend);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        table.getColumns().addAll(buildColumns(viewModel, table));

        Runnable refreshAll =
                () -> {
                    List<ColorResolutionRow> rows = new ArrayList<>(viewModel.visibleRows());
                    table.getItems().setAll(rows);
                    emptyFilterLabel.setVisible(rows.isEmpty());
                    emptyFilterLabel.setManaged(rows.isEmpty());

                    sessionCounters.setText(
                            "Требуют выбора: "
                                    + viewModel.countNeedsChoice()
                                    + "    Сопоставлено: "
                                    + viewModel.countMatched()
                                    + "    Изменено вручную: "
                                    + viewModel.countManuallyChanged());

                    conflictWarning.setVisible(viewModel.hasMappingSaveConflicts());
                    conflictWarning.setManaged(viewModel.hasMappingSaveConflicts());

                    applyPairHighlighted.setDisable(
                            !viewModel.canApplyMassPairToHighlighted(
                                    selectedColorId(massInside), selectedColorId(massOutside)));
                    applyPairBySource.setDisable(
                            !viewModel.canApplyMassPairToSourceKey(
                                    selectedColorId(massInside), selectedColorId(massOutside)));
                    keepSourceHighlighted.setDisable(!viewModel.canKeepSourceTextForHighlighted());
                    customColorButton.setDisable(!viewModel.canKeepSourceTextForHighlighted());

                    Button applyButton = (Button) dialogPane.lookupButton(applyType);
                    if (applyButton != null) {
                        applyButton.setDisable(!viewModel.isApplyEnabled());
                    }
                    table.refresh();
                };

        viewModel.setOnChange(refreshAll);

        filterNeeds.setOnAction(
                event -> viewModel.setFilter(ColorResolutionDialogViewModel.DisplayFilter.NEEDS_CHOICE));
        filterAll.setOnAction(
                event -> viewModel.setFilter(ColorResolutionDialogViewModel.DisplayFilter.ALL));
        searchField.textProperty().addListener((obs, old, value) -> viewModel.setSearchText(value));

        Runnable massPairChanged = refreshAll;
        massInside.valueProperty().addListener((obs, old, value) -> massPairChanged.run());
        massOutside.valueProperty().addListener((obs, old, value) -> massPairChanged.run());
        massInside.getEditor().textProperty().addListener((obs, old, value) -> massPairChanged.run());
        massOutside.getEditor().textProperty().addListener((obs, old, value) -> massPairChanged.run());

        applyPairHighlighted.setOnAction(
                event ->
                        viewModel.applyMassPairToHighlighted(
                                selectedColorId(massInside), selectedColorId(massOutside)));
        applyPairBySource.setOnAction(
                event ->
                        viewModel.applyMassPairToSourceKey(
                                selectedColorId(massInside), selectedColorId(massOutside)));
        keepSourceHighlighted.setOnAction(event -> viewModel.keepSourceTextForHighlighted());
        keepSourceAll.setOnAction(event -> viewModel.keepSourceTextForAllNeedsChoice());
        customColorButton.setOnAction(
                event -> {
                    TextInputDialog customDialog = new TextInputDialog();
                    customDialog.initOwner(dialog.getDialogPane().getScene().getWindow());
                    customDialog.setTitle("Свой цвет");
                    customDialog.setHeaderText(null);
                    customDialog.setContentText("Введите итоговый цвет:");
                    DialogPane customPane = customDialog.getDialogPane();
                    TmpTheme.apply(customPane);
                    Optional<String> value = customDialog.showAndWait();
                    if (value.isPresent()) {
                        viewModel.applyCustomColorToHighlighted(value.get());
                    }
                });

        FlowPane filterLevel =
                new FlowPane(8, 8, buildToolbarGroup(filterNeeds, filterAll, searchField));
        filterLevel.getStyleClass().add("tmp-toolbar");
        filterLevel.setAlignment(Pos.CENTER_LEFT);

        FlowPane pairLevel =
                new FlowPane(
                        8,
                        8,
                        buildToolbarGroup(
                                labeled("Внутри TMP", massInside), labeled("Снаружи TMP", massOutside)));
        pairLevel.getStyleClass().add("tmp-toolbar");
        pairLevel.setAlignment(Pos.CENTER_LEFT);

        FlowPane applyLevel =
                new FlowPane(8, 8, buildToolbarGroup(applyPairHighlighted, applyPairBySource));
        applyLevel.getStyleClass().add("tmp-toolbar");
        applyLevel.setAlignment(Pos.CENTER_LEFT);

        HBox primaryToolbar = new HBox(12, filterLevel, toolbarSeparator(), pairLevel, toolbarSeparator(), applyLevel);
        primaryToolbar.setAlignment(Pos.CENTER_LEFT);

        FlowPane secondaryToolbar =
                new FlowPane(8, 8, keepSourceHighlighted, keepSourceAll, customColorButton);
        secondaryToolbar.getStyleClass().add("tmp-toolbar");
        secondaryToolbar.setAlignment(Pos.CENTER_LEFT);

        VBox content =
                new VBox(
                        8,
                        primaryToolbar,
                        secondaryToolbar,
                        metaRow,
                        tableHost,
                        conflictWarning);
        content.setPadding(new Insets(8, 8, 0, 8));
        dialogPane.setContent(content);

        Button applyButton = (Button) dialogPane.lookupButton(applyType);
        if (applyButton != null) {
            applyButton.setDefaultButton(false);
            applyButton.getStyleClass().add("tmp-button-primary");
        }
        Button cancelButton = (Button) dialogPane.lookupButton(cancelType);
        if (cancelButton != null) {
            cancelButton.getStyleClass().add("tmp-button-secondary");
        }

        dialog.setOnShowing(
                event -> {
                    Rectangle2D bounds = visualBounds(owner);
                    double width = Math.max(DIALOG_MIN_WIDTH, bounds.getWidth() * SCREEN_WIDTH_FRACTION);
                    double height = Math.max(DIALOG_MIN_HEIGHT, bounds.getHeight() * SCREEN_HEIGHT_FRACTION);
                    dialogPane.setPrefWidth(width);
                    dialogPane.setPrefHeight(height);
                    Window window = dialogPane.getScene().getWindow();
                    window.setX(bounds.getMinX() + (bounds.getWidth() - width) / 2);
                    window.setY(bounds.getMinY() + (bounds.getHeight() - height) / 2);
                });

        dialog.setOnCloseRequest(
                event -> {
                    if (dialog.getResult() == null) {
                        dialog.setResult(new Result(false, null, null));
                    }
                });

        dialog.setResultConverter(
                button -> {
                    if (button == applyType) {
                        if (!viewModel.isApplyEnabled()) {
                            return new Result(false, null, null);
                        }
                        List<OrderImportBatch> rebuilt = viewModel.session().rebuild();
                        return new Result(true, viewModel.session(), rebuilt);
                    }
                    return new Result(false, null, null);
                });

        refreshAll.run();
        return dialog.showAndWait();
    }

    private static TableRow<ColorResolutionRow> colorResolutionRow(ColorResolutionDialogViewModel viewModel) {
        return new TableRow<>() {
            @Override
            protected void updateItem(ColorResolutionRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass()
                        .removeAll(
                                "tmp-col-res-row-matched",
                                "tmp-col-res-row-needs",
                                "tmp-col-res-row-manual",
                                "tmp-col-res-row-highlighted");
                if (empty || item == null) {
                    return;
                }
                switch (item.userState()) {
                    case MATCHED -> getStyleClass().add("tmp-col-res-row-matched");
                    case NEEDS_CHOICE -> getStyleClass().add("tmp-col-res-row-needs");
                    case MANUALLY_CHANGED -> getStyleClass().add("tmp-col-res-row-manual");
                    default -> { /* no-op */ }
                }
                if (viewModel.isRowHighlighted(item.id())) {
                    getStyleClass().add("tmp-col-res-row-highlighted");
                }
            }
        };
    }

    private static HBox buildToolbarGroup(javafx.scene.Node... nodes) {
        HBox box = new HBox(8, nodes);
        box.getStyleClass().add("tmp-col-res-toolbar-group");
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Separator toolbarSeparator() {
        Separator separator = new Separator(javafx.geometry.Orientation.VERTICAL);
        separator.setPrefHeight(28);
        separator.setMaxHeight(28);
        return separator;
    }

    private static HBox labeled(String caption, javafx.scene.Node control) {
        Label label = new Label(caption);
        label.getStyleClass().add("tmp-form-label");
        return new HBox(6, label, control);
    }

    private static HBox legendItem(String swatchClass, String text) {
        Region swatch = new Region();
        swatch.getStyleClass().addAll("tmp-col-res-legend-swatch", swatchClass);
        Label label = new Label(text);
        label.getStyleClass().add("tmp-text-small");
        return new HBox(6, swatch, label);
    }

    private static void applyDialogStylesheet(DialogPane dialogPane) {
        URL url = ColorResolutionDialog.class.getClassLoader().getResource(DIALOG_STYLESHEET);
        if (url != null) {
            String external = url.toExternalForm();
            if (!dialogPane.getStylesheets().contains(external)) {
                dialogPane.getStylesheets().add(external);
            }
        }
    }

    private static Rectangle2D visualBounds(Window owner) {
        if (owner != null) {
            for (Screen screen : Screen.getScreens()) {
                if (screen.getBounds().contains(owner.getX(), owner.getY())) {
                    return screen.getVisualBounds();
                }
            }
        }
        return Screen.getPrimary().getVisualBounds();
    }

    private static UUID selectedColorId(ComboBox<ColorDto> combo) {
        ColorDto selected = combo.getSelectionModel().getSelectedItem();
        if (selected != null) {
            return selected.id();
        }
        ColorDto fromEditor = combo.getConverter().fromString(combo.getEditor().getText());
        return fromEditor == null ? null : fromEditor.id();
    }

    private static List<TableColumn<ColorResolutionRow, ?>> buildColumns(
            ColorResolutionDialogViewModel viewModel, TableView<ColorResolutionRow> table) {
        List<TableColumn<ColorResolutionRow, ?>> columns = new ArrayList<>();

        Runnable showPairError =
                () -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Ошибка выбора пары");
                    alert.setHeaderText(null);
                    alert.setContentText("Не удалось сформировать пару цветов. Проверьте выбранные значения.");
                    alert.showAndWait();
                };

        TableColumn<ColorResolutionRow, Void> selectCol = new TableColumn<>();
        selectCol.setText(null);
        Region selectHeader = new Region();
        selectHeader.getStyleClass().add("tmp-col-res-header-empty");
        selectCol.setGraphic(selectHeader);
        selectCol.setMinWidth(36);
        selectCol.setPrefWidth(40);
        selectCol.setMaxWidth(48);
        selectCol.setCellFactory(
                column ->
                        new TableCell<>() {
                            private final CheckBox checkBox = new CheckBox();

                            {
                                checkBox.setOnAction(
                                        event -> {
                                            ColorResolutionRow row =
                                                    getTableRow() == null ? null : getTableRow().getItem();
                                            if (row != null) {
                                                viewModel.setRowHighlighted(row.id(), checkBox.isSelected());
                                            }
                                        });
                            }

                            @Override
                            protected void updateItem(Void item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                                    setGraphic(null);
                                    return;
                                }
                                ColorResolutionRow row = getTableRow().getItem();
                                checkBox.setSelected(viewModel.isRowHighlighted(row.id()));
                                setGraphic(checkBox);
                            }
                        });
        columns.add(selectCol);

        columns.add(textColumn("Заказ", row -> row.orderNumber(), 90, 110));
        columns.add(productColumn(viewModel));

        TableColumn<ColorResolutionRow, String> lineCol =
                textColumn(
                        "Стр.",
                        row -> String.valueOf(row.specificationLineNumber()),
                        48,
                        52);
        lineCol.setMaxWidth(58);
        setColumnHeaderTooltip(lineCol, "Номер строки спецификации");
        columns.add(lineCol);

        columns.add(textColumn("Артикул", row -> row.materialCode(), 90, 120));
        columns.add(textColumn("Наименование материала", row -> row.materialName(), 140, 200));
        columns.add(textColumn("Исходный текст СО7", row -> row.sourceText(), 120, 160));

        TableColumn<ColorResolutionRow, String> insideCol = new TableColumn<>("Внутри TMP");
        insideCol.setMinWidth(120);
        insideCol.setPrefWidth(150);
        insideCol.setEditable(true);
        insideCol.setCellValueFactory(data -> new SimpleStringProperty(viewModel.insideDisplay(data.getValue())));
        insideCol.setCellFactory(
                ColorPairCellEditors.colorCellFactory(
                        viewModel, ColorPairCellEditors.Side.INSIDE, showPairError));

        TableColumn<ColorResolutionRow, String> outsideCol = new TableColumn<>("Снаружи TMP");
        outsideCol.setMinWidth(120);
        outsideCol.setPrefWidth(150);
        outsideCol.setEditable(true);
        outsideCol.setCellValueFactory(data -> new SimpleStringProperty(viewModel.outsideDisplay(data.getValue())));
        outsideCol.setCellFactory(
                ColorPairCellEditors.colorCellFactory(
                        viewModel, ColorPairCellEditors.Side.OUTSIDE, showPairError));

        TableColumn<ColorResolutionRow, String> pairGroup = new TableColumn<>("Цветовая пара");
        pairGroup.getColumns().addAll(insideCol, outsideCol);
        columns.add(pairGroup);

        TableColumn<ColorResolutionRow, String> finalCol = new TableColumn<>("Итоговый цвет");
        finalCol.setMinWidth(120);
        finalCol.setPrefWidth(170);
        finalCol.setCellValueFactory(data -> new SimpleStringProperty(nullToEmpty(data.getValue().finalColor())));
        finalCol.setCellFactory(
                column ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                                    setText(null);
                                    getStyleClass().remove("tmp-input-error");
                                    return;
                                }
                                ColorResolutionRow row = getTableRow().getItem();
                                setText(nullToEmpty(item));
                                String message = viewModel.constraintErrorMessage(row);
                                if (!message.isEmpty()) {
                                    if (!getStyleClass().contains("tmp-input-error")) {
                                        getStyleClass().add("tmp-input-error");
                                    }
                                } else {
                                    getStyleClass().remove("tmp-input-error");
                                }
                                installTooltip(this, nullToEmpty(item));
                            }
                        });
        columns.add(finalCol);

        if (viewModel.showSaveMappingColumn()) {
            TableColumn<ColorResolutionRow, Void> saveCol = new TableColumn<>();
            saveCol.setMinWidth(96);
            saveCol.setPrefWidth(104);
            saveCol.setMaxWidth(112);
            Label saveHeader = wrappedHeader("Сохр. в", "справ.");
            Tooltip.install(
                    saveHeader,
                    new Tooltip(
                            "После успешного импорта сохранить соответствие исходного текста СО7 паре TMP"));
            saveCol.setGraphic(saveHeader);
            saveCol.setCellFactory(
                    column ->
                            new TableCell<>() {
                                private final CheckBox checkBox = new CheckBox();

                                {
                                    checkBox.setOnAction(
                                            event -> {
                                                ColorResolutionRow row =
                                                        getTableRow() == null ? null : getTableRow().getItem();
                                                if (row != null && row.saveMappingAvailable()) {
                                                    viewModel.toggleSaveMapping(row.id(), checkBox.isSelected());
                                                }
                                            });
                                }

                                @Override
                                protected void updateItem(Void item, boolean empty) {
                                    super.updateItem(item, empty);
                                    if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                                        setGraphic(null);
                                        setTooltip(null);
                                        return;
                                    }
                                    ColorResolutionRow row = getTableRow().getItem();
                                    if (!row.saveMappingAvailable()) {
                                        checkBox.setDisable(true);
                                        checkBox.setSelected(false);
                                        String tip = viewModel.saveMappingUnavailableTooltip(row);
                                        setTooltip(tip.isEmpty() ? null : new Tooltip(tip));
                                    } else {
                                        checkBox.setDisable(false);
                                        checkBox.setSelected(row.saveMappingSelected());
                                        setTooltip(null);
                                    }
                                    setGraphic(checkBox);
                                }
                            });
            columns.add(saveCol);
        }

        TableColumn<ColorResolutionRow, String> errorCol = new TableColumn<>("Ошибка");
        errorCol.setMinWidth(72);
        errorCol.setPrefWidth(120);
        errorCol.setCellValueFactory(
                data -> new SimpleStringProperty(viewModel.constraintErrorMessage(data.getValue())));
        errorCol.setCellFactory(
                column ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty) {
                                    setText(null);
                                    setTooltip(null);
                                    getStyleClass().remove("tmp-text-error");
                                    return;
                                }
                                if (item == null || item.isEmpty()) {
                                    setText(null);
                                    setTooltip(null);
                                    getStyleClass().remove("tmp-text-error");
                                    return;
                                }
                                setText(item);
                                installTooltip(this, item);
                                if (!getStyleClass().contains("tmp-text-error")) {
                                    getStyleClass().add("tmp-text-error");
                                }
                            }
                        });
        columns.add(errorCol);

        table.setEditable(true);

        return columns;
    }

    private static TableColumn<ColorResolutionRow, String> productColumn(ColorResolutionDialogViewModel viewModel) {
        TableColumn<ColorResolutionRow, String> column = new TableColumn<>("Изделие");
        column.setMinWidth(180);
        column.setPrefWidth(260);
        column.setCellValueFactory(
                data -> new SimpleStringProperty(nullToEmpty(viewModel.productLineLabel(data.getValue()))));
        column.setCellFactory(
                col ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                                    setText(null);
                                    setTooltip(null);
                                    return;
                                }
                                ColorResolutionRow row = getTableRow().getItem();
                                setText(nullToEmpty(viewModel.productLineLabel(row)));
                                String tip = viewModel.productLineTooltip(row);
                                setTooltip(tip.isBlank() ? null : new Tooltip(tip));
                            }
                        });
        return column;
    }

    private static Label wrappedHeader(String line1, String line2) {
        Label header = new Label(line1 + "\n" + line2);
        header.getStyleClass().addAll("tmp-text-small", "tmp-col-res-header-wrap");
        header.setWrapText(false);
        header.setTextOverrun(OverrunStyle.CLIP);
        return header;
    }

    private static void setColumnHeaderTooltip(TableColumn<?, ?> column, String text) {
        Tooltip tooltip = new Tooltip(text);
        if (column.getGraphic() == null) {
            Label header = new Label(column.getText());
            header.getStyleClass().add("tmp-col-res-column-header");
            header.setTextOverrun(OverrunStyle.CLIP);
            column.setText(null);
            column.setGraphic(header);
        }
        Tooltip.install(column.getGraphic(), tooltip);
    }

    private interface RowText {
        String apply(ColorResolutionRow row);
    }

    private static TableColumn<ColorResolutionRow, String> textColumn(
            String title, RowText accessor, double min, double pref) {
        TableColumn<ColorResolutionRow, String> column = new TableColumn<>(title);
        column.setMinWidth(min);
        column.setPrefWidth(pref);
        column.setCellValueFactory(data -> new SimpleStringProperty(nullToEmpty(accessor.apply(data.getValue()))));
        column.setCellFactory(
                col ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(empty ? null : item);
                                installTooltip(this, empty ? null : item);
                            }
                        });
        return column;
    }

    private static void installTooltip(TableCell<?, ?> cell, String text) {
        if (text == null || text.isBlank()) {
            cell.setTooltip(null);
            return;
        }
        cell.setTooltip(new Tooltip(text));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}