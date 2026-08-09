package com.tmp.ui.shell.screen.warehouse;

import com.tmp.ui.shell.navigation.ViewModelAware;
import com.tmp.warehouse.api.WarehouseApi.ReservationLinkView;
import com.tmp.warehouse.api.WarehouseApi.StockView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Warehouse workbench FXML controller. Binds eight Capability-gated sections to
 * {@link WarehouseWorkbenchViewModel}.
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
    private Button warehousesButton;

    @FXML
    private Button stockButton;

    @FXML
    private Button receiptButton;

    @FXML
    private Button moveButton;

    @FXML
    private Button transferButton;

    @FXML
    private Button consumptionButton;

    @FXML
    private Button adjustmentButton;

    @FXML
    private Button reservationsButton;

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
    private TextField stockWarehouseIdField;

    @FXML
    private TextField stockMaterialField;

    @FXML
    private Button loadStockButton;

    @FXML
    private TableView<StockView> stockTable;

    @FXML
    private TableColumn<StockView, String> stockMaterialColumn;

    @FXML
    private TableColumn<StockView, String> stockWarehouseColumn;

    @FXML
    private TableColumn<StockView, String> stockCellColumn;

    @FXML
    private TableColumn<StockView, String> stockQuantityColumn;

    @FXML
    private TableColumn<StockView, String> stockStateColumn;

    @FXML
    private TextField receiptMaterialField;

    @FXML
    private TextField receiptQuantityField;

    @FXML
    private TextField receiptWarehouseIdField;

    @FXML
    private TextField receiptCellIdField;

    @FXML
    private Button submitReceiptButton;

    @FXML
    private TextField moveMaterialField;

    @FXML
    private TextField moveQuantityField;

    @FXML
    private TextField moveSourceWarehouseIdField;

    @FXML
    private TextField moveSourceCellIdField;

    @FXML
    private TextField moveDestWarehouseIdField;

    @FXML
    private TextField moveDestCellIdField;

    @FXML
    private Button submitMoveButton;

    @FXML
    private TextField transferMaterialField;

    @FXML
    private TextField transferQuantityField;

    @FXML
    private TextField transferSourceWarehouseIdField;

    @FXML
    private TextField transferSourceCellIdField;

    @FXML
    private TextField transferDestWarehouseIdField;

    @FXML
    private Button submitTransferButton;

    @FXML
    private TextField consumptionMaterialField;

    @FXML
    private TextField consumptionQuantityField;

    @FXML
    private TextField consumptionWarehouseIdField;

    @FXML
    private TextField consumptionCellIdField;

    @FXML
    private TextField consumptionBasisField;

    @FXML
    private Button submitConsumptionButton;

    @FXML
    private TextField adjustmentMaterialField;

    @FXML
    private TextField adjustmentQuantityDeltaField;

    @FXML
    private TextField adjustmentWarehouseIdField;

    @FXML
    private TextField adjustmentCellIdField;

    @FXML
    private TextField adjustmentReasonField;

    @FXML
    private Button submitAdjustmentButton;

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

        warehousesButton.disableProperty().bind(viewModel.canViewProperty().not());
        stockButton.disableProperty().bind(viewModel.canViewProperty().not());
        receiptButton.disableProperty().bind(viewModel.canReceiptProperty().not());
        moveButton.disableProperty().bind(viewModel.canMoveProperty().not());
        transferButton.disableProperty().bind(viewModel.canTransferProperty().not());
        consumptionButton.disableProperty().bind(viewModel.canConsumptionProperty().not());
        adjustmentButton.disableProperty().bind(viewModel.canAdjustmentProperty().not());
        reservationsButton.disableProperty().bind(viewModel.canReservationProperty().not());

        warehousesButton.setOnAction(e -> viewModel.selectSection(WarehouseSection.WAREHOUSES));
        stockButton.setOnAction(e -> viewModel.selectSection(WarehouseSection.STOCK));
        receiptButton.setOnAction(e -> viewModel.selectSection(WarehouseSection.RECEIPT));
        moveButton.setOnAction(e -> viewModel.selectSection(WarehouseSection.MOVE));
        transferButton.setOnAction(e -> viewModel.selectSection(WarehouseSection.TRANSFER));
        consumptionButton.setOnAction(e -> viewModel.selectSection(WarehouseSection.CONSUMPTION));
        adjustmentButton.setOnAction(e -> viewModel.selectSection(WarehouseSection.ADJUSTMENT));
        reservationsButton.setOnAction(e -> viewModel.selectSection(WarehouseSection.RESERVATIONS));

        warehouseCodeColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().code()));
        warehouseNameColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().name()));
        warehouseActiveColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().active() ? "Активен" : "Неактивен"));
        warehousesTable.setItems(viewModel.warehouses());

        stockWarehouseIdField.textProperty().bindBidirectional(viewModel.stockWarehouseIdProperty());
        stockMaterialField.textProperty().bindBidirectional(viewModel.stockMaterialCodeProperty());
        loadStockButton.setOnAction(e -> viewModel.loadStock());
        stockMaterialColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().materialCode()));
        stockWarehouseColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().warehouseId().toString()));
        stockCellColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().storageCellId().toString()));
        stockQuantityColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().quantity().toPlainString()));
        stockStateColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().stockState().name()));
        stockTable.setItems(viewModel.stockRows());

        bindText(receiptMaterialField, viewModel.receiptMaterialProperty());
        bindText(receiptQuantityField, viewModel.receiptQuantityProperty());
        bindText(receiptWarehouseIdField, viewModel.receiptWarehouseIdProperty());
        bindText(receiptCellIdField, viewModel.receiptCellIdProperty());
        submitReceiptButton.disableProperty().bind(viewModel.canReceiptProperty().not());
        submitReceiptButton.setOnAction(e -> viewModel.submitReceipt());

        bindText(moveMaterialField, viewModel.moveMaterialProperty());
        bindText(moveQuantityField, viewModel.moveQuantityProperty());
        bindText(moveSourceWarehouseIdField, viewModel.moveSourceWarehouseIdProperty());
        bindText(moveSourceCellIdField, viewModel.moveSourceCellIdProperty());
        bindText(moveDestWarehouseIdField, viewModel.moveDestWarehouseIdProperty());
        bindText(moveDestCellIdField, viewModel.moveDestCellIdProperty());
        submitMoveButton.disableProperty().bind(viewModel.canMoveProperty().not());
        submitMoveButton.setOnAction(e -> viewModel.submitMove());

        bindText(transferMaterialField, viewModel.transferMaterialProperty());
        bindText(transferQuantityField, viewModel.transferQuantityProperty());
        bindText(transferSourceWarehouseIdField, viewModel.transferSourceWarehouseIdProperty());
        bindText(transferSourceCellIdField, viewModel.transferSourceCellIdProperty());
        bindText(transferDestWarehouseIdField, viewModel.transferDestWarehouseIdProperty());
        submitTransferButton.disableProperty().bind(viewModel.canTransferProperty().not());
        submitTransferButton.setOnAction(e -> viewModel.submitTransferSend());

        bindText(consumptionMaterialField, viewModel.consumptionMaterialProperty());
        bindText(consumptionQuantityField, viewModel.consumptionQuantityProperty());
        bindText(consumptionWarehouseIdField, viewModel.consumptionWarehouseIdProperty());
        bindText(consumptionCellIdField, viewModel.consumptionCellIdProperty());
        bindText(consumptionBasisField, viewModel.consumptionBasisProperty());
        submitConsumptionButton.disableProperty().bind(viewModel.canConsumptionProperty().not());
        submitConsumptionButton.setOnAction(e -> viewModel.submitConsumption());

        bindText(adjustmentMaterialField, viewModel.adjustmentMaterialProperty());
        bindText(adjustmentQuantityDeltaField, viewModel.adjustmentQuantityDeltaProperty());
        bindText(adjustmentWarehouseIdField, viewModel.adjustmentWarehouseIdProperty());
        bindText(adjustmentCellIdField, viewModel.adjustmentCellIdProperty());
        bindText(adjustmentReasonField, viewModel.adjustmentReasonProperty());
        submitAdjustmentButton.disableProperty().bind(viewModel.canAdjustmentProperty().not());
        submitAdjustmentButton.setOnAction(e -> viewModel.submitAdjustment());

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
        reservationsPane.setVisible(section == WarehouseSection.RESERVATIONS);
        reservationsPane.setManaged(section == WarehouseSection.RESERVATIONS);
    }
}
