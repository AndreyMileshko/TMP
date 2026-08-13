package com.tmp.ui.shell.screen.warehouse;

import com.tmp.ui.shell.navigation.ViewModelAware;
import com.tmp.warehouse.api.WarehouseApi.ReservationLinkView;
import com.tmp.warehouse.api.WarehouseApi.StockView;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Warehouse workbench FXML controller. Navigation is left ListView only.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX Controller retains ViewModel for FXML wiring")
public final class WarehouseWorkbenchController implements ViewModelAware<WarehouseWorkbenchViewModel> {

    @FXML
    private Label titleLabel;

    @FXML
    private ListView<WarehouseSection> sectionList;

    @FXML
    private VBox warehousesPane;

    @FXML
    private VBox stockPane;

    @FXML
    private VBox receiptPane;

    @FXML
    private VBox movePane;

    @FXML
    private VBox transferPane;

    @FXML
    private VBox consumptionPane;

    @FXML
    private VBox adjustmentPane;

    @FXML
    private VBox inventoryPane;

    @FXML
    private VBox reservationsPane;

    @FXML
    private TableView<WarehouseView> warehousesTable;

    @FXML
    private TableColumn<WarehouseView, String> warehouseCodeColumn;

    @FXML
    private TableColumn<WarehouseView, String> warehouseNameColumn;

    @FXML
    private TableColumn<WarehouseView, String> warehouseActiveColumn;

    @FXML
    private TextField newWarehouseCodeField;

    @FXML
    private TextField newWarehouseNameField;

    @FXML
    private CheckBox newWarehouseActiveCheck;

    @FXML
    private Button createWarehouseButton;

    @FXML
    private ComboBox<WarehouseChoice> newCellWarehouseCombo;

    @FXML
    private TextField newCellCodeField;

    @FXML
    private CheckBox newCellActiveCheck;

    @FXML
    private Button createCellButton;

    @FXML
    private TableView<StorageCellView> cellsTable;

    @FXML
    private TableColumn<StorageCellView, String> cellCodeColumn;

    @FXML
    private TableColumn<StorageCellView, String> cellActiveColumn;

    @FXML
    private ComboBox<WarehouseChoice> stockWarehouseCombo;

    @FXML
    private TextField stockMaterialField;

    @FXML
    private Button loadStockButton;

    @FXML
    private TableView<StockView> stockTable;

    @FXML
    private TableColumn<StockView, String> stockArticleColumn;

    @FXML
    private TableColumn<StockView, String> stockMaterialNameColumn;

    @FXML
    private TableColumn<StockView, String> stockColorColumn;

    @FXML
    private TableColumn<StockView, String> stockSizeColumn;

    @FXML
    private TableColumn<StockView, String> stockUnitColumn;

    @FXML
    private TableColumn<StockView, String> stockWarehouseColumn;

    @FXML
    private TableColumn<StockView, String> stockCellColumn;

    @FXML
    private TableColumn<StockView, String> stockQuantityColumn;

    @FXML
    private TableColumn<StockView, String> stockStateColumn;

    @FXML
    private TextField receiptArticleField;

    @FXML
    private TextField receiptNameField;

    @FXML
    private TextField receiptColorField;

    @FXML
    private TextField receiptSizeField;

    @FXML
    private ComboBox<String> receiptUnitCombo;

    @FXML
    private TextField receiptQuantityField;

    @FXML
    private ComboBox<WarehouseChoice> receiptWarehouseCombo;

    @FXML
    private ComboBox<StorageCellChoice> receiptCellCombo;

    @FXML
    private Button submitReceiptButton;

    @FXML
    private ComboBox<MaterialChoice> moveMaterialCombo;

    @FXML
    private TextField moveQuantityField;

    @FXML
    private ComboBox<WarehouseChoice> moveSourceWarehouseCombo;

    @FXML
    private ComboBox<StorageCellChoice> moveSourceCellCombo;

    @FXML
    private ComboBox<StorageCellChoice> moveDestCellCombo;

    @FXML
    private Button submitMoveButton;

