package com.tmp.ui.shell.screen.warehouse;

import com.tmp.ui.shell.navigation.ViewModelAware;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.ActionEditRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.CellDetailRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.HistoryOperationOption;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.HistoryRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.ReceiveAllocationEditRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.SummaryRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.TaskRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.WarehouseFilterOption;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.WorkspaceTab;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Modern warehouse workspace controller (Задачи / Остатки / История). */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX Controller retains ViewModel for FXML wiring")
public final class WarehouseWorkspaceController
        implements ViewModelAware<WarehouseWorkspaceViewModel> {

    @FXML
    private Label titleLabel;

    @FXML
    private ToggleButton tasksTabButton;

    @FXML
    private ToggleButton stockTabButton;

    @FXML
    private ToggleButton historyTabButton;

    @FXML
    private StackPane tabContentStack;

    @FXML
    private VBox tasksPane;

    @FXML
    private VBox stockPane;

    @FXML
    private VBox historyPane;

    @FXML
    private ComboBox<WarehouseFilterOption> warehouseCombo;

    @FXML
    private TextField searchField;

    @FXML
    private Button searchButton;

    @FXML
    private DatePicker historyFromDatePicker;

    @FXML
    private DatePicker historyToDatePicker;

    @FXML
    private TextField historySearchField;

    @FXML
    private Button historySearchButton;

    @FXML
    private ComboBox<HistoryOperationOption> historyOperationCombo;

    @FXML
    private Button historyRefreshButton;

    @FXML
    private Label loadingLabel;

    @FXML
    private Label stockLoadingLabel;

    @FXML
    private Label historyLoadingLabel;

    @FXML
    private TableView<TaskRow> tasksTable;

    @FXML
    private TableColumn<TaskRow, String> taskDocumentColumn;

    @FXML
    private TableColumn<TaskRow, String> taskKindColumn;

    @FXML
    private TableColumn<TaskRow, String> taskStateColumn;

    @FXML
    private TableColumn<TaskRow, String> taskRouteColumn;

    @FXML
    private TableColumn<TaskRow, String> taskLinesColumn;

    @FXML
    private TableColumn<TaskRow, String> taskWorkerColumn;

    @FXML
    private Button takeTaskInWorkButton;

    @FXML
    private Button sendTransferButton;

    @FXML
    private Button receiveTransferButton;

    @FXML
    private Button rejectTransferButton;

    @FXML
    private Button returnTransferButton;

    @FXML
    private Button addReceiveAllocationButton;

    @FXML
    private Label transferActionsHintLabel;

    @FXML
    private Label taskDetailsLabel;

    @FXML
    private TableView<ActionEditRow> actionLinesTable;

    @FXML
    private TableColumn<ActionEditRow, String> actionMaterialColumn;

    @FXML
    private TableColumn<ActionEditRow, String> actionReferenceQtyColumn;

    @FXML
    private TableColumn<ActionEditRow, StorageCellChoice> actionCellColumn;

    @FXML
    private TableColumn<ActionEditRow, String> actionQuantityColumn;

    @FXML
    private TableView<WarehouseWorkspaceViewModel.StockTableRow> stockTable;

    @FXML
    private TableColumn<WarehouseWorkspaceViewModel.StockTableRow, String> expandColumn;

    @FXML
    private TableColumn<WarehouseWorkspaceViewModel.StockTableRow, String> warehouseColumn;

    @FXML
    private TableColumn<WarehouseWorkspaceViewModel.StockTableRow, String> articleColumn;

    @FXML
    private TableColumn<WarehouseWorkspaceViewModel.StockTableRow, String> nameColumn;

    @FXML
    private TableColumn<WarehouseWorkspaceViewModel.StockTableRow, String> colorColumn;

    @FXML
    private TableColumn<WarehouseWorkspaceViewModel.StockTableRow, String> sizeColumn;

    @FXML
    private TableColumn<WarehouseWorkspaceViewModel.StockTableRow, String> quantityColumn;

    @FXML
    private TableColumn<WarehouseWorkspaceViewModel.StockTableRow, String> unitColumn;

    @FXML
    private Button previousPageButton;

    @FXML
    private Button nextPageButton;

    @FXML
    private Label pageLabel;

    @FXML
    private TableView<HistoryRow> historyTable;

    @FXML
    private TableColumn<HistoryRow, String> historyOccurredAtColumn;

    @FXML
    private TableColumn<HistoryRow, String> historyOperationColumn;

    @FXML
    private TableColumn<HistoryRow, String> historyMaterialColumn;

    @FXML
    private TableColumn<HistoryRow, String> historyQuantityColumn;

    @FXML
    private TableColumn<HistoryRow, String> historySourceColumn;

    @FXML
    private TableColumn<HistoryRow, String> historyDestinationColumn;

    @FXML
    private TableColumn<HistoryRow, String> historyDocumentColumn;

    @FXML
    private TableColumn<HistoryRow, String> historyActorColumn;

    @FXML
    private Button historyPreviousPageButton;

    @FXML
    private Button historyNextPageButton;

    @FXML
    private Label historyPageLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label errorLabel;

    private WarehouseWorkspaceViewModel viewModel;
    private boolean binding;

    @Override
    public void setViewModel(WarehouseWorkspaceViewModel viewModel) {
        this.viewModel = viewModel;
        binding = true;
        titleLabel.textProperty().bind(viewModel.titleProperty());
        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(viewModel.errorMessageProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        loadingLabel.visibleProperty().bind(
                Bindings.and(
                        viewModel.loadingProperty(),
                        Bindings.equal(viewModel.selectedTabProperty(), WorkspaceTab.TASKS)));
        loadingLabel.managedProperty().bind(loadingLabel.visibleProperty());
        stockLoadingLabel.visibleProperty().bind(
                Bindings.and(
                        viewModel.loadingProperty(),
                        Bindings.equal(viewModel.selectedTabProperty(), WorkspaceTab.STOCK)));
        stockLoadingLabel.managedProperty().bind(stockLoadingLabel.visibleProperty());
        historyLoadingLabel.visibleProperty().bind(
                Bindings.and(
                        viewModel.loadingProperty(),
                        Bindings.equal(viewModel.selectedTabProperty(), WorkspaceTab.HISTORY)));
        historyLoadingLabel.managedProperty().bind(historyLoadingLabel.visibleProperty());

        ToggleGroup tabGroup = new ToggleGroup();
        tasksTabButton.setToggleGroup(tabGroup);
        stockTabButton.setToggleGroup(tabGroup);
        historyTabButton.setToggleGroup(tabGroup);
        tasksTabButton.setSelected(true);

        tasksTabButton.setOnAction(e -> selectTab(WorkspaceTab.TASKS));
        stockTabButton.setOnAction(e -> selectTab(WorkspaceTab.STOCK));
        historyTabButton.setOnAction(e -> selectTab(WorkspaceTab.HISTORY));

        viewModel.selectedTabProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            if (!binding) {
                binding = true;
                try {
                    switch (newValue) {
                        case TASKS -> tasksTabButton.setSelected(true);
                        case STOCK -> stockTabButton.setSelected(true);
                        case HISTORY -> historyTabButton.setSelected(true);
                    }
                } finally {
                    binding = false;
                }
            }
            updateTabVisibility(newValue);
        });
        updateTabVisibility(viewModel.selectedTabProperty().get());

        warehouseCombo.setItems(viewModel.warehouseFilterOptions());
        warehouseCombo.valueProperty()
                .addListener(
                        (obs, oldValue, newValue) -> {
                            if (binding || newValue == null || java.util.Objects.equals(oldValue, newValue)) {
                                return;
                            }
                            viewModel.selectWarehouseFilter(newValue);
                        });
        viewModel.selectedWarehouseFilterProperty()
                .addListener(
                        (obs, oldValue, newValue) -> {
                            if (binding) {
                                return;
                            }
                            binding = true;
                            try {
                                warehouseCombo.setValue(newValue);
                            } finally {
                                binding = false;
                            }
                        });
        warehouseCombo.setValue(viewModel.selectedWarehouseFilterProperty().get());

        searchField.textProperty().bindBidirectional(viewModel.searchInputProperty());
        searchField.setOnAction(e -> viewModel.commitSearch());
        searchButton.setOnAction(e -> viewModel.commitSearch());

        configureHistoryFilters();
        configureTasksTable();
        configureActionLinesTable();
        configureStockTable();
        configureHistoryTable();

        takeTaskInWorkButton.setOnAction(e -> viewModel.takeSelectedTaskInWork());
        takeTaskInWorkButton.disableProperty().bind(viewModel.canTakeSelectedTaskInWorkProperty().not());
        sendTransferButton.setOnAction(e -> viewModel.sendSelectedTask());
        sendTransferButton.disableProperty().bind(viewModel.canSendSelectedTaskProperty().not());
        receiveTransferButton.setOnAction(e -> viewModel.receiveSelectedTask());
        receiveTransferButton.disableProperty().bind(viewModel.canReceiveSelectedTaskProperty().not());
        rejectTransferButton.setOnAction(e -> promptRejectReason());
        rejectTransferButton.disableProperty().bind(viewModel.canRejectSelectedTaskProperty().not());
        returnTransferButton.setOnAction(e -> viewModel.returnSelectedTask());
        returnTransferButton.disableProperty().bind(viewModel.canReturnSelectedTaskProperty().not());
        addReceiveAllocationButton.setOnAction(e -> addReceiveAllocationForSelectedLine());
        addReceiveAllocationButton
                .disableProperty()
                .bind(viewModel.canRejectSelectedTaskProperty().not());
        transferActionsHintLabel.textProperty().bind(viewModel.transferActionsHintProperty());
        taskDetailsLabel.textProperty().bind(viewModel.taskDetailsTextProperty());

        previousPageButton.setOnAction(e -> viewModel.previousPage());
        nextPageButton.setOnAction(e -> viewModel.nextPage());
        previousPageButton.disableProperty().bind(viewModel.canGoPreviousProperty().not());
        nextPageButton.disableProperty().bind(viewModel.canGoNextProperty().not());
        pageLabel.textProperty()
                .bind(
                        Bindings.createStringBinding(
                                () ->
                                        "Страница "
                                                + (viewModel.pageIndexProperty().get() + 1),
                                viewModel.pageIndexProperty()));

        historyPreviousPageButton.setOnAction(e -> viewModel.previousHistoryPage());
        historyNextPageButton.setOnAction(e -> viewModel.nextHistoryPage());
        historyPreviousPageButton.disableProperty().bind(viewModel.historyCanGoPreviousProperty().not());
        historyNextPageButton.disableProperty().bind(viewModel.historyCanGoNextProperty().not());
        historyPageLabel.textProperty()
                .bind(
                        Bindings.createStringBinding(
                                () ->
                                        "Страница "
                                                + (viewModel.historyPageIndexProperty().get() + 1),
                                viewModel.historyPageIndexProperty()));

        binding = false;
        viewModel.onScreenOpened();
    }

    private void configureHistoryFilters() {
        historyFromDatePicker.setValue(viewModel.historyFromDateProperty().get());
        historyToDatePicker.setValue(viewModel.historyToDateProperty().get());
        historyFromDatePicker
                .valueProperty()
                .addListener(
                        (obs, oldValue, newValue) -> {
                            if (binding) {
                                return;
                            }
                            viewModel.setHistoryFromDate(newValue);
                        });
        historyToDatePicker
                .valueProperty()
                .addListener(
                        (obs, oldValue, newValue) -> {
                            if (binding) {
                                return;
                            }
                            viewModel.setHistoryToDate(newValue);
                        });
        viewModel.historyFromDateProperty()
                .addListener(
                        (obs, oldValue, newValue) -> syncHistoryDatePicker(historyFromDatePicker, newValue));
        viewModel.historyToDateProperty()
                .addListener(
                        (obs, oldValue, newValue) -> syncHistoryDatePicker(historyToDatePicker, newValue));

        historySearchField.textProperty().bindBidirectional(viewModel.historySearchInputProperty());
        historySearchField.setOnAction(e -> viewModel.commitHistorySearch());
        historySearchButton.setOnAction(e -> viewModel.commitHistorySearch());
        historyRefreshButton.setOnAction(e -> viewModel.refreshHistory());

        historyOperationCombo.setItems(viewModel.historyOperationOptions());
        historyOperationCombo.setValue(viewModel.selectedHistoryOperationProperty().get());
        historyOperationCombo
                .valueProperty()
                .addListener(
                        (obs, oldValue, newValue) -> {
                            if (binding || newValue == null || java.util.Objects.equals(oldValue, newValue)) {
                                return;
                            }
                            viewModel.selectHistoryOperation(newValue);
                        });
        viewModel.selectedHistoryOperationProperty()
                .addListener(
                        (obs, oldValue, newValue) -> {
                            if (binding) {
                                return;
                            }
                            binding = true;
                            try {
                                historyOperationCombo.setValue(newValue);
                            } finally {
                                binding = false;
                            }
                        });
    }

    private void syncHistoryDatePicker(DatePicker picker, LocalDate value) {
        if (binding || java.util.Objects.equals(picker.getValue(), value)) {
            return;
        }
        binding = true;
        try {
            picker.setValue(value);
        } finally {
            binding = false;
        }
    }

    private void selectTab(WorkspaceTab tab) {
        if (binding) {
            return;
        }
        viewModel.selectTab(tab);
    }

    private void updateTabVisibility(WorkspaceTab tab) {
        tasksPane.setVisible(tab == WorkspaceTab.TASKS);
        tasksPane.setManaged(tab == WorkspaceTab.TASKS);
        stockPane.setVisible(tab == WorkspaceTab.STOCK);
        stockPane.setManaged(tab == WorkspaceTab.STOCK);
        historyPane.setVisible(tab == WorkspaceTab.HISTORY);
        historyPane.setManaged(tab == WorkspaceTab.HISTORY);
    }

    private void configureTasksTable() {
        taskDocumentColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().documentNumber()));
        taskKindColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().kindLabel()));
        taskStateColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().stateLabel()));
        taskRouteColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().routeLabel()));
        taskLinesColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().lineCountText()));
        taskWorkerColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().workerDisplay()));

        tasksTable.setItems(viewModel.taskRows());
        tasksTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Label placeholder = new Label();
        placeholder.textProperty().bind(viewModel.statusMessageProperty());
        placeholder.getStyleClass().add("tmp-empty-state-hint");
        placeholder.setWrapText(true);
        tasksTable.setPlaceholder(placeholder);

        tasksTable.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (obs, oldValue, newValue) -> {
                            if (binding) {
                                return;
                            }
                            viewModel.selectTask(newValue);
                        });
        viewModel.selectedTaskProperty()
                .addListener(
                        (obs, oldValue, newValue) -> {
                            if (binding || java.util.Objects.equals(tasksTable.getSelectionModel().getSelectedItem(), newValue)) {
                                return;
                            }
                            binding = true;
                            try {
                                tasksTable.getSelectionModel().select(newValue);
                            } finally {
                                binding = false;
                            }
                        });
    }

    private void configureActionLinesTable() {
        actionMaterialColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().materialLabel()));
        actionReferenceQtyColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().referenceQuantityText()));
        actionCellColumn.setCellValueFactory(cell -> cell.getValue().storageCellProperty());
        actionCellColumn.setCellFactory(column -> new ActionCellComboCell());
        actionQuantityColumn.setCellValueFactory(cell -> cell.getValue().quantityTextProperty());
        actionQuantityColumn.setCellFactory(column -> new ActionQuantityCell());

        actionLinesTable.setItems(viewModel.actionLines());
        actionLinesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        actionLinesTable.setPlaceholder(new Label("Выберите задачу для редактирования строк"));
    }

    private void promptRejectReason() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Отклонение перемещения");
        dialog.setHeaderText("Укажите причину отклонения");
        ButtonType rejectType = new ButtonType("Отклонить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(rejectType, ButtonType.CANCEL);
        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Причина");
        reasonArea.setWrapText(true);
        reasonArea.setPrefRowCount(4);
        dialog.getDialogPane().setContent(reasonArea);
        Button rejectButton = (Button) dialog.getDialogPane().lookupButton(rejectType);
        rejectButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> reasonArea.getText() == null || reasonArea.getText().isBlank(),
                reasonArea.textProperty()));
        dialog.setResultConverter(
                button -> button == rejectType ? reasonArea.getText() : null);
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(viewModel::rejectSelectedTask);
    }

    private void addReceiveAllocationForSelectedLine() {
        ActionEditRow selected = actionLinesTable.getSelectionModel().getSelectedItem();
        UUID lineId =
                selected instanceof ReceiveAllocationEditRow receive
                        ? receive.lineId()
                        : viewModel.actionLines().stream()
                                .filter(ReceiveAllocationEditRow.class::isInstance)
                                .map(ActionEditRow::lineId)
                                .findFirst()
                                .orElse(null);
        if (lineId != null) {
            viewModel.addReceiveAllocationForLine(lineId);
        }
    }

    private void configureStockTable() {
        expandColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(""));
        expandColumn.setCellFactory(column -> new ExpandCell());

        warehouseColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(warehouseText(cell.getValue())));
        warehouseColumn.visibleProperty().bind(viewModel.showWarehouseColumnProperty());

        articleColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(articleText(cell.getValue())));
        nameColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(nameText(cell.getValue())));
        colorColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(colorText(cell.getValue())));
        sizeColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(sizeText(cell.getValue())));
        quantityColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                quantityText(cell.getValue())));
        unitColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(unitText(cell.getValue())));

        stockTable.setItems(viewModel.tableRows());
        stockTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Label placeholder = new Label();
        placeholder.textProperty().bind(viewModel.statusMessageProperty());
        placeholder.getStyleClass().add("tmp-empty-state-hint");
        placeholder.setWrapText(true);
        stockTable.setPlaceholder(placeholder);

        stockTable.setRowFactory(
                table -> {
                    TableRow<WarehouseWorkspaceViewModel.StockTableRow> row = new TableRow<>();
                    row.setOnMouseClicked(
                            event -> {
                                if (event.getButton() == MouseButton.PRIMARY
                                        && event.getClickCount() == 2
                                        && !row.isEmpty()
                                        && row.getItem() instanceof SummaryRow summary) {
                                    viewModel.toggleExpand(summary);
                                }
                            });
                    return row;
                });
    }

    private void configureHistoryTable() {
        historyOccurredAtColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().occurredAtText()));
        historyOperationColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().operationLabel()));
        historyMaterialColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().materialText()));
        historyQuantityColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().quantityText()));
        historySourceColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().sourceText()));
        historyDestinationColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().destinationText()));
        historyDocumentColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().documentText()));
        historyActorColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().actorText()));

        historyTable.setItems(viewModel.historyRows());
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Label placeholder = new Label();
        placeholder.textProperty().bind(viewModel.statusMessageProperty());
        placeholder.getStyleClass().add("tmp-empty-state-hint");
        placeholder.setWrapText(true);
        historyTable.setPlaceholder(placeholder);
    }

    private static String warehouseText(WarehouseWorkspaceViewModel.StockTableRow row) {
        if (row instanceof SummaryRow summary) {
            return summary.warehouseLabel();
        }
        return "";
    }

    private static String articleText(WarehouseWorkspaceViewModel.StockTableRow row) {
        if (row instanceof SummaryRow summary) {
            return summary.article();
        }
        if (row instanceof CellDetailRow detail) {
            return detail.indentedArticle();
        }
        return "";
    }

    private static String nameText(WarehouseWorkspaceViewModel.StockTableRow row) {
        if (row instanceof SummaryRow summary) {
            return summary.name();
        }
        return "";
    }

    private static String colorText(WarehouseWorkspaceViewModel.StockTableRow row) {
        return row instanceof SummaryRow summary ? summary.color() : "";
    }

    private static String sizeText(WarehouseWorkspaceViewModel.StockTableRow row) {
        return row instanceof SummaryRow summary ? summary.size() : "";
    }

    private static String quantityText(WarehouseWorkspaceViewModel.StockTableRow row) {
        if (row instanceof SummaryRow summary) {
            return summary.quantityText();
        }
        if (row instanceof CellDetailRow detail) {
            return detail.quantityText();
        }
        return "";
    }

    private static String unitText(WarehouseWorkspaceViewModel.StockTableRow row) {
        return row instanceof SummaryRow summary ? summary.unitOfMeasure() : "";
    }

    private final class ActionCellComboCell extends TableCell<ActionEditRow, StorageCellChoice> {

        private final ComboBox<StorageCellChoice> combo = new ComboBox<>();

        ActionCellComboCell() {
            combo.setItems(viewModel.actionCellChoices());
            combo.valueProperty()
                    .addListener(
                            (obs, oldValue, newValue) -> {
                                ActionEditRow row = getTableRow() == null ? null : getTableRow().getItem();
                                if (row != null && !isEmpty()) {
                                    row.storageCellProperty().set(newValue);
                                }
                            });
        }

        @Override
        protected void updateItem(StorageCellChoice item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
                return;
            }
            combo.setValue(item);
            setGraphic(combo);
        }
    }

    private final class ActionQuantityCell extends TableCell<ActionEditRow, String> {

        private final TextField field = new TextField();

        ActionQuantityCell() {
            field.textProperty()
                    .addListener(
                            (obs, oldValue, newValue) -> {
                                ActionEditRow row = getTableRow() == null ? null : getTableRow().getItem();
                                if (row != null && row.quantityEditable() && !isEmpty()) {
                                    row.quantityTextProperty().set(newValue);
                                }
                            });
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            ActionEditRow row = getTableRow().getItem();
            if (row.quantityEditable()) {
                field.setText(item == null ? "" : item);
                setGraphic(field);
                setText(null);
            } else {
                setGraphic(null);
                setText(item);
            }
        }
    }

    private final class ExpandCell extends TableCell<WarehouseWorkspaceViewModel.StockTableRow, String> {

        private final Button button = new Button("▶");

        ExpandCell() {
            button.getStyleClass().add("tmp-button-secondary");
            button.setOnAction(
                    e -> {
                        WarehouseWorkspaceViewModel.StockTableRow row = getTableRow().getItem();
                        if (row instanceof SummaryRow summary) {
                            viewModel.toggleExpand(summary);
                            refreshButton(summary);
                        }
                    });
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || !(getTableRow().getItem() instanceof SummaryRow summary)) {
                setGraphic(null);
                return;
            }
            refreshButton(summary);
            setGraphic(button);
        }

        private void refreshButton(SummaryRow summary) {
            if (summary.expandingProperty().get()) {
                button.setText("…");
                button.setDisable(true);
            } else if (summary.expandedProperty().get()) {
                button.setText("▼");
                button.setDisable(false);
            } else {
                button.setText("▶");
                button.setDisable(false);
            }
            summary.expandedProperty()
                    .addListener(
                            (obs, oldValue, newValue) -> {
                                if (getTableRow() != null && getTableRow().getItem() == summary) {
                                    refreshButton(summary);
                                }
                            });
            summary.expandingProperty()
                    .addListener(
                            (obs, oldValue, newValue) -> {
                                if (getTableRow() != null && getTableRow().getItem() == summary) {
                                    refreshButton(summary);
                                }
                            });
        }
    }
}
