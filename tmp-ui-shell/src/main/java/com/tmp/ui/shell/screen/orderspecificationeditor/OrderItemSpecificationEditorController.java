package com.tmp.ui.shell.screen.orderspecificationeditor;

import com.tmp.ui.shell.navigation.ViewModelAware;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.stage.Window;

/**
 * Item Specification editor FXML controller. Dialog-driven mutations; no Spring imports.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX Controller retains ViewModel for FXML wiring")
public final class OrderItemSpecificationEditorController
        implements ViewModelAware<OrderItemSpecificationEditorViewModel> {

    @FXML
    private Label titleLabel;

    @FXML
    private Label orderedQuantityLabel;

    @FXML
    private Button addMaterialButton;

    @FXML
    private TableView<SpecificationLineRow> linesTable;

    @FXML
    private TableColumn<SpecificationLineRow, String> materialCodeColumn;

    @FXML
    private TableColumn<SpecificationLineRow, String> materialNameColumn;

    @FXML
    private TableColumn<SpecificationLineRow, String> colorColumn;

    @FXML
    private TableColumn<SpecificationLineRow, String> lengthMmColumn;

    @FXML
    private TableColumn<SpecificationLineRow, String> lineQuantityColumn;

    @FXML
    private TableColumn<SpecificationLineRow, String> unitOfMeasureColumn;

    @FXML
    private Label successLabel;

    @FXML
    private Label warningLabel;

    @FXML
    private Label errorLabel;

    private OrderItemSpecificationEditorViewModel viewModel;
    private ContextMenu rowContextMenu;

    @Override
    public void setViewModel(OrderItemSpecificationEditorViewModel viewModel) {
        this.viewModel = viewModel;
        titleLabel.textProperty().bind(viewModel.titleProperty());
        orderedQuantityLabel.textProperty().bind(viewModel.orderedQuantityTextProperty());

        linesTable.setItems(viewModel.lines());
        linesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        materialCodeColumn.setMaxWidth(Double.MAX_VALUE);
        materialNameColumn.setMaxWidth(Double.MAX_VALUE);

        linesTable
                .getSelectionModel()
                .selectedIndexProperty()
                .addListener(
                        (obs, oldValue, newValue) ->
                                viewModel.selectLine(newValue == null ? -1 : newValue.intValue()));
        viewModel
                .selectedIndexProperty()
                .addListener(
                        (obs, oldValue, newValue) -> {
                            int index = newValue == null ? -1 : newValue.intValue();
                            if (index < 0) {
                                linesTable.getSelectionModel().clearSelection();
                            } else if (linesTable.getSelectionModel().getSelectedIndex() != index) {
                                linesTable.getSelectionModel().select(index);
                            }
                        });

        materialCodeColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().materialCode()));
        materialNameColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().materialName()));
        colorColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().color()));
        lengthMmColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().lengthMm()));
        lineQuantityColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().lineQuantity()));
        unitOfMeasureColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().unitOfMeasure()));

        addMaterialButton.disableProperty().bind(viewModel.canAddLineProperty().not());
        addMaterialButton.visibleProperty().bind(viewModel.editableProperty());
        addMaterialButton.managedProperty().bind(addMaterialButton.visibleProperty());
        addMaterialButton.setOnAction(e -> onAddMaterial());

        rowContextMenu = buildContextMenu();
        linesTable.setRowFactory(
                table -> {
                    TableRow<SpecificationLineRow> row = new TableRow<>();
                    row.setOnMouseClicked(
                            event -> {
                                if (event.getButton() == MouseButton.PRIMARY
                                        && event.getClickCount() == 2
                                        && !row.isEmpty()
                                        && viewModel.editableProperty().get()) {
                                    viewModel.selectLine(row.getIndex());
                                    onEditSelected();
                                }
                            });
                    row.contextMenuProperty()
                            .bind(
                                    Bindings.createObjectBinding(
                                            () -> {
                                                if (row.isEmpty()
                                                        || !viewModel.editableProperty().get()) {
                                                    return null;
                                                }
                                                return rowContextMenu;
                                            },
                                            row.emptyProperty(),
                                            viewModel.editableProperty()));
                    return row;
                });
        linesTable.setOnKeyPressed(
                event -> {
                    if (event.getCode() == KeyCode.ENTER
                            && viewModel.selectedLineProperty().get() != null
                            && viewModel.editableProperty().get()) {
                        onEditSelected();
                    }
                });

        successLabel.textProperty().bind(viewModel.successMessageProperty());
        successLabel
                .visibleProperty()
                .bind(
                        Bindings.createBooleanBinding(
                                () -> {
                                    String message = viewModel.successMessageProperty().get();
                                    return message != null && !message.isBlank();
                                },
                                viewModel.successMessageProperty()));
        successLabel.managedProperty().bind(successLabel.visibleProperty());

        warningLabel.textProperty().bind(viewModel.warningMessageProperty());
        warningLabel
                .visibleProperty()
                .bind(
                        Bindings.createBooleanBinding(
                                () -> {
                                    String message = viewModel.warningMessageProperty().get();
                                    return message != null && !message.isBlank();
                                },
                                viewModel.warningMessageProperty()));
        warningLabel.managedProperty().bind(warningLabel.visibleProperty());

        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel
                .visibleProperty()
                .bind(
                        Bindings.createBooleanBinding(
                                () -> {
                                    String message = viewModel.errorMessageProperty().get();
                                    return message != null && !message.isBlank();
                                },
                                viewModel.errorMessageProperty()));
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
    }

    private ContextMenu buildContextMenu() {
        MenuItem edit = new MenuItem("Изменить");
        edit.setOnAction(e -> onEditSelected());
        MenuItem delete = new MenuItem("Удалить");
        delete.setOnAction(e -> onDeleteSelected());
        MenuItem moveUp = new MenuItem("Переместить выше");
        moveUp.setOnAction(e -> viewModel.moveSelectedUp());
        MenuItem moveDown = new MenuItem("Переместить ниже");
        moveDown.setOnAction(e -> viewModel.moveSelectedDown());
        ContextMenu menu = new ContextMenu(edit, delete, moveUp, moveDown);
        menu.setOnShowing(
                e -> {
                    edit.setDisable(!viewModel.canUpdateLineProperty().get());
                    delete.setDisable(!viewModel.canDeleteLineProperty().get());
                    moveUp.setDisable(!viewModel.canMoveUpProperty().get());
                    moveDown.setDisable(!viewModel.canMoveDownProperty().get());
                });
        return menu;
    }

    private void onAddMaterial() {
        Window owner = ownerWindow();
        SpecificationLineDialogs.showAddDialog(owner).ifPresent(viewModel::addLine);
    }

    private void onEditSelected() {
        SpecificationLineRow selected = viewModel.selectedLineProperty().get();
        if (selected == null) {
            return;
        }
        Window owner = ownerWindow();
        SpecificationLineDialogs.showEditDialog(owner, selected)
                .ifPresent(viewModel::updateSelectedLine);
    }

    private void onDeleteSelected() {
        if (!viewModel.canDeleteLineProperty().get()) {
            return;
        }
        Window owner = ownerWindow();
        if (SpecificationLineDialogs.showDeleteConfirmation(owner)) {
            viewModel.deleteSelectedLine();
        }
    }

    private Window ownerWindow() {
        if (linesTable.getScene() != null) {
            return linesTable.getScene().getWindow();
        }
        return null;
    }
}
