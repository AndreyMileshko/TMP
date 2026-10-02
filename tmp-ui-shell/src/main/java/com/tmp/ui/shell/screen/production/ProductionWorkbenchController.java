package com.tmp.ui.shell.screen.production;

import com.tmp.order.api.OrderId;
import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
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
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Production workbench FXML controller. LEVEL 1 Order→Item tree; LEVEL 2 Order Card. Confirmation
 * dialogs live here; no business logic.
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
    private Label orderTitleLabel;

    @FXML
    private Label customerLabel;

    @FXML
    private Label siteLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label progressLabel;

    @FXML
    private Button acceptButton;

    @FXML
    private ToggleGroup quantityModeGroup;

    @FXML
    private RadioButton standardModeRadio;

    @FXML
    private RadioButton flexibleModeRadio;

    @FXML
    private Label quantityModeHintLabel;

    @FXML
    private Button saveQuantityModeButton;

    @FXML
    private TableView<ProductionItemRow> itemsTable;

    @FXML
    private TableColumn<ProductionItemRow, String> itemPositionColumn;

    @FXML
    private TableColumn<ProductionItemRow, String> itemProductColumn;

    @FXML
    private TableColumn<ProductionItemRow, String> itemQuantityColumn;

    @FXML
    private TableColumn<ProductionItemRow, String> itemStatusColumn;

    @FXML
    private TableColumn<ProductionItemRow, String> itemReleasedColumn;

    @FXML
    private TableColumn<ProductionItemRow, String> itemRemainingColumn;

    private ProductionWorkbenchViewModel viewModel;

    private final TreeItem<ProductionTreeNode> treeRoot = new TreeItem<>();
    private final Map<UUID, TreeItem<ProductionTreeNode>> orderTreeItems = new HashMap<>();
    private boolean rebuildingTree;
    private boolean suppressingModeUi;

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

        orderTitleLabel.textProperty().bind(viewModel.orderTitleProperty());
        customerLabel.textProperty().bind(viewModel.customerLabelProperty());
        siteLabel.textProperty().bind(viewModel.siteLabelProperty());
        statusLabel.textProperty().bind(viewModel.statusLabelProperty());
        progressLabel.textProperty().bind(viewModel.progressLabelProperty());
        quantityModeHintLabel.textProperty().bind(viewModel.quantityModeHintProperty());

        searchField.textProperty().bindBidirectional(viewModel.searchTextProperty());

        statusFilterCombo.setItems(
                FXCollections.observableArrayList(ProductionTreeStatusFilter.values()));
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
        acceptButton.visibleProperty().bind(viewModel.canAcceptProperty());
        acceptButton.managedProperty().bind(viewModel.canAcceptProperty());
        acceptButton.setOnAction(e -> confirmAccept());

        bindQuantityModeControls();
        bindProductionTree();
        bindItemsTable();
    }

    private void bindQuantityModeControls() {
        standardModeRadio
                .disableProperty()
                .bind(
                        viewModel
                                .canEditQuantityModeProperty()
                                .not()
                                .or(viewModel.loadingProperty()));
        flexibleModeRadio
                .disableProperty()
                .bind(
                        viewModel
                                .canEditQuantityModeProperty()
                                .not()
                                .or(viewModel.loadingProperty()));
        saveQuantityModeButton
                .disableProperty()
                .bind(
                        viewModel
                                .quantityModeDirtyProperty()
                                .not()
                                .or(viewModel.canEditQuantityModeProperty().not())
                                .or(viewModel.loadingProperty()));
        saveQuantityModeButton.visibleProperty().bind(viewModel.canEditQuantityModeProperty());
        saveQuantityModeButton.managedProperty().bind(viewModel.canEditQuantityModeProperty());
        saveQuantityModeButton.setOnAction(e -> viewModel.saveQuantityMode());

        standardModeRadio.setOnAction(
                e -> {
                    if (!suppressingModeUi) {
                        viewModel.selectQuantityMode(QuantityModeView.STANDARD);
                    }
                });
        flexibleModeRadio.setOnAction(
                e -> {
                    if (!suppressingModeUi) {
                        viewModel.selectQuantityMode(QuantityModeView.FLEXIBLE);
                    }
                });

        viewModel
                .selectedQuantityModeProperty()
                .addListener((obs, oldValue, newValue) -> syncModeRadios(newValue));
        syncModeRadios(viewModel.selectedQuantityModeProperty().get());
    }

    private void syncModeRadios(QuantityModeView mode) {
        suppressingModeUi = true;
        try {
            if (mode == QuantityModeView.FLEXIBLE) {
                flexibleModeRadio.setSelected(true);
            } else {
                standardModeRadio.setSelected(true);
            }
        } finally {
            suppressingModeUi = false;
        }
    }

    private void bindProductionTree() {
        productionTree.setRoot(treeRoot);
        productionTree.setShowRoot(false);
        productionTree.setColumnResizePolicy(
                TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        treeIdentityColumn.setCellValueFactory(
                c ->
                        new SimpleStringProperty(
                                c.getValue() == null || c.getValue().getValue() == null
                                        ? ""
                                        : c.getValue().getValue().identityLabel()));
        treeSecondaryColumn.setCellValueFactory(
                c ->
                        new SimpleStringProperty(
                                c.getValue() == null || c.getValue().getValue() == null
                                        ? ""
                                        : c.getValue().getValue().secondaryLabel()));
        treeQuantityColumn.setCellValueFactory(
                c ->
                        new SimpleStringProperty(
                                c.getValue() == null || c.getValue().getValue() == null
                                        ? ""
                                        : c.getValue().getValue().quantityLabel()));
        treeStatusColumn.setCellValueFactory(
                c ->
                        new SimpleStringProperty(
                                c.getValue() == null || c.getValue().getValue() == null
                                        ? ""
                                        : c.getValue().getValue().statusLabel()));
        treeReleasedColumn.setCellValueFactory(
                c ->
                        new SimpleStringProperty(
                                c.getValue() == null || c.getValue().getValue() == null
                                        ? ""
                                        : c.getValue().getValue().releasedLabel()));
        treeRemainingColumn.setCellValueFactory(
                c ->
                        new SimpleStringProperty(
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

    private final class SelectionCheckCell extends TreeTableCell<ProductionTreeNode, Boolean> {
        private final CheckBox checkBox = new CheckBox();

        private SelectionCheckCell() {
            checkBox.setAllowIndeterminate(true);
            checkBox.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
            checkBox.setOnAction(
                    e -> {
                        ProductionTreeNode node =
                                getTreeTableRow() == null ? null : getTreeTableRow().getItem();
                        if (node == null) {
                            return;
                        }
                        if (node.isOrder()) {
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
        itemsTable.setEditable(false);
        itemPositionColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().positionLabel()));
        itemProductColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().productLabel()));
        itemQuantityColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().quantityLabel()));
        itemStatusColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().statusLabel()));
        itemReleasedColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().releasedLabel()));
        itemRemainingColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().remainingLabel()));
        itemsTable.setItems(viewModel.itemRows());
    }

    private void confirmAccept() {
        String number = viewModel.currentOrderNumber();
        String orderCaption = number == null || number.isBlank() ? "заказ" : "заказ №" + number;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        TmpTheme.apply(alert.getDialogPane());
        alert.setTitle("Принять в производство");
        alert.setHeaderText("Принять " + orderCaption + " в производство?");
        alert.setContentText("Будут приняты все активные позиции заказа.");
        Button accept = (Button) alert.getDialogPane().lookupButton(ButtonType.OK);
        if (accept != null) {
            accept.setText("Принять");
        }
        Button cancel = (Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancel != null) {
            cancel.setText("Отмена");
        }
        alert.showAndWait()
                .filter(response -> response == ButtonType.OK)
                .ifPresent(response -> viewModel.acceptOrder());
    }
}
