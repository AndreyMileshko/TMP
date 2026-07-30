package com.tmp.ui.shell.screen.orderspecificationeditor;

import com.tmp.ui.shell.navigation.ViewModelAware;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/**
 * Item Specification editor FXML controller. Document-driven; no Spring imports.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX Controller retains ViewModel for FXML wiring")
public final class OrderItemSpecificationEditorController
        implements ViewModelAware<OrderItemSpecificationEditorViewModel> {

    @FXML
    private Label titleLabel;

    @FXML
    private Label revisionNumberLabel;

    @FXML
    private Label revisionStatusLabel;

    @FXML
    private TextField orderedQuantityField;

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
    private TextField materialCodeField;

    @FXML
    private TextField materialNameField;

    @FXML
    private TextField colorField;

    @FXML
    private TextField lengthMmField;

    @FXML
    private TextField lineQuantityField;

    @FXML
    private TextField unitOfMeasureField;

    @FXML
    private Button addLineButton;

    @FXML
    private Button updateLineButton;

    @FXML
    private Button deleteLineButton;

    @FXML
    private Button moveUpButton;

    @FXML
    private Button moveDownButton;

    @FXML
    private Button clearLinesButton;

    @FXML
    private Button saveDraftButton;

    @FXML
    private Button postButton;

    @FXML
    private Button backButton;

    @FXML
    private Label successLabel;

    @FXML
    private Label warningLabel;

    @FXML
    private Label errorLabel;

    @Override
    public void setViewModel(OrderItemSpecificationEditorViewModel viewModel) {
        titleLabel.textProperty().bind(viewModel.titleProperty());
        revisionNumberLabel.textProperty().bind(viewModel.revisionNumberTextProperty());
        revisionStatusLabel.textProperty().bind(viewModel.revisionStatusTextProperty());
        orderedQuantityField.textProperty().bindBidirectional(viewModel.orderedQuantityProperty());
        orderedQuantityField.disableProperty().bind(viewModel.editableProperty().not());

        linesTable.setItems(viewModel.lines());
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

        materialCodeField.textProperty().bindBidirectional(viewModel.editMaterialCodeProperty());
        materialNameField.textProperty().bindBidirectional(viewModel.editMaterialNameProperty());
        colorField.textProperty().bindBidirectional(viewModel.editColorProperty());
        lengthMmField.textProperty().bindBidirectional(viewModel.editLengthMmProperty());
        lineQuantityField.textProperty().bindBidirectional(viewModel.editQuantityProperty());
        unitOfMeasureField.textProperty().bindBidirectional(viewModel.editUnitOfMeasureProperty());

        materialCodeField.disableProperty().bind(viewModel.editableProperty().not());
        materialNameField.disableProperty().bind(viewModel.editableProperty().not());
        colorField.disableProperty().bind(viewModel.editableProperty().not());
        lengthMmField.disableProperty().bind(viewModel.editableProperty().not());
        lineQuantityField.disableProperty().bind(viewModel.editableProperty().not());
        unitOfMeasureField.disableProperty().bind(viewModel.editableProperty().not());

        addLineButton.disableProperty().bind(viewModel.canAddLineProperty().not());
        updateLineButton.disableProperty().bind(viewModel.canUpdateLineProperty().not());
        deleteLineButton.disableProperty().bind(viewModel.canDeleteLineProperty().not());
        moveUpButton.disableProperty().bind(viewModel.canMoveUpProperty().not());
        moveDownButton.disableProperty().bind(viewModel.canMoveDownProperty().not());
        clearLinesButton.disableProperty().bind(viewModel.canClearLinesProperty().not());
        saveDraftButton.disableProperty().bind(viewModel.canSaveDraftProperty().not());
        postButton.disableProperty().bind(viewModel.canPostProperty().not());

        addLineButton.setOnAction(e -> viewModel.addLine());
        updateLineButton.setOnAction(e -> viewModel.updateSelectedLine());
        deleteLineButton.setOnAction(e -> viewModel.deleteSelectedLine());
        moveUpButton.setOnAction(e -> viewModel.moveSelectedUp());
        moveDownButton.setOnAction(e -> viewModel.moveSelectedDown());
        clearLinesButton.setOnAction(e -> viewModel.clearLines());
        saveDraftButton.setOnAction(e -> viewModel.saveDraft());
        postButton.setOnAction(e -> viewModel.postDocument());
        backButton.setOnAction(e -> viewModel.backToItem());

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
}