    @FXML
    private ComboBox<WarehouseChoice> transferSourceWarehouseCombo;

    @FXML
    private ComboBox<StorageCellChoice> transferSourceCellCombo;

    @FXML
    private ComboBox<WarehouseChoice> transferDestWarehouseCombo;

    @FXML
    private ComboBox<MaterialChoice> transferMaterialCombo;

    @FXML
    private TextField transferQuantityField;

    @FXML
    private Button submitTransferSendButton;

    @FXML
    private ComboBox<MaterialChoice> transferReceiveMaterialCombo;

    @FXML
    private TextField transferReceiveQuantityField;

    @FXML
    private ComboBox<WarehouseChoice> transferReceiveSourceWarehouseCombo;

    @FXML
    private ComboBox<StorageCellChoice> transferReceiveSourceCellCombo;

    @FXML
    private ComboBox<WarehouseChoice> transferReceiveDestWarehouseCombo;

    @FXML
    private ComboBox<StorageCellChoice> transferReceiveDestCellCombo;

    @FXML
    private Button submitTransferReceiveButton;

    @FXML
    private ComboBox<MaterialChoice> consumptionMaterialCombo;

    @FXML
    private TextField consumptionQuantityField;

    @FXML
    private ComboBox<WarehouseChoice> consumptionWarehouseCombo;

    @FXML
    private ComboBox<StorageCellChoice> consumptionCellCombo;

    @FXML
    private TextField consumptionBasisField;

    @FXML
    private Button submitConsumptionButton;

    @FXML
    private ComboBox<MaterialChoice> adjustmentMaterialCombo;

    @FXML
    private TextField adjustmentQuantityDeltaField;

    @FXML
    private ComboBox<WarehouseChoice> adjustmentWarehouseCombo;

    @FXML
    private ComboBox<StorageCellChoice> adjustmentCellCombo;

    @FXML
    private TextField adjustmentReasonField;

    @FXML
    private Button submitAdjustmentButton;

    @FXML
    private Button openAdjustmentFromInventoryButton;

    @FXML
    private TextField reservationFilterMaterialField;

    @FXML
    private Button loadReservationsButton;

    @FXML
    private TextField reservationMaterialField;

    @FXML
    private TextField reservationOrderRefField;

    @FXML
    private TextField reservationQuantityField;

    @FXML
    private Button submitReservationButton;

    @FXML
    private TableView<ReservationLinkView> reservationsTable;

    @FXML
    private TableColumn<ReservationLinkView, String> reservationMaterialColumn;

    @FXML
    private TableColumn<ReservationLinkView, String> reservationOrderColumn;

    @FXML
    private TableColumn<ReservationLinkView, String> reservationQuantityColumn;

    @FXML
    private Label loadingLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label errorLabel;

