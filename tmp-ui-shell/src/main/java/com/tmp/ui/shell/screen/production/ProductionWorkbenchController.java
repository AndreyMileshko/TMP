package com.tmp.ui.shell.screen.production;

import com.tmp.ui.shell.navigation.ViewModelAware;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import java.util.UUID;

/**
 * Production workbench FXML controller. Confirmation dialogs live here; no business logic.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX Controller retains ViewModel for FXML wiring")
public final class ProductionWorkbenchController
        implements ViewModelAware<ProductionWorkbenchViewModel> {

    @FXML
    private ScrollPane rootScroll;

    @FXML
    private Label titleLabel;

    @FXML
    private Label emptyStateLabel;

    @FXML
    private Label orderNumberLabel;

    @FXML
    private Label customerLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label statusDetailLabel;

    @FXML
    private Label statusMessageLabel;

    @FXML
    private Label errorMessageLabel;

    @FXML
    private Label loadingLabel;

    @FXML
    private TextField orderSelectorField;

    @FXML
    private Button openOrderButton;

    @FXML
    private Button refreshButton;

    @FXML
    private Button acceptButton;

    @FXML
    private Button checkMaterialsButton;

    @FXML
    private Button prepareTransferButton;

    @FXML
    private Button confirmReceiptButton;

    @FXML
    private Button prepareReleaseButton;

    @FXML
    private Button cancelProductionButton;

    @FXML
    private TableView<ProductionItemRow> itemsTable;

    @FXML
    private TableColumn<ProductionItemRow, String> itemPositionColumn;

    @FXML
    private TableColumn<ProductionItemRow, String> itemStatusColumn;

    @FXML
    private TableColumn<ProductionItemRow, String> itemOrderedColumn;

    @FXML
    private TableColumn<ProductionItemRow, String> itemActiveColumn;

    @FXML
    private TableColumn<ProductionItemRow, String> itemReleasedColumn;

    @FXML
    private TableColumn<ProductionItemRow, String> itemSpecColumn;

    @FXML
    private TableColumn<ProductionItemRow, String> itemCuttingColumn;

    @FXML
    private TableColumn<ProductionItemRow, String> itemReleaseQtyColumn;

    @FXML
    private TableView<MaterialAvailabilityRow> materialsTable;

    @FXML
    private TableColumn<MaterialAvailabilityRow, String> materialNameColumn;

    @FXML
    private TableColumn<MaterialAvailabilityRow, String> materialRequiredColumn;

    @FXML
    private TableColumn<MaterialAvailabilityRow, String> materialMainColumn;

    @FXML
    private TableColumn<MaterialAvailabilityRow, String> materialProductionColumn;

    @FXML
    private TableColumn<MaterialAvailabilityRow, String> materialTotalColumn;

    @FXML
    private TableColumn<MaterialAvailabilityRow, String> materialDeficitColumn;

    @FXML
    private TableColumn<MaterialAvailabilityRow, String> materialSourceColumn;

    @FXML
    private TableView<ProductionHistoryRow> historyTable;

    @FXML
    private TableColumn<ProductionHistoryRow, String> historyTimeColumn;

    @FXML
    private TableColumn<ProductionHistoryRow, String> historyTypeColumn;

    @FXML
    private TableColumn<ProductionHistoryRow, String> historyActorColumn;

    @FXML
    private TableColumn<ProductionHistoryRow, String> historySummaryColumn;

    @FXML
    private ComboBox<LogicalTransferRow> logicalTransferCombo;

    @FXML
    private VBox transferPanel;

    @FXML
    private TableView<TransferLineRow> transferLinesTable;

    @FXML
    private TableColumn<TransferLineRow, String> transferMaterialColumn;

    @FXML
    private TableColumn<TransferLineRow, String> transferRecommendedColumn;

    @FXML
    private TableColumn<TransferLineRow, String> transferRequestedColumn;

    @FXML
    private TableColumn<TransferLineRow, String> transferIncludedColumn;

    @FXML
    private TableColumn<TransferLineRow, String> transferAllocationSummaryColumn;

    @FXML
    private TableView<TransferAllocationRow> transferAllocationsTable;

    @FXML
    private TableColumn<TransferAllocationRow, StorageCellChoice> transferAllocSourceColumn;

    @FXML
    private TableColumn<TransferAllocationRow, StorageCellChoice> transferAllocDestColumn;

    @FXML
    private TableColumn<TransferAllocationRow, String> transferAllocQtyColumn;

    @FXML
    private Button addTransferAllocationButton;

    @FXML
    private Button removeTransferAllocationButton;

    @FXML
    private Button applyRequestedQtyButton;

    @FXML
    private Button excludeTransferLineButton;

    @FXML
    private Button restoreTransferLineButton;

    @FXML
    private Button confirmTransferButton;

    @FXML
    private VBox releasePanel;

    @FXML
    private TableView<ReleaseMaterialRow> releaseMaterialsTable;

    @FXML
    private TableColumn<ReleaseMaterialRow, String> releaseMaterialColumn;

    @FXML
    private TableColumn<ReleaseMaterialRow, String> releasePlannedColumn;

    @FXML
    private TableColumn<ReleaseMaterialRow, String> releaseActualColumn;

    @FXML
    private TableColumn<ReleaseMaterialRow, String> releaseAllocationSummaryColumn;

    @FXML
    private TableView<ReleaseCellAllocationRow> releaseAllocationsTable;

    @FXML
    private TableColumn<ReleaseCellAllocationRow, StorageCellChoice> releaseAllocCellColumn;

    @FXML
    private TableColumn<ReleaseCellAllocationRow, String> releaseAllocQtyColumn;

    @FXML
    private Button addReleaseAllocationButton;

    @FXML
    private Button removeReleaseAllocationButton;

    @FXML
    private Button confirmReleaseButton;

    private ProductionWorkbenchViewModel viewModel;

    private final ObservableList<TransferAllocationRow> emptyTransferAllocations =
            FXCollections.observableArrayList();

    private final ObservableList<ReleaseCellAllocationRow> emptyReleaseAllocations =
            FXCollections.observableArrayList();

    @Override
    public void setViewModel(ProductionWorkbenchViewModel viewModel) {
        this.viewModel = viewModel;
        bind();
    }

    private void bind() {
        titleLabel.setText("Производство");
        emptyStateLabel.textProperty().bind(viewModel.emptyStateMessageProperty());
        orderNumberLabel.textProperty().bind(viewModel.orderNumberProperty());
        customerLabel.textProperty().bind(viewModel.customerLabelProperty());
        statusLabel.textProperty().bind(viewModel.statusLabelProperty());
        statusDetailLabel.textProperty().bind(viewModel.statusDetailLabelProperty());
        statusMessageLabel.textProperty().bind(viewModel.statusMessageProperty());
        errorMessageLabel.textProperty().bind(viewModel.errorMessageProperty());
        loadingLabel.visibleProperty().bind(viewModel.loadingProperty());
        loadingLabel.managedProperty().bind(viewModel.loadingProperty());

        orderSelectorField.textProperty().bindBidirectional(viewModel.orderSelectorInputProperty());
        openOrderButton.setOnAction(e -> viewModel.openSelectedOrder());
        refreshButton.setOnAction(e -> viewModel.refresh());

        acceptButton.disableProperty().bind(viewModel.canAcceptProperty().not());
        checkMaterialsButton.disableProperty().bind(viewModel.canCheckProperty().not());
        prepareTransferButton.disableProperty().bind(viewModel.canTransferProperty().not());
        confirmReceiptButton.disableProperty().bind(viewModel.canReceiptProperty().not());
        prepareReleaseButton.disableProperty().bind(viewModel.canReleaseProperty().not());
        cancelProductionButton.disableProperty().bind(viewModel.canCancelProperty().not());

        acceptButton.setOnAction(e -> viewModel.acceptOrder());
        checkMaterialsButton.setOnAction(e -> viewModel.checkMaterials());
        prepareTransferButton.setOnAction(e -> viewModel.prepareTransfer());
        confirmReceiptButton.setOnAction(e -> viewModel.confirmReceipt());
        prepareReleaseButton.setOnAction(e -> viewModel.prepareRelease());
        cancelProductionButton.setOnAction(e -> confirmCancel());

        bindItemsTable();
        bindMaterialsTable();
        bindHistoryTable();
        bindTransferPanel();
        bindReleasePanel();

        logicalTransferCombo.setItems(viewModel.logicalTransfers());
        logicalTransferCombo
                .valueProperty()
                .bindBidirectional(viewModel.selectedLogicalTransferProperty());
    }

    private void bindItemsTable() {
        itemsTable.setEditable(true);
        itemPositionColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().positionLabel()));
        itemStatusColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().statusLabel()));
        itemOrderedColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().orderedQuantity()));
        itemActiveColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().activeQuantity()));
        itemReleasedColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().releasedQuantity()));
        itemSpecColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().specificationId()));
        itemCuttingColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().cuttingPlanRefs()));
        itemReleaseQtyColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        itemReleaseQtyColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().releaseQuantityInput()));
        itemReleaseQtyColumn.setOnEditCommit(
                event -> event.getRowValue().setReleaseQuantityInput(event.getNewValue()));
        itemsTable.setItems(viewModel.itemRows());
    }

    private void bindMaterialsTable() {
        materialNameColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().material()));
        materialRequiredColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().required()));
        materialMainColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().mainWarehouse()));
        materialProductionColumn.setCellValueFactory(
                c ->
                        new javafx.beans.property.SimpleStringProperty(
                                c.getValue().productionWarehouse()));
        materialTotalColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().totalAvailable()));
        materialDeficitColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().deficit()));
        materialSourceColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().planningSource()));
        materialsTable.setItems(viewModel.materialRows());
    }

    private void bindHistoryTable() {
        historyTimeColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().occurredAt()));
        historyTypeColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().typeLabel()));
        historyActorColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().actor()));
        historySummaryColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().summary()));
        historyTable.setItems(viewModel.historyRows());
    }

    private void bindTransferPanel() {
        transferPanel.visibleProperty().bind(viewModel.transferPanelVisibleProperty());
        transferPanel.managedProperty().bind(viewModel.transferPanelVisibleProperty());
        transferLinesTable.setEditable(true);
        transferMaterialColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().materialLabel()));
        transferRecommendedColumn.setCellValueFactory(
                c ->
                        new javafx.beans.property.SimpleStringProperty(
                                c.getValue().recommendedQuantity()));
        transferRequestedColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        transferRequestedColumn.setCellValueFactory(
                c ->
                        new javafx.beans.property.SimpleStringProperty(
                                c.getValue().requestedQuantity()));
        transferRequestedColumn.setOnEditCommit(
                event -> event.getRowValue().setRequestedQuantity(event.getNewValue()));
        transferIncludedColumn.setCellValueFactory(
                c ->
                        new javafx.beans.property.SimpleStringProperty(
                                c.getValue().included() ? "Да" : "Нет"));
        transferAllocationSummaryColumn.setCellValueFactory(
                c ->
                        new javafx.beans.property.SimpleStringProperty(
                                c.getValue().allocationSummary()));
        transferLinesTable.setItems(viewModel.transferLines());

        transferAllocationsTable.setItems(emptyTransferAllocations);
        transferAllocationsTable.setEditable(true);
        transferAllocQtyColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        transferAllocQtyColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().quantity()));
        transferAllocQtyColumn.setOnEditCommit(
                event -> event.getRowValue().setQuantity(event.getNewValue()));
        transferAllocSourceColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().sourceCell()));
        transferAllocSourceColumn.setCellFactory(
                col ->
                        new TableCell<>() {
                            private final ComboBox<StorageCellChoice> combo = new ComboBox<>();

                            {
                                combo.setMaxWidth(Double.MAX_VALUE);
                                combo.valueProperty()
                                        .addListener(
                                                (obs, oldValue, newValue) -> {
                                                    TransferAllocationRow row =
                                                            getTableRow() == null
                                                                    ? null
                                                                    : getTableRow().getItem();
                                                    if (row != null) {
                                                        row.setSourceCell(newValue);
                                                    }
                                                });
                            }

                            @Override
                            protected void updateItem(StorageCellChoice item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty
                                        || getTableRow() == null
                                        || getTableRow().getItem() == null) {
                                    setGraphic(null);
                                    return;
                                }
                                TransferAllocationRow row = getTableRow().getItem();
                                combo.setItems(row.sourceCellChoices());
                                combo.setValue(row.sourceCell());
                                setGraphic(combo);
                            }
                        });
        transferAllocDestColumn.setCellValueFactory(
                c ->
                        new javafx.beans.property.SimpleObjectProperty<>(
                                c.getValue().destinationCell()));
        transferAllocDestColumn.setCellFactory(
                col ->
                        new TableCell<>() {
                            private final ComboBox<StorageCellChoice> combo = new ComboBox<>();

                            {
                                combo.setMaxWidth(Double.MAX_VALUE);
                                combo.valueProperty()
                                        .addListener(
                                                (obs, oldValue, newValue) -> {
                                                    TransferAllocationRow row =
                                                            getTableRow() == null
                                                                    ? null
                                                                    : getTableRow().getItem();
                                                    if (row != null) {
                                                        row.setDestinationCell(newValue);
                                                    }
                                                });
                            }

                            @Override
                            protected void updateItem(StorageCellChoice item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty
                                        || getTableRow() == null
                                        || getTableRow().getItem() == null) {
                                    setGraphic(null);
                                    return;
                                }
                                TransferAllocationRow row = getTableRow().getItem();
                                combo.setItems(row.destinationCellChoices());
                                combo.setValue(row.destinationCell());
                                setGraphic(combo);
                            }
                        });

        transferLinesTable.setRowFactory(
                table -> {
                    TableRow<TransferLineRow> row = new TableRow<>();
                    row.addEventHandler(
                            MouseEvent.MOUSE_PRESSED,
                            event -> {
                                if (!row.isEmpty()) {
                                    TransferLineRow item = row.getItem();
                                    table.getSelectionModel().select(item);
                                    viewModel.selectTransferLine(item.lineId());
                                    syncTransferAllocationsTable();
                                }
                            });
                    return row;
                });

        transferLinesTable
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (obs, oldValue, selected) -> {
                            if (selected != null) {
                                viewModel.selectTransferLine(selected.lineId());
                            }
                            syncTransferAllocationsTable();
                            transferLinesTable.refresh();
                        });

        viewModel
                .selectedTransferLineIdProperty()
                .addListener(
                        (obs, oldValue, lineId) -> {
                            if (lineId != null) {
                                TransferLineRow row = viewModel.findTransferLine(lineId);
                                if (row != null) {
                                    transferLinesTable.getSelectionModel().select(row);
                                }
                            }
                            syncTransferAllocationsTable();
                        });

        viewModel
                .transferLines()
                .addListener(
                        (ListChangeListener<TransferLineRow>)
                                change -> {
                                    UUID lineId =
                                            viewModel.selectedTransferLineIdProperty().get();
                                    if (lineId != null) {
                                        TransferLineRow row = viewModel.findTransferLine(lineId);
                                        if (row != null) {
                                            transferLinesTable.getSelectionModel().select(row);
                                        }
                                    }
                                    syncTransferAllocationsTable();
                                });

        addTransferAllocationButton.setOnAction(
                e -> {
                    TransferLineRow selected =
                            transferLinesTable.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        viewModel.addTransferAllocation(selected);
                        syncTransferAllocationsTable();
                        transferLinesTable.refresh();
                    }
                });
        removeTransferAllocationButton.setOnAction(
                e -> {
                    TransferLineRow line =
                            transferLinesTable.getSelectionModel().getSelectedItem();
                    TransferAllocationRow allocation =
                            transferAllocationsTable.getSelectionModel().getSelectedItem();
                    if (line != null && allocation != null) {
                        viewModel.removeTransferAllocation(line, allocation);
                        syncTransferAllocationsTable();
                        transferLinesTable.refresh();
                    }
                });
        applyRequestedQtyButton.setOnAction(
                e -> {
                    TransferLineRow selected =
                            transferLinesTable.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        viewModel.applyTransferRequestedQuantity(selected);
                        syncTransferAllocationsTable();
                    }
                });
        excludeTransferLineButton.setOnAction(
                e -> {
                    TransferLineRow selected =
                            transferLinesTable.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        viewModel.excludeTransferLine(selected);
                    }
                });
        restoreTransferLineButton.setOnAction(
                e -> {
                    TransferLineRow selected =
                            transferLinesTable.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        viewModel.restoreTransferLine(selected);
                    }
                });
        confirmTransferButton.disableProperty().bind(viewModel.canTransferProperty().not());
        confirmTransferButton.setOnAction(e -> viewModel.confirmTransfer());
    }

    private void bindReleasePanel() {
        releasePanel.visibleProperty().bind(viewModel.releasePanelVisibleProperty());
        releasePanel.managedProperty().bind(viewModel.releasePanelVisibleProperty());
        releaseMaterialsTable.setEditable(true);
        releaseMaterialColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().materialLabel()));
        releasePlannedColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().plannedQuantity()));
        releaseActualColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        releaseActualColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().actualQuantity()));
        releaseActualColumn.setOnEditCommit(
                event -> {
                    event.getRowValue().setActualQuantity(event.getNewValue());
                    releaseMaterialsTable.refresh();
                });
        releaseAllocationSummaryColumn.setCellValueFactory(
                c ->
                        new javafx.beans.property.SimpleStringProperty(
                                c.getValue().allocationSummary()));
        releaseMaterialsTable.setItems(viewModel.releaseMaterialRows());

        releaseAllocationsTable.setItems(emptyReleaseAllocations);
        releaseAllocationsTable.setEditable(true);
        releaseAllocQtyColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        releaseAllocQtyColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().quantity()));
        releaseAllocQtyColumn.setOnEditCommit(
                event -> event.getRowValue().setQuantity(event.getNewValue()));
        releaseAllocCellColumn.setCellValueFactory(
                c ->
                        new javafx.beans.property.SimpleObjectProperty<>(
                                c.getValue().productionCell()));
        releaseAllocCellColumn.setCellFactory(
                col ->
                        new TableCell<>() {
                            private final ComboBox<StorageCellChoice> combo = new ComboBox<>();

                            {
                                combo.setMaxWidth(Double.MAX_VALUE);
                                combo.valueProperty()
                                        .addListener(
                                                (obs, oldValue, newValue) -> {
                                                    ReleaseCellAllocationRow row =
                                                            getTableRow() == null
                                                                    ? null
                                                                    : getTableRow().getItem();
                                                    if (row != null) {
                                                        row.setProductionCell(newValue);
                                                    }
                                                });
                            }

                            @Override
                            protected void updateItem(StorageCellChoice item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty
                                        || getTableRow() == null
                                        || getTableRow().getItem() == null) {
                                    setGraphic(null);
                                    return;
                                }
                                ReleaseCellAllocationRow row = getTableRow().getItem();
                                combo.setItems(row.cellChoices());
                                combo.setValue(row.productionCell());
                                setGraphic(combo);
                            }
                        });

        releaseMaterialsTable.setRowFactory(
                table -> {
                    TableRow<ReleaseMaterialRow> row = new TableRow<>();
                    row.addEventHandler(
                            MouseEvent.MOUSE_PRESSED,
                            event -> {
                                if (!row.isEmpty()) {
                                    ReleaseMaterialRow item = row.getItem();
                                    table.getSelectionModel().select(item);
                                    syncReleaseAllocationsTable();
                                }
                            });
                    return row;
                });

        releaseMaterialsTable
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (obs, oldValue, selected) -> {
                            syncReleaseAllocationsTable();
                            releaseMaterialsTable.refresh();
                        });

        addReleaseAllocationButton.setOnAction(
                e -> {
                    ReleaseMaterialRow selected =
                            releaseMaterialsTable.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        viewModel.addReleaseAllocation(selected);
                        syncReleaseAllocationsTable();
                        releaseMaterialsTable.refresh();
                    }
                });
        removeReleaseAllocationButton.setOnAction(
                e -> {
                    ReleaseMaterialRow material =
                            releaseMaterialsTable.getSelectionModel().getSelectedItem();
                    ReleaseCellAllocationRow allocation =
                            releaseAllocationsTable.getSelectionModel().getSelectedItem();
                    if (material != null && allocation != null) {
                        viewModel.removeReleaseAllocation(material, allocation);
                        syncReleaseAllocationsTable();
                        releaseMaterialsTable.refresh();
                    }
                });
        confirmReleaseButton.disableProperty().bind(viewModel.canReleaseProperty().not());
        confirmReleaseButton.setOnAction(e -> viewModel.confirmRelease());
    }

    private void syncTransferAllocationsTable() {
        TransferLineRow selected = transferLinesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UUID lineId = viewModel.selectedTransferLineIdProperty().get();
            if (lineId != null) {
                selected = viewModel.findTransferLine(lineId);
            }
        }
        if (selected == null) {
            transferAllocationsTable.setItems(emptyTransferAllocations);
            return;
        }
        transferAllocationsTable.setItems(selected.allocations());
    }

    private void syncReleaseAllocationsTable() {
        ReleaseMaterialRow selected = releaseMaterialsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            releaseAllocationsTable.setItems(emptyReleaseAllocations);
            return;
        }
        releaseAllocationsTable.setItems(selected.allocations());
    }

    private void confirmCancel() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Отмена производства");
        alert.setHeaderText("Отменить производство заказа целиком?");
        alert.setContentText(
                "Будет отменено производство всего заказа. Отдельные позиции выбрать нельзя.");
        alert.showAndWait()
                .filter(response -> response == ButtonType.OK)
                .ifPresent(response -> viewModel.cancelProduction());
    }
}
