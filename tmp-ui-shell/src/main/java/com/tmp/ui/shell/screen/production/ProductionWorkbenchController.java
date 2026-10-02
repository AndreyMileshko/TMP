package com.tmp.ui.shell.screen.production;

import com.tmp.order.api.OrderId;
import com.tmp.ui.shell.navigation.ViewModelAware;
import com.tmp.ui.shell.order.worklist.OrderListPeriod;
import com.tmp.ui.shell.theme.TmpTheme;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Production workbench FXML controller. LEVEL 1 is the Order→Item tree; detail panels are
 * transitional until the Order Card phase. Confirmation dialogs live here; no business logic.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX Controller retains ViewModel for FXML wiring")
public final class ProductionWorkbenchController
        implements ViewModelAware<ProductionWorkbenchViewModel> {

    @FXML
    private Label titleLabel;

    @FXML
    private Label emptyStateLabel;

    @FXML
    private Label statusMessageLabel;

    @FXML
    private Label errorMessageLabel;

    @FXML
    private Label loadingLabel;

    @FXML
    private Label selectionCountLabel;

    @FXML
    private VBox treePane;

    @FXML
    private VBox detailPane;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<ProductionTreeStatusFilter> statusFilterCombo;

    @FXML
    private ComboBox<OrderListPeriod.Preset> periodPresetCombo;

    @FXML
    private Button refreshButton;

    @FXML
    private TreeTableView<ProductionTreeNode> productionTree;

    @FXML
    private TreeTableColumn<ProductionTreeNode, Boolean> treeSelectedColumn;

    @FXML
    private TreeTableColumn<ProductionTreeNode, String> treeIdentityColumn;

    @FXML
    private TreeTableColumn<ProductionTreeNode, String> treeSecondaryColumn;

    @FXML
    private TreeTableColumn<ProductionTreeNode, String> treeQuantityColumn;

    @FXML
    private TreeTableColumn<ProductionTreeNode, String> treeStatusColumn;

    @FXML
    private TreeTableColumn<ProductionTreeNode, String> treeReleasedColumn;

    @FXML
    private TreeTableColumn<ProductionTreeNode, String> treeRemainingColumn;

    @FXML
    private Button backToTreeButton;

    @FXML
    private Button detailRefreshButton;

    @FXML
    private Label orderNumberLabel;

    @FXML
    private Label customerLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label statusDetailLabel;

    @FXML
    private Button acceptButton;

    @FXML
    private Button checkMaterialsButton;

    @FXML
    private Button prepareTransferButton;

    @FXML
    private Button prepareReleaseButton;

    @FXML
    private Button cancelProductionButton;

    @FXML
    private ScrollPane rootScroll;

    @FXML
    private TableView<ProductionItemRow> itemsTable;

    @FXML
    private TableColumn<ProductionItemRow, Boolean> itemSelectedColumn;

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
    private VBox materialRequirementPanel;

    @FXML
    private TableView<MaterialRequirementLineRow> requirementLinesTable;

    @FXML
    private TableColumn<MaterialRequirementLineRow, String> requirementCodeColumn;

    @FXML
    private TableColumn<MaterialRequirementLineRow, String> requirementNameColumn;

    @FXML
    private TableColumn<MaterialRequirementLineRow, String> requirementColorColumn;

    @FXML
    private TableColumn<MaterialRequirementLineRow, String> requirementQtyColumn;

    @FXML
    private TableColumn<MaterialRequirementLineRow, String> requirementUomColumn;

    @FXML
    private Button applyRequirementQtyButton;

    @FXML
    private Button submitRequirementButton;

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

    private final TreeItem<ProductionTreeNode> treeRoot = new TreeItem<>();
    private final Map<UUID, TreeItem<ProductionTreeNode>> orderTreeItems = new HashMap<>();
    private final ObservableList<ReleaseCellAllocationRow> emptyReleaseAllocations =
            FXCollections.observableArrayList();
    private boolean rebuildingTree;

    @Override
    public void setViewModel(ProductionWorkbenchViewModel viewModel) {
        this.viewModel = viewModel;
        bind();
        viewModel.loadTree();
    }

    private void bind() {
        titleLabel.setText("Производство");
        emptyStateLabel.textProperty().bind(viewModel.emptyStateMessageProperty());
        statusMessageLabel.textProperty().bind(viewModel.statusMessageProperty());
        errorMessageLabel.textProperty().bind(viewModel.errorMessageProperty());
        loadingLabel.visibleProperty().bind(viewModel.loadingProperty());
        selectionCountLabel.textProperty().bind(viewModel.selectionCountLabelProperty());

        treePane.visibleProperty().bind(viewModel.treeVisibleProperty());
        treePane.managedProperty().bind(viewModel.treeVisibleProperty());
        detailPane.visibleProperty().bind(viewModel.detailVisibleProperty());
        detailPane.managedProperty().bind(viewModel.detailVisibleProperty());

        orderNumberLabel.textProperty().bind(viewModel.orderNumberProperty());
        customerLabel.textProperty().bind(viewModel.customerLabelProperty());
        statusLabel.textProperty().bind(viewModel.statusLabelProperty());
        statusDetailLabel.textProperty().bind(viewModel.statusDetailLabelProperty());

        searchField.textProperty().bindBidirectional(viewModel.searchTextProperty());

        statusFilterCombo.setItems(FXCollections.observableArrayList(ProductionTreeStatusFilter.values()));
        statusFilterCombo.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(ProductionTreeStatusFilter value) {
                        return value == null ? "" : value.caption();
                    }

                    @Override
                    public ProductionTreeStatusFilter fromString(String string) {
                        return ProductionTreeStatusFilter.IN_PROGRESS;
                    }
                });
        statusFilterCombo.valueProperty().bindBidirectional(viewModel.statusFilterProperty());

        periodPresetCombo.setItems(
                FXCollections.observableArrayList(
                        OrderListPeriod.Preset.TODAY,
                        OrderListPeriod.Preset.LAST_7_DAYS,
                        OrderListPeriod.Preset.LAST_30_DAYS,
                        OrderListPeriod.Preset.CURRENT_MONTH));
        periodPresetCombo.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(OrderListPeriod.Preset value) {
                        if (value == null) {
                            return "";
                        }
                        return switch (value) {
                            case TODAY -> "Сегодня";
                            case LAST_7_DAYS -> "7 дней";
                            case LAST_30_DAYS -> "30 дней";
                            case CURRENT_MONTH -> "Текущий месяц";
                            case CUSTOM -> "Период";
                        };
                    }

                    @Override
                    public OrderListPeriod.Preset fromString(String string) {
                        return OrderListPeriod.Preset.LAST_30_DAYS;
                    }
                });
        periodPresetCombo.valueProperty().bindBidirectional(viewModel.periodPresetProperty());

        refreshButton.setOnAction(e -> viewModel.refresh());
        detailRefreshButton.setOnAction(e -> viewModel.refresh());
        backToTreeButton.setOnAction(e -> viewModel.backToTree());

        acceptButton
                .disableProperty()
                .bind(viewModel.canAcceptProperty().not().or(viewModel.loadingProperty()));
        checkMaterialsButton
                .disableProperty()
                .bind(viewModel.canCheckProperty().not().or(viewModel.loadingProperty()));
        prepareTransferButton
                .disableProperty()
                .bind(viewModel.canTransferProperty().not().or(viewModel.loadingProperty()));
        prepareReleaseButton
                .disableProperty()
                .bind(viewModel.canReleaseProperty().not().or(viewModel.loadingProperty()));
        cancelProductionButton
                .disableProperty()
                .bind(viewModel.canCancelProperty().not().or(viewModel.loadingProperty()));

        acceptButton.setOnAction(e -> viewModel.acceptOrder());
        checkMaterialsButton.setOnAction(e -> viewModel.checkMaterials());
        prepareTransferButton.setOnAction(e -> viewModel.prepareMaterialRequirement());
        prepareReleaseButton.setOnAction(e -> viewModel.prepareRelease());
        cancelProductionButton.setOnAction(e -> confirmCancel());

        bindProductionTree();
        bindItemsTable();
        bindMaterialsTable();
        bindHistoryTable();
        bindMaterialRequirementPanel();
        bindReleasePanel();
    }

    private void bindProductionTree() {
        productionTree.setRoot(treeRoot);
        productionTree.setShowRoot(false);
        productionTree.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        treeIdentityColumn.setCellValueFactory(
                c -> new SimpleStringProperty(
                        c.getValue() == null || c.getValue().getValue() == null
                                ? ""
                                : c.getValue().getValue().identityLabel()));
        treeSecondaryColumn.setCellValueFactory(
                c -> new SimpleStringProperty(
                        c.getValue() == null || c.getValue().getValue() == null
                                ? ""
                                : c.getValue().getValue().secondaryLabel()));
        treeQuantityColumn.setCellValueFactory(
                c -> new SimpleStringProperty(
                        c.getValue() == null || c.getValue().getValue() == null
                                ? ""
                                : c.getValue().getValue().quantityLabel()));
        treeStatusColumn.setCellValueFactory(
                c -> new SimpleStringProperty(
                        c.getValue() == null || c.getValue().getValue() == null
                                ? ""
                                : c.getValue().getValue().statusLabel()));
        treeReleasedColumn.setCellValueFactory(
                c -> new SimpleStringProperty(
                        c.getValue() == null || c.getValue().getValue() == null
                                ? ""
                                : c.getValue().getValue().releasedLabel()));
        treeRemainingColumn.setCellValueFactory(
                c -> new SimpleStringProperty(
                        c.getValue() == null || c.getValue().getValue() == null
                                ? ""
                                : c.getValue().getValue().remainingLabel()));

        treeSelectedColumn.setSortable(false);
        treeSelectedColumn.setCellFactory(col -> new SelectionCheckCell());

        productionTree.setRowFactory(
                table -> {
                    TreeTableRow<ProductionTreeNode> row = new TreeTableRow<>();
                    row.addEventFilter(
                            MouseEvent.MOUSE_CLICKED,
                            event -> {
                                if (event.getButton() != MouseButton.PRIMARY
                                        || event.getClickCount() != 2
                                        || row.isEmpty()
                                        || row.getItem() == null) {
                                    return;
                                }
                                ProductionTreeNode node = row.getItem();
                                if (node.isOrder()) {
                                    viewModel.openOrderDetail(OrderId.of(node.sourceOrderId()));
                                    event.consume();
                                }
                                // Item double-click must not toggle checkbox.
                            });
                    return row;
                });

        viewModel
                .visibleTreeProperty()
                .addListener((obs, oldValue, newValue) -> rebuildTree(newValue));
        viewModel
                .selectionModel()
                .revisionProperty()
                .addListener((obs, oldValue, newValue) -> productionTree.refresh());

        rebuildTree(viewModel.visibleTreeProperty().get());
    }

    private void rebuildTree(List<ProductionWorkbenchViewModel.TreeOrderModel> models) {
        rebuildingTree = true;
        try {
            orderTreeItems.clear();
            treeRoot.getChildren().clear();
            if (models == null) {
                return;
            }
            for (ProductionWorkbenchViewModel.TreeOrderModel model : models) {
                TreeItem<ProductionTreeNode> orderItem = new TreeItem<>(model.orderNode());
                orderItem.setExpanded(model.expanded());
                for (ProductionTreeNode itemNode : model.items()) {
                    orderItem.getChildren().add(new TreeItem<>(itemNode));
                }
                orderItem
                        .expandedProperty()
                        .addListener(
                                (obs, wasExpanded, expanded) -> {
                                    if (rebuildingTree) {
                                        return;
                                    }
                                    viewModel.setOrderExpanded(
                                            model.orderNode().sourceOrderId(), expanded);
                                });
                orderTreeItems.put(model.orderNode().sourceOrderId(), orderItem);
                treeRoot.getChildren().add(orderItem);
            }
        } finally {
            rebuildingTree = false;
        }
    }

    private final class SelectionCheckCell
            extends TreeTableCell<ProductionTreeNode, Boolean> {
        private final CheckBox checkBox = new CheckBox();

        private SelectionCheckCell() {
            checkBox.setAllowIndeterminate(true);
            checkBox.addEventFilter(
                    MouseEvent.MOUSE_CLICKED,
                    event -> {
                        // Keep row focus independent of business selection.
                        event.consume();
                    });
            checkBox.setOnAction(
                    e -> {
                        ProductionTreeNode node =
                                getTreeTableRow() == null ? null : getTreeTableRow().getItem();
                        if (node == null) {
                            return;
                        }
                        if (node.isOrder()) {
                            // After a click, indeterminate is cleared; selected means select-all.
                            if (checkBox.isSelected() && !checkBox.isIndeterminate()) {
                                viewModel.selectOrder(node.sourceOrderId());
                            } else {
                                viewModel.deselectOrder(node.sourceOrderId());
                            }
                        } else {
                            viewModel.setItemSelected(node.itemRef(), checkBox.isSelected());
                        }
                    });
        }

        @Override
        protected void updateItem(Boolean item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getTreeTableRow() == null || getTreeTableRow().getItem() == null) {
                setGraphic(null);
                return;
            }
            ProductionTreeNode node = getTreeTableRow().getItem();
            if (node.isOrder()) {
                ProductionTreeSelectionModel.OrderCheckState state =
                        viewModel
                                .selectionModel()
                                .orderCheckState(node.sourceOrderId(), node.childRefs());
                switch (state) {
                    case CHECKED -> {
                        checkBox.setIndeterminate(false);
                        checkBox.setSelected(true);
                    }
                    case UNCHECKED -> {
                        checkBox.setIndeterminate(false);
                        checkBox.setSelected(false);
                    }
                    case INDETERMINATE -> checkBox.setIndeterminate(true);
                }
            } else {
                checkBox.setIndeterminate(false);
                checkBox.setSelected(viewModel.selectionModel().isItemSelected(node.itemRef()));
            }
            setGraphic(checkBox);
        }
    }

    private void bindItemsTable() {
        itemsTable.setEditable(true);
        itemSelectedColumn.setCellValueFactory(
                c ->
                        new javafx.beans.property.SimpleBooleanProperty(
                                c.getValue().isSelected()));
        itemSelectedColumn.setCellFactory(
                col ->
                        new TableCell<>() {
                            private final CheckBox checkBox = new CheckBox();

                            {
                                checkBox.setOnAction(
                                        e -> {
                                            ProductionItemRow row =
                                                    getTableRow() == null
                                                            ? null
                                                            : getTableRow().getItem();
                                            if (row != null && row.isSelectable()) {
                                                row.setSelected(checkBox.isSelected());
                                            } else if (row != null) {
                                                checkBox.setSelected(false);
                                            }
                                        });
                            }

                            @Override
                            protected void updateItem(Boolean item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty
                                        || getTableRow() == null
                                        || getTableRow().getItem() == null) {
                                    setGraphic(null);
                                    return;
                                }
                                ProductionItemRow row = getTableRow().getItem();
                                checkBox.setDisable(!row.isSelectable());
                                checkBox.setSelected(row.isSelected());
                                setGraphic(checkBox);
                            }
                        });
        itemPositionColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().positionLabel()));
        itemStatusColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().statusLabel()));
        itemOrderedColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().orderedQuantity()));
        itemActiveColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().activeQuantity()));
        itemReleasedColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().releasedQuantity()));
        itemReleaseQtyColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        itemReleaseQtyColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().releaseQuantityInput()));
        itemReleaseQtyColumn.setOnEditCommit(
                event -> event.getRowValue().setReleaseQuantityInput(event.getNewValue()));
        itemsTable.setItems(viewModel.itemRows());
    }

    private void bindMaterialsTable() {
        materialNameColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().material()));
        materialRequiredColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().required()));
        materialMainColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().mainWarehouse()));
        materialProductionColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().productionWarehouse()));
        materialTotalColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().totalAvailable()));
        materialDeficitColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().deficit()));
        materialSourceColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().planningSource()));
        materialsTable.setItems(viewModel.materialRows());
    }

    private void bindHistoryTable() {
        historyTimeColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().occurredAt()));
        historyTypeColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().typeLabel()));
        historyActorColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().actor()));
        historySummaryColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().summary()));
        historyTable.setItems(viewModel.historyRows());
    }

    private void bindMaterialRequirementPanel() {
        materialRequirementPanel
                .visibleProperty()
                .bind(viewModel.materialRequirementPanelVisibleProperty());
        materialRequirementPanel
                .managedProperty()
                .bind(viewModel.materialRequirementPanelVisibleProperty());
        requirementLinesTable.setEditable(true);
        requirementCodeColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().materialCode()));
        requirementNameColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().materialName()));
        requirementColorColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().color()));
        requirementQtyColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        requirementQtyColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().quantity()));
        requirementQtyColumn.setOnEditCommit(
                event -> event.getRowValue().setQuantity(event.getNewValue()));
        requirementUomColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().unitOfMeasure()));
        requirementLinesTable.setItems(viewModel.requirementLines());

        requirementLinesTable.setRowFactory(
                table -> {
                    TableRow<MaterialRequirementLineRow> row = new TableRow<>();
                    row.addEventHandler(
                            MouseEvent.MOUSE_PRESSED,
                            event -> {
                                if (!row.isEmpty()) {
                                    MaterialRequirementLineRow item = row.getItem();
                                    table.getSelectionModel().select(item);
                                    viewModel.selectRequirementLine(item.lineId());
                                }
                            });
                    return row;
                });

        requirementLinesTable
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (obs, oldValue, selected) -> {
                            if (selected != null) {
                                viewModel.selectRequirementLine(selected.lineId());
                            }
                            requirementLinesTable.refresh();
                        });

        viewModel
                .selectedRequirementLineIdProperty()
                .addListener(
                        (obs, oldValue, lineId) -> {
                            if (lineId != null) {
                                MaterialRequirementLineRow row =
                                        viewModel.findRequirementLine(lineId);
                                if (row != null) {
                                    requirementLinesTable.getSelectionModel().select(row);
                                }
                            }
                        });

        viewModel
                .requirementLines()
                .addListener(
                        (ListChangeListener<MaterialRequirementLineRow>)
                                change -> {
                                    UUID lineId =
                                            viewModel.selectedRequirementLineIdProperty().get();
                                    if (lineId != null) {
                                        MaterialRequirementLineRow row =
                                                viewModel.findRequirementLine(lineId);
                                        if (row != null) {
                                            requirementLinesTable.getSelectionModel().select(row);
                                        }
                                    }
                                });

        applyRequirementQtyButton
                .disableProperty()
                .bind(viewModel.requirementSubmittedProperty());
        applyRequirementQtyButton.setOnAction(
                e -> {
                    MaterialRequirementLineRow selected =
                            requirementLinesTable.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        viewModel.applyRequirementQuantity(selected);
                    }
                });

        submitRequirementButton
                .disableProperty()
                .bind(
                        viewModel
                                .canTransferProperty()
                                .not()
                                .or(viewModel.requirementSubmittedProperty()));
        submitRequirementButton.setOnAction(e -> viewModel.submitMaterialRequirement());
    }

    private void bindReleasePanel() {
        releasePanel.visibleProperty().bind(viewModel.releasePanelVisibleProperty());
        releasePanel.managedProperty().bind(viewModel.releasePanelVisibleProperty());
        releaseMaterialsTable.setEditable(true);
        releaseMaterialColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().materialLabel()));
        releasePlannedColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().plannedQuantity()));
        releaseActualColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        releaseActualColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().actualQuantity()));
        releaseActualColumn.setOnEditCommit(
                event -> {
                    event.getRowValue().setActualQuantity(event.getNewValue());
                    releaseMaterialsTable.refresh();
                });
        releaseAllocationSummaryColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().allocationSummary()));
        releaseMaterialsTable.setItems(viewModel.releaseMaterialRows());

        releaseAllocationsTable.setItems(emptyReleaseAllocations);
        releaseAllocationsTable.setEditable(true);
        releaseAllocQtyColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        releaseAllocQtyColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().quantity()));
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
        TmpTheme.apply(alert.getDialogPane());
        alert.setTitle("Отмена производства");
        alert.setHeaderText("Отменить производство заказа целиком?");
        alert.setContentText(
                "Будет отменено производство всего заказа. Отдельные позиции выбрать нельзя.");
        alert.showAndWait()
                .filter(response -> response == ButtonType.OK)
                .ifPresent(response -> viewModel.cancelProduction());
    }
}
