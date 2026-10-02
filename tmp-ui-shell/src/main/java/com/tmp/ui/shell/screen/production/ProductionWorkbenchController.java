package com.tmp.ui.shell.screen.production;

import com.tmp.order.api.OrderId;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementDraftSummaryView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementView;
import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
import com.tmp.production.api.ProductionApplicationApi.SubmitMaterialRequirementResultView;
import com.tmp.ui.shell.navigation.ViewModelAware;
import com.tmp.ui.shell.order.worklist.OrderListPeriod;
import com.tmp.ui.shell.theme.TmpTheme;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private Button requestMaterialsButton;

    @FXML
    private Button materialDraftsButton;

    @FXML
    private Button releaseButton;

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
    private VBox materialsBlock;

    @FXML
    private Label materialsSummaryLabel;

    @FXML
    private Label materialsDetailLabel;

    @FXML
    private Button materialsDetailsButton;

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

        requestMaterialsButton.visibleProperty().bind(viewModel.canRequestMaterialsProperty());
        requestMaterialsButton.managedProperty().bind(viewModel.canRequestMaterialsProperty());
        requestMaterialsButton
                .disableProperty()
                .bind(
                        viewModel
                                .requestMaterialsEnabledProperty()
                                .not()
                                .or(viewModel.loadingProperty()));
        requestMaterialsButton.setOnAction(e -> startMaterialRequest());

        materialDraftsButton.visibleProperty().bind(viewModel.canOpenMaterialDraftsProperty());
        materialDraftsButton.managedProperty().bind(viewModel.canOpenMaterialDraftsProperty());
        materialDraftsButton.disableProperty().bind(viewModel.loadingProperty());
        materialDraftsButton.setOnAction(e -> openMaterialDrafts());

        releaseButton.visibleProperty().bind(viewModel.canReleaseProperty());
        releaseButton.managedProperty().bind(viewModel.canReleaseProperty());
        releaseButton
                .disableProperty()
                .bind(
                        viewModel
                                .releaseEnabledProperty()
                                .not()
                                .or(viewModel.loadingProperty()));
        releaseButton.setOnAction(e -> startRelease());

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

        materialsSummaryLabel.textProperty().bind(viewModel.materialsSummaryProperty());
        materialsDetailLabel.textProperty().bind(viewModel.materialsDetailProperty());
        materialsDetailLabel.visibleProperty().bind(viewModel.materialsDetailProperty().isNotEmpty());
        materialsDetailLabel.managedProperty().bind(materialsDetailLabel.visibleProperty());
        materialsDetailsButton.visibleProperty().bind(viewModel.materialsDetailsVisibleProperty());
        materialsDetailsButton.managedProperty().bind(viewModel.materialsDetailsVisibleProperty());
        materialsDetailsButton.setOnAction(e -> showMaterialReadinessDetails());

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

    private void startMaterialRequest() {
        ProductionWorkbenchViewModel.MaterialRequestStep1LoadResult loaded;
        try {
            loaded = viewModel.loadMaterialRequestStep1();
        } catch (RuntimeException ex) {
            viewModel.setErrorMessage(ProductionUiErrorMapper.text(ex));
            return;
        }
        if (loaded.accessDenied()) {
            viewModel.setErrorMessage(loaded.validationMessage());
            return;
        }
        if (!loaded.ok()) {
            MaterialRequestDialogSupport.showValidation(loaded.validationMessage());
            return;
        }
        runMaterialRequestFromStep1(loaded.rows());
    }

    private void startRelease() {
        ProductionWorkbenchViewModel.ReleaseStep1LoadResult loaded;
        try {
            loaded = viewModel.loadReleaseStep1();
        } catch (RuntimeException ex) {
            viewModel.setErrorMessage(ProductionUiErrorMapper.text(ex));
            return;
        }
        if (loaded.accessDenied()) {
            viewModel.setErrorMessage(loaded.validationMessage());
            return;
        }
        if (!loaded.ok()) {
            ReleaseDialogSupport.showValidation(loaded.validationMessage());
            return;
        }
        runReleaseFromStep1(loaded.rows());
    }

    private void runReleaseFromStep1(List<ReleaseQuantityRow> rows) {
        while (true) {
            ReleaseDialogSupport.Step1Outcome step1 = ReleaseDialogSupport.showStep1(rows);
            if (!step1.proceed()) {
                return;
            }
            ProductionWorkbenchViewModel.ReleaseStep1StaleResult stale;
            try {
                stale = viewModel.detectReleaseStep1Stale(step1.rows());
            } catch (RuntimeException ex) {
                viewModel.setErrorMessage(ProductionUiErrorMapper.text(ex));
                return;
            }
            if (stale.stale()) {
                ReleaseDialogSupport.showValidation(stale.message());
                if (stale.message().equals(ProductionUiErrorMapper.RELEASE_CANCELLED)) {
                    return;
                }
                ProductionWorkbenchViewModel.ReleaseStep1LoadResult reloaded;
                try {
                    reloaded = viewModel.loadReleaseStep1();
                } catch (RuntimeException ex) {
                    viewModel.setErrorMessage(ProductionUiErrorMapper.text(ex));
                    return;
                }
                if (!reloaded.ok()) {
                    if (!reloaded.validationMessage().isBlank()) {
                        ReleaseDialogSupport.showValidation(reloaded.validationMessage());
                    }
                    return;
                }
                rows = reloaded.rows();
                continue;
            }

            ProductionWorkbenchViewModel.ReleaseReadinessBatchResult readiness;
            try {
                readiness = viewModel.checkReleaseReadiness(step1.rows());
            } catch (RuntimeException ex) {
                viewModel.setErrorMessage(ProductionUiErrorMapper.text(ex));
                return;
            }
            if (!readiness.ready()) {
                ReleaseDialogSupport.showReadinessBlocked(readiness.blocking());
                return;
            }

            List<ReleaseMaterialRow> materials;
            try {
                materials = viewModel.prepareReleaseMaterialRows(step1.rows());
            } catch (RuntimeException ex) {
                viewModel.setErrorMessage(ProductionUiErrorMapper.text(ex));
                return;
            }
            ReleaseDialogSupport.Step2Outcome step2 =
                    ReleaseDialogSupport.showStep2(
                            materials, viewModel.canReleaseProperty().get());
            if (!step2.proceed()) {
                return;
            }
            if (!ReleaseDialogSupport.showConfirmSummary(step1.rows())) {
                return;
            }

            releaseButton.setDisable(true);
            try {
                ProductionWorkbenchViewModel.MultiOrderReleaseResult result =
                        viewModel.confirmReleases(step1.rows(), step2.materials());
                viewModel.afterSuccessfulRelease(result, step1.rows());
                ReleaseDialogSupport.showInfo(
                        ReleaseDialogSupport.STEP1_TITLE, result.summaryMessage());
            } catch (RuntimeException ex) {
                viewModel.setErrorMessage(ProductionUiErrorMapper.text(ex));
            } finally {
                releaseButton
                        .disableProperty()
                        .bind(
                                viewModel
                                        .releaseEnabledProperty()
                                        .not()
                                        .or(viewModel.loadingProperty()));
            }
            return;
        }
    }

    private void runMaterialRequestFromStep1(List<MaterialRequestQuantityRow> rows) {
        while (true) {
            MaterialRequestDialogSupport.Step1Outcome step1 =
                    MaterialRequestDialogSupport.showStep1(rows);
            if (!step1.proceed()) {
                return;
            }
            MaterialRequirementView prepared;
            try {
                prepared = viewModel.prepareMaterialRequirement(step1.rows());
            } catch (RuntimeException ex) {
                if (ProductionUiErrorMapper.isMaterialModeChanged(ex)
                        || ProductionUiErrorMapper.isMaterialCoverageChanged(ex)) {
                    MaterialRequestDialogSupport.showValidation(ProductionUiErrorMapper.text(ex));
                    ProductionWorkbenchViewModel.MaterialRequestStep1LoadResult reloaded;
                    try {
                        reloaded = viewModel.loadMaterialRequestStep1();
                    } catch (RuntimeException reloadEx) {
                        viewModel.setErrorMessage(ProductionUiErrorMapper.text(reloadEx));
                        return;
                    }
                    if (!reloaded.ok()) {
                        if (!reloaded.validationMessage().isBlank()) {
                            MaterialRequestDialogSupport.showValidation(reloaded.validationMessage());
                        }
                        return;
                    }
                    rows = reloaded.rows();
                    continue;
                }
                viewModel.setErrorMessage(ProductionUiErrorMapper.text(ex));
                return;
            }
            openMaterialRequirementDraft(prepared, true);
            return;
        }
    }

    private void openMaterialDrafts() {
        List<MaterialRequirementDraftSummaryView> drafts;
        try {
            drafts = viewModel.listMaterialRequirementDrafts();
        } catch (RuntimeException ex) {
            viewModel.setErrorMessage(ProductionUiErrorMapper.text(ex));
            return;
        }
        MaterialRequestDialogSupport.DraftListOutcome outcome =
                MaterialRequestDialogSupport.showDraftList(drafts);
        if (outcome.openRequirementId().isEmpty()) {
            return;
        }
        Optional<MaterialRequirementView> requirement;
        try {
            requirement = viewModel.getMaterialRequirement(outcome.openRequirementId().get());
        } catch (RuntimeException ex) {
            viewModel.setErrorMessage(ProductionUiErrorMapper.text(ex));
            return;
        }
        if (requirement.isEmpty()) {
            MaterialRequestDialogSupport.showValidation("Черновик больше недоступен.");
            return;
        }
        openMaterialRequirementDraft(requirement.get(), viewModel.canRequestMaterialsProperty().get());
    }

    private void openMaterialRequirementDraft(MaterialRequirementView requirement, boolean canMutate) {
        java.util.concurrent.atomic.AtomicReference<MaterialRequirementView> current =
                new java.util.concurrent.atomic.AtomicReference<>(requirement);
        List<String> sources = viewModel.materialRequirementSourceSummary(requirement);
        MaterialRequestDialogSupport.Step2Action action =
                MaterialRequestDialogSupport.showStep2(
                        requirement,
                        sources,
                        canMutate,
                        row -> {
                            try {
                                MaterialRequirementView latest = current.get();
                                MaterialRequirementView saved =
                                        viewModel.changeMaterialRequirementLineQuantity(
                                                latest.requirementId(),
                                                row.lineId(),
                                                row.quantity(),
                                                latest.version());
                                current.set(saved);
                                return Optional.of(saved);
                            } catch (RuntimeException ex) {
                                if (ProductionUiErrorMapper.isMaterialDraftConflict(ex)) {
                                    MaterialRequestDialogSupport.showValidation(
                                            ProductionUiErrorMapper.text(ex));
                                    Optional<MaterialRequirementView> reloaded =
                                            viewModel.getMaterialRequirement(
                                                    current.get().requirementId());
                                    reloaded.ifPresent(current::set);
                                    return reloaded;
                                }
                                MaterialRequestDialogSupport.showValidation(
                                        ProductionUiErrorMapper.text(ex));
                                return Optional.empty();
                            }
                        },
                        draft -> {
                            try {
                                SubmitMaterialRequirementResultView result =
                                        viewModel.submitMaterialRequirement(
                                                draft.requirementId(), draft.version());
                                MaterialRequirementView submitted =
                                        viewModel
                                                .getMaterialRequirement(result.requirementId())
                                                .orElse(draft);
                                viewModel.afterSuccessfulMaterialSubmit(submitted);
                                MaterialRequestDialogSupport.showInfo(
                                        MaterialRequestDialogSupport.STEP2_TITLE,
                                        ProductionUiErrorMapper.MATERIAL_SUBMIT_SUCCESS_HINT);
                                return Optional.empty();
                            } catch (RuntimeException ex) {
                                return Optional.of(ProductionUiErrorMapper.text(ex));
                            }
                        });
        if (action == MaterialRequestDialogSupport.Step2Action.SUBMITTED) {
            viewModel.refresh();
        }
    }

    private void showMaterialReadinessDetails() {
        var readiness = viewModel.currentMaterialReadiness();
        if (readiness == null || readiness.lines().isEmpty()) {
            return;
        }
        TableView<MaterialReadinessLineRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns()
                .addAll(
                        stringColumn("Артикул", MaterialReadinessLineRow::materialCode),
                        stringColumn("Наименование", MaterialReadinessLineRow::materialName),
                        stringColumn("Цвет", MaterialReadinessLineRow::color),
                        stringColumn("Требуется", MaterialReadinessLineRow::requiredQuantity),
                        stringColumn("Доступно", MaterialReadinessLineRow::availableQuantity),
                        stringColumn("Не хватает", MaterialReadinessLineRow::shortageQuantity),
                        stringColumn("Ед.", MaterialReadinessLineRow::unitOfMeasure));
        List<MaterialReadinessLineRow> rows = new ArrayList<>();
        for (var line : readiness.lines()) {
            rows.add(MaterialReadinessLineRow.from(line));
        }
        table.setItems(FXCollections.observableArrayList(rows));
        table.setPrefHeight(Math.min(360, 48 + rows.size() * 28.0));

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Материалы");
        alert.setHeaderText("МАТЕРИАЛЫ ДЛЯ ВЫПУСКА ОСТАТКА");
        alert.getDialogPane().setContent(table);
        alert.getDialogPane().setPrefWidth(760);
        alert.showAndWait();
    }

    private static TableColumn<MaterialReadinessLineRow, String> stringColumn(
            String title, java.util.function.Function<MaterialReadinessLineRow, String> getter) {
        TableColumn<MaterialReadinessLineRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(
                cell -> new SimpleStringProperty(getter.apply(cell.getValue())));
        return column;
    }
}