    @Override
    public void setViewModel(WarehouseWorkbenchViewModel viewModel) {
        titleLabel.textProperty().bind(viewModel.titleProperty());
        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    String message = viewModel.errorMessageProperty().get();
                    return message != null && !message.isBlank();
                },
                viewModel.errorMessageProperty()));
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
        loadingLabel.visibleProperty().bind(viewModel.loadingProperty());
        loadingLabel.managedProperty().bind(loadingLabel.visibleProperty());

        sectionList.getItems().setAll(WarehouseSection.values());
        sectionList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(WarehouseSection item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.title());
            }
        });
        sectionList.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> {
                    if (newValue != null) {
                        viewModel.selectSection(newValue);
                    }
                });
        viewModel.sectionProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && sectionList.getSelectionModel().getSelectedItem() != newValue) {
                sectionList.getSelectionModel().select(newValue);
            }
            showPane(newValue);
        });

        warehouseCodeColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().code()));
        warehouseNameColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().name()));
        warehouseActiveColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().active() ? "Активен" : "Неактивен"));
        warehousesTable.setItems(viewModel.warehouses());

        bindText(newWarehouseCodeField, viewModel.newWarehouseCodeProperty());
        bindText(newWarehouseNameField, viewModel.newWarehouseNameProperty());
        newWarehouseActiveCheck.selectedProperty().bindBidirectional(
                viewModel.newWarehouseActiveProperty());
        createWarehouseButton.disableProperty().bind(viewModel.canCreateWarehouseProperty().not());
        createWarehouseButton.visibleProperty().bind(viewModel.canCreateWarehouseProperty());
        createWarehouseButton.managedProperty().bind(viewModel.canCreateWarehouseProperty());
        createWarehouseButton.setOnAction(e -> viewModel.createWarehouse());

        newCellWarehouseCombo.setItems(viewModel.warehouseChoices());
        newCellWarehouseCombo.valueProperty().bindBidirectional(
                viewModel.newCellWarehouseProperty());
        bindText(newCellCodeField, viewModel.newCellCodeProperty());
        newCellActiveCheck.selectedProperty().bindBidirectional(viewModel.newCellActiveProperty());
        createCellButton.disableProperty().bind(viewModel.canCreateStorageCellProperty().not());
        createCellButton.visibleProperty().bind(viewModel.canCreateStorageCellProperty());
        createCellButton.managedProperty().bind(viewModel.canCreateStorageCellProperty());
        createCellButton.setOnAction(e -> viewModel.createStorageCell());
        cellCodeColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().code()));
        cellActiveColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().active() ? "Активна" : "Неактивна"));
        cellsTable.setItems(viewModel.warehouseCells());

        stockWarehouseCombo.setItems(viewModel.warehouseChoices());
        stockWarehouseCombo.valueProperty().bindBidirectional(viewModel.stockWarehouseProperty());
        stockMaterialField.textProperty().bindBidirectional(viewModel.stockMaterialCodeProperty());
        loadStockButton.setOnAction(e -> viewModel.loadStock());
        stockArticleColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().article()));
        stockMaterialNameColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().materialName()));
        stockColorColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().color()));
        stockSizeColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().size()));
        stockUnitColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().unitOfMeasure()));
        stockWarehouseColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().warehouse()));
        stockCellColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().storageCell()));
        stockQuantityColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().quantity().toPlainString()));
        stockStateColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().stockState().name()));
        stockTable.setItems(viewModel.stockRows());

        bindText(receiptArticleField, viewModel.receiptArticleProperty());
        bindText(receiptNameField, viewModel.receiptNameProperty());
        bindText(receiptColorField, viewModel.receiptColorProperty());
        bindText(receiptSizeField, viewModel.receiptSizeProperty());
        receiptUnitCombo.setItems(viewModel.unitOfMeasureChoices());
        receiptUnitCombo.valueProperty().bindBidirectional(viewModel.receiptUnitProperty());
        bindText(receiptQuantityField, viewModel.receiptQuantityProperty());
        bindWarehouseCombo(receiptWarehouseCombo, viewModel);
        receiptWarehouseCombo.valueProperty().bindBidirectional(
                viewModel.receiptWarehouseProperty());
        receiptCellCombo.setItems(viewModel.receiptCellChoices());
        receiptCellCombo.valueProperty().bindBidirectional(viewModel.receiptCellProperty());
        submitReceiptButton.disableProperty().bind(viewModel.canReceiptProperty().not());
        submitReceiptButton.setOnAction(e -> viewModel.submitReceipt());

        bindMaterialCombo(moveMaterialCombo, viewModel);
        moveMaterialCombo.valueProperty().bindBidirectional(viewModel.moveMaterialProperty());
        bindText(moveQuantityField, viewModel.moveQuantityProperty());
        bindWarehouseCombo(moveSourceWarehouseCombo, viewModel);
        moveSourceWarehouseCombo.valueProperty().bindBidirectional(
                viewModel.moveSourceWarehouseProperty());
        moveSourceCellCombo.setItems(viewModel.moveSourceCellChoices());
        moveSourceCellCombo.valueProperty().bindBidirectional(viewModel.moveSourceCellProperty());
        moveDestCellCombo.setItems(viewModel.moveDestCellChoices());
        moveDestCellCombo.valueProperty().bindBidirectional(viewModel.moveDestCellProperty());
        submitMoveButton.disableProperty().bind(viewModel.canMoveProperty().not());
        submitMoveButton.setOnAction(e -> viewModel.submitMove());

        bindWarehouseCombo(transferSourceWarehouseCombo, viewModel);
        transferSourceWarehouseCombo.valueProperty().bindBidirectional(
                viewModel.transferSourceWarehouseProperty());
        transferSourceCellCombo.setItems(viewModel.transferSendCellChoices());
        transferSourceCellCombo.valueProperty().bindBidirectional(
                viewModel.transferSourceCellProperty());
        bindWarehouseCombo(transferDestWarehouseCombo, viewModel);
        transferDestWarehouseCombo.valueProperty().bindBidirectional(
                viewModel.transferDestWarehouseProperty());
        bindMaterialCombo(transferMaterialCombo, viewModel);
        transferMaterialCombo.valueProperty().bindBidirectional(viewModel.transferMaterialProperty());
        bindText(transferQuantityField, viewModel.transferQuantityProperty());
        submitTransferSendButton.disableProperty().bind(viewModel.canTransferProperty().not());
        submitTransferSendButton.setOnAction(e -> viewModel.submitTransferSend());

        bindMaterialCombo(transferReceiveMaterialCombo, viewModel);
        transferReceiveMaterialCombo.valueProperty().bindBidirectional(
                viewModel.transferReceiveMaterialProperty());
        bindText(transferReceiveQuantityField, viewModel.transferReceiveQuantityProperty());
        bindWarehouseCombo(transferReceiveSourceWarehouseCombo, viewModel);
        transferReceiveSourceWarehouseCombo.valueProperty().bindBidirectional(
                viewModel.transferReceiveSourceWarehouseProperty());
        transferReceiveSourceCellCombo.setItems(viewModel.transferReceiveSourceCellChoices());
        transferReceiveSourceCellCombo.valueProperty().bindBidirectional(
                viewModel.transferReceiveSourceCellProperty());
        bindWarehouseCombo(transferReceiveDestWarehouseCombo, viewModel);
        transferReceiveDestWarehouseCombo.valueProperty().bindBidirectional(
                viewModel.transferReceiveDestWarehouseProperty());
        transferReceiveDestCellCombo.setItems(viewModel.transferReceiveDestCellChoices());
        transferReceiveDestCellCombo.valueProperty().bindBidirectional(
                viewModel.transferReceiveDestCellProperty());
        submitTransferReceiveButton.disableProperty().bind(viewModel.canTransferProperty().not());
        submitTransferReceiveButton.setOnAction(e -> viewModel.submitTransferReceive());

        bindMaterialCombo(consumptionMaterialCombo, viewModel);
        consumptionMaterialCombo.valueProperty().bindBidirectional(viewModel.consumptionMaterialProperty());
        bindText(consumptionQuantityField, viewModel.consumptionQuantityProperty());
        bindText(consumptionBasisField, viewModel.consumptionBasisProperty());
        bindWarehouseCombo(consumptionWarehouseCombo, viewModel);
        consumptionWarehouseCombo.valueProperty().bindBidirectional(
                viewModel.consumptionWarehouseProperty());
        consumptionCellCombo.setItems(viewModel.consumptionCellChoices());
        consumptionCellCombo.valueProperty().bindBidirectional(
                viewModel.consumptionCellProperty());
        submitConsumptionButton.disableProperty().bind(viewModel.canConsumptionProperty().not());
        submitConsumptionButton.setOnAction(e -> viewModel.submitConsumption());

        bindMaterialCombo(adjustmentMaterialCombo, viewModel);
        adjustmentMaterialCombo.valueProperty().bindBidirectional(viewModel.adjustmentMaterialProperty());
        bindText(adjustmentQuantityDeltaField, viewModel.adjustmentQuantityDeltaProperty());
        bindText(adjustmentReasonField, viewModel.adjustmentReasonProperty());
        bindWarehouseCombo(adjustmentWarehouseCombo, viewModel);
        adjustmentWarehouseCombo.valueProperty().bindBidirectional(
                viewModel.adjustmentWarehouseProperty());
        adjustmentCellCombo.setItems(viewModel.adjustmentCellChoices());
        adjustmentCellCombo.valueProperty().bindBidirectional(viewModel.adjustmentCellProperty());
        submitAdjustmentButton.disableProperty().bind(viewModel.canAdjustmentProperty().not());
        submitAdjustmentButton.setOnAction(e -> viewModel.submitAdjustment());

        openAdjustmentFromInventoryButton.disableProperty().bind(
                viewModel.canAdjustmentProperty().not());
        openAdjustmentFromInventoryButton.setOnAction(e -> viewModel.openAdjustmentFromInventory());

        bindText(reservationFilterMaterialField, viewModel.reservationFilterMaterialProperty());
        bindText(reservationMaterialField, viewModel.reservationMaterialProperty());
        bindText(reservationOrderRefField, viewModel.reservationOrderRefProperty());
        bindText(reservationQuantityField, viewModel.reservationQuantityProperty());
        loadReservationsButton.disableProperty().bind(viewModel.canReservationProperty().not());
        submitReservationButton.disableProperty().bind(viewModel.canReservationProperty().not());
        loadReservationsButton.setOnAction(e -> viewModel.loadReservationLinks());
        submitReservationButton.setOnAction(e -> viewModel.submitReservationLink());
        reservationMaterialColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().materialCode()));
        reservationOrderColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().targetReference()));
        reservationQuantityColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().quantity().toPlainString()));
        reservationsTable.setItems(viewModel.reservationLinks());

        sectionList.getSelectionModel().select(WarehouseSection.WAREHOUSES);
        showPane(WarehouseSection.WAREHOUSES);
        viewModel.refreshCurrent();
    }

    private static void bindText(TextField field, javafx.beans.property.StringProperty property) {
        field.textProperty().bindBidirectional(property);
    }

    private static void bindMaterialDisplay(
            TextField materialField, Label displayLabel, Runnable refreshAction) {
        materialField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (Boolean.FALSE.equals(isFocused)) {
                refreshAction.run();
            }
        });
        displayLabel.setWrapText(true);
    }

    private static void bindMaterialCombo(
            ComboBox<MaterialChoice> combo, WarehouseWorkbenchViewModel viewModel) {
        combo.setItems(viewModel.materialChoices());
    }

    private static void bindWarehouseCombo(
            ComboBox<WarehouseChoice> combo, WarehouseWorkbenchViewModel viewModel) {
        combo.setItems(viewModel.warehouseChoices());
    }

    private void showPane(WarehouseSection section) {
        warehousesPane.setVisible(section == WarehouseSection.WAREHOUSES);
        warehousesPane.setManaged(section == WarehouseSection.WAREHOUSES);
        stockPane.setVisible(section == WarehouseSection.STOCK);
        stockPane.setManaged(section == WarehouseSection.STOCK);
        receiptPane.setVisible(section == WarehouseSection.RECEIPT);
        receiptPane.setManaged(section == WarehouseSection.RECEIPT);
        movePane.setVisible(section == WarehouseSection.MOVE);
        movePane.setManaged(section == WarehouseSection.MOVE);
        transferPane.setVisible(section == WarehouseSection.TRANSFER);
        transferPane.setManaged(section == WarehouseSection.TRANSFER);
        consumptionPane.setVisible(section == WarehouseSection.CONSUMPTION);
        consumptionPane.setManaged(section == WarehouseSection.CONSUMPTION);
        adjustmentPane.setVisible(section == WarehouseSection.ADJUSTMENT);
        adjustmentPane.setManaged(section == WarehouseSection.ADJUSTMENT);
        inventoryPane.setVisible(section == WarehouseSection.INVENTORY);
        inventoryPane.setManaged(section == WarehouseSection.INVENTORY);
        reservationsPane.setVisible(section == WarehouseSection.RESERVATIONS);
        reservationsPane.setManaged(section == WarehouseSection.RESERVATIONS);
    }
}
