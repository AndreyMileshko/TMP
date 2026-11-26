package com.tmp.ui.shell.screen.orderimport.colors;

import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.reference.api.ColorDto;
import com.tmp.ui.shell.theme.TmpTheme;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.TableCell;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/** Modal unified SO7 color resolution window (COL-041A). */
public final class ColorResolutionDialog {

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
    dialogPane.setMinWidth(960);
    dialogPane.setMinHeight(560);
    dialogPane.setPrefWidth(1200);
    dialogPane.setPrefHeight(720);

    ButtonType applyType = new ButtonType("Применить", ButtonBar.ButtonData.OK_DONE);
    ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
    dialogPane.getButtonTypes().addAll(applyType, cancelType);

    Label conflictWarning = new Label(viewModel.mappingSaveConflictWarning());
    conflictWarning.getStyleClass().add("tmp-text-warning");
    conflictWarning.setWrapText(true);
    conflictWarning.setVisible(false);
    conflictWarning.setManaged(false);

    TableView<ColorResolutionRow> table = new TableView<>();
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table.setFixedCellSize(36);
    table.setPlaceholder(new Region());
    table.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.SINGLE);

    ComboBox<ColorDto> massInside = ColorPairCellEditors.createSearchableColorCombo(initialSession.activeColors());
    ComboBox<ColorDto> massOutside = ColorPairCellEditors.createSearchableColorCombo(initialSession.activeColors());
    massInside.setPrefWidth(150);
    massOutside.setPrefWidth(150);
    massInside.setMinWidth(120);
    massOutside.setMinWidth(120);

    Button applyPairHighlighted = new Button("Применить пару к выделенным");
    Button applyPairBySource = new Button("Применить пару по тексту СО7");
    Button keepSourceHighlighted = new Button("Оставить исходный текст");
    Button keepSourceAll = new Button("Оставить исходный для всех");
    Button customColorButton = new Button("Свой цвет");

    TextField searchField = new TextField();
    searchField.setPromptText("Поиск по тексту СО7");
    searchField.setPrefWidth(200);

    ToggleButton filterNeeds = new ToggleButton("Требуют выбора");
    ToggleButton filterAll = new ToggleButton("Все");
    filterNeeds.setSelected(true);
    ToggleGroup filterGroup = new ToggleGroup();
    filterNeeds.setToggleGroup(filterGroup);
    filterAll.setToggleGroup(filterGroup);

    Label emptyFilterLabel = new Label("Нет строк по текущему фильтру");
    emptyFilterLabel.getStyleClass().add("tmp-text-muted");
    StackPane tableHost = new StackPane(table, emptyFilterLabel);
    StackPane.setAlignment(emptyFilterLabel, Pos.CENTER);
    VBox.setVgrow(tableHost, Priority.ALWAYS);

    table.getColumns().addAll(buildColumns(viewModel, table));

    Runnable refreshAll =
        () -> {
          List<ColorResolutionRow> rows = new ArrayList<>(viewModel.visibleRows());
          table.getItems().setAll(rows);
          emptyFilterLabel.setVisible(rows.isEmpty());
          emptyFilterLabel.setManaged(rows.isEmpty());

          conflictWarning.setVisible(viewModel.hasMappingSaveConflicts());
          conflictWarning.setManaged(viewModel.hasMappingSaveConflicts());

          applyPairHighlighted.setDisable(!viewModel.canApplyMassPairToHighlighted(selectedColorId(massInside), selectedColorId(massOutside)));
          applyPairBySource.setDisable(
              !viewModel.canApplyMassPairToSourceKey(selectedColorId(massInside), selectedColorId(massOutside)));
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
        event -> {
          viewModel.setFilter(ColorResolutionDialogViewModel.DisplayFilter.NEEDS_CHOICE);
        });
    filterAll.setOnAction(
        event -> {
          viewModel.setFilter(ColorResolutionDialogViewModel.DisplayFilter.ALL);
        });
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

    HBox filterRow =
        new HBox(
            8,
            filterNeeds,
            filterAll,
            new Label("Поиск:"),
            searchField,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            new Label("Внутри TMP:"),
            massInside,
            new Label("Снаружи TMP:"),
            massOutside,
            applyPairHighlighted,
            applyPairBySource);
    filterRow.setAlignment(Pos.CENTER_LEFT);

    HBox commandRow =
        new HBox(8, keepSourceHighlighted, keepSourceAll, customColorButton);
    commandRow.setAlignment(Pos.CENTER_LEFT);

    VBox content = new VBox(8, filterRow, commandRow, tableHost, conflictWarning);
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

    TableColumn<ColorResolutionRow, Void> selectCol = new TableColumn<>("Выбор");
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
                      ColorResolutionRow row = getTableRow() == null ? null : getTableRow().getItem();
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
    columns.add(textColumn("Изделие", viewModel::productLineLabel, 180, 260));
    columns.add(textColumn("№ строки", row -> String.valueOf(row.specificationLineNumber()), 56, 72));
    columns.add(textColumn("Артикул", row -> row.materialCode(), 90, 120));
    columns.add(textColumn("Наименование материала", row -> row.materialName(), 140, 200));
    columns.add(textColumn("Исходный текст СО7", row -> row.sourceText(), 120, 160));
    columns.add(textColumn("Состояние", row -> viewModel.userStateLabel(row.userState()), 120, 148));

    TableColumn<ColorResolutionRow, String> insideCol = new TableColumn<>("Внутри TMP");
    insideCol.setMinWidth(120);
    insideCol.setPrefWidth(150);
    insideCol.setEditable(true);
    insideCol.setCellValueFactory(data -> new SimpleStringProperty(viewModel.insideDisplay(data.getValue())));
    insideCol.setCellFactory(
        ColorPairCellEditors.colorCellFactory(
            viewModel, ColorPairCellEditors.Side.INSIDE, showPairError));
    columns.add(insideCol);

    TableColumn<ColorResolutionRow, String> outsideCol = new TableColumn<>("Снаружи TMP");
    outsideCol.setMinWidth(120);
    outsideCol.setPrefWidth(150);
    outsideCol.setEditable(true);
    outsideCol.setCellValueFactory(data -> new SimpleStringProperty(viewModel.outsideDisplay(data.getValue())));
    outsideCol.setCellFactory(
        ColorPairCellEditors.colorCellFactory(
            viewModel, ColorPairCellEditors.Side.OUTSIDE, showPairError));
    columns.add(outsideCol);

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
      TableColumn<ColorResolutionRow, Void> saveCol =
          new TableColumn<>("Сохранить сопоставление в справочнике");
      saveCol.setMinWidth(200);
      saveCol.setPrefWidth(240);
      saveCol.setCellFactory(
          column ->
              new TableCell<>() {
                private final CheckBox checkBox = new CheckBox();

                {
                  checkBox.setOnAction(
                      event -> {
                        ColorResolutionRow row = getTableRow() == null ? null : getTableRow().getItem();
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

    TableColumn<ColorResolutionRow, String> errorCol = new TableColumn<>("Ошибка проверки");
    errorCol.setMinWidth(180);
    errorCol.setPrefWidth(240);
    errorCol.setCellValueFactory(data -> new SimpleStringProperty(viewModel.constraintErrorMessage(data.getValue())));
    errorCol.setCellFactory(
        column ->
            new TableCell<>() {
              @Override
              protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                  setText(null);
                  getStyleClass().remove("tmp-text-error");
                  return;
                }
                setText(item == null || item.isEmpty() ? null : item);
                if (item != null && !item.isEmpty()) {
                  if (!getStyleClass().contains("tmp-text-error")) {
                    getStyleClass().add("tmp-text-error");
                  }
                } else {
                  getStyleClass().remove("tmp-text-error");
                }
              }
            });
    columns.add(errorCol);

    table.setEditable(true);

    return columns;
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