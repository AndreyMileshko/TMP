package com.tmp.ui.shell.screen.warehouse;

import com.tmp.ui.shell.navigation.ViewModelAware;
import com.tmp.ui.shell.order.DecimalQuantityParser;
import com.tmp.ui.shell.order.DecimalUiFormat;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.CellFilterOption;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.HistoryOperationOption;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.HistoryRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.StockMoveLine;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.StockRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.TaskRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.WarehouseFilterOption;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.WorkspaceTab;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
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
    private Button receiptButton;

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
    private ComboBox<CellFilterOption> cellCombo;

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
    private TableColumn<TaskRow, String> taskCreatedAtColumn;

    @FXML
    private TableColumn<TaskRow, String> taskDocumentColumn;

    @FXML
    private TableColumn<TaskRow, String> taskOrderColumn;

    @FXML
    private TableColumn<TaskRow, String> taskKindColumn;

    @FXML
    private TableColumn<TaskRow, String> taskStateColumn;

    @FXML
    private TableColumn<TaskRow, String> taskSourceColumn;

    @FXML
    private TableColumn<TaskRow, String> taskDestinationColumn;

    @FXML
    private TableColumn<TaskRow, String> taskLinesColumn;

    @FXML
    private TableColumn<TaskRow, String> taskWorkerColumn;

    @FXML
    private TableView<StockRow> stockTable;

    @FXML
    private TableColumn<StockRow, Boolean> stockSelectColumn;

    @FXML
    private TableColumn<StockRow, String> warehouseColumn;

    @FXML
    private Button moveStockButton;

    @FXML
    private Button consumeStockButton;

    @FXML
    private Button adjustStockButton;

    @FXML
    private TableColumn<StockRow, String> cellColumn;

    @FXML
    private TableColumn<StockRow, String> articleColumn;

    @FXML
    private TableColumn<StockRow, String> nameColumn;

    @FXML
    private TableColumn<StockRow, String> colorColumn;

    @FXML
    private TableColumn<StockRow, String> sizeColumn;

    @FXML
    private TableColumn<StockRow, String> quantityColumn;

    @FXML
    private TableColumn<StockRow, String> unitColumn;

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
    private TableColumn<HistoryRow, String> historyArticleColumn;

    @FXML
    private TableColumn<HistoryRow, String> historyNameColumn;

    @FXML
    private TableColumn<HistoryRow, String> historyColorColumn;

    @FXML
    private TableColumn<HistoryRow, String> historySizeColumn;

    @FXML
    private TableColumn<HistoryRow, String> historyQuantityColumn;

    @FXML
    private TableColumn<HistoryRow, String> historyUnitColumn;

    @FXML
    private TableColumn<HistoryRow, String> historySourceColumn;

    @FXML
    private TableColumn<HistoryRow, String> historyDestinationColumn;

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
    private WarehouseTaskDialogSupport.TaskDialogSession openTaskDialogSession;

    @Override
    public void setViewModel(WarehouseWorkspaceViewModel viewModel) {
        this.viewModel = viewModel;
        binding = true;
        titleLabel.textProperty().bind(viewModel.titleProperty());
        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(viewModel.errorMessageProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        loadingLabel.setManaged(false);
        loadingLabel.visibleProperty().bind(
                Bindings.and(
                        viewModel.loadingProperty(),
                        Bindings.equal(viewModel.selectedTabProperty(), WorkspaceTab.TASKS)));
        // Stocks loading is an overlay: never managed=true (that resized the table and caused jitter).
        stockLoadingLabel.setManaged(false);
        stockLoadingLabel.visibleProperty().bind(
                Bindings.and(
                        viewModel.loadingProperty(),
                        Bindings.equal(viewModel.selectedTabProperty(), WorkspaceTab.STOCK)));
        historyLoadingLabel.setManaged(false);
        historyLoadingLabel.visibleProperty().bind(
                Bindings.and(
                        viewModel.loadingProperty(),
                        Bindings.equal(viewModel.selectedTabProperty(), WorkspaceTab.HISTORY)));

        IntSupplier scrollAnchor =
                () -> {
                    int top = estimateTopVisibleStockIndex();
                    traceStockLayout("pre-reload");
                    return top;
                };
        IntConsumer scrollRestorer =
                index ->
                        Platform.runLater(
                                () -> {
                                    if (index >= 0 && !stockTable.getItems().isEmpty()) {
                                        int restore =
                                                Math.min(index, stockTable.getItems().size() - 1);
                                        stockTable.scrollTo(restore);
                                    }
                                    traceStockLayout("post-reload");
                                });
        viewModel.setStockScrollHooks(scrollAnchor, scrollRestorer);

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
        warehouseCombo.setConverter(
                new javafx.util.StringConverter<>() {
                    @Override
                    public String toString(WarehouseFilterOption option) {
                        return option == null ? "" : option.toString();
                    }

                    @Override
                    public WarehouseFilterOption fromString(String string) {
                        return null;
                    }
                });
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

        cellCombo.setItems(viewModel.cellFilterOptions());
        cellCombo.setConverter(
                new javafx.util.StringConverter<>() {
                    @Override
                    public String toString(CellFilterOption option) {
                        return option == null ? "" : option.toString();
                    }

                    @Override
                    public CellFilterOption fromString(String string) {
                        return null;
                    }
                });
        cellCombo.valueProperty()
                .addListener(
                        (obs, oldValue, newValue) -> {
                            if (binding || newValue == null || java.util.Objects.equals(oldValue, newValue)) {
                                return;
                            }
                            viewModel.selectCellFilter(newValue);
                        });
        viewModel.selectedCellFilterProperty()
                .addListener(
                        (obs, oldValue, newValue) -> {
                            if (binding) {
                                return;
                            }
                            binding = true;
                            try {
                                cellCombo.setValue(newValue);
                            } finally {
                                binding = false;
                            }
                        });
        cellCombo.setValue(viewModel.selectedCellFilterProperty().get());

        searchField.textProperty().bindBidirectional(viewModel.searchInputProperty());
        searchField.setOnAction(e -> viewModel.commitSearch());
        searchButton.setOnAction(e -> viewModel.commitSearch());

        configureHistoryFilters();
        configureTasksTable();
        configureStockTable();
        configureHistoryTable();

        moveStockButton.setOnAction(e -> openMoveDialog());
        moveStockButton.disableProperty().bind(viewModel.canMoveSelectedStockProperty().not());
        receiptButton.setOnAction(e -> openReceiptDialog());
        receiptButton.disableProperty().bind(viewModel.canCreateReceiptProperty().not());
        consumeStockButton.setOnAction(e -> openConsumeDialog());
        consumeStockButton.disableProperty().bind(viewModel.canConsumeSelectedStockProperty().not());
        adjustStockButton.setOnAction(e -> openAdjustDialog());
        adjustStockButton.disableProperty().bind(viewModel.canAdjustSelectedStockProperty().not());

        viewModel.setAfterTerminalTaskAction(this::closeOpenTaskDialog);
        viewModel.setAfterTaskDetailsLoaded(this::refreshOpenTaskDialogHeader);

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
        taskCreatedAtColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().createdAtText()));
        taskOrderColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().orderNumberText()));
        taskKindColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().kindLabel()));
        taskStateColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().stateLabel()));
        taskSourceColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().sourceWarehouseLabel()));
        taskDestinationColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().destinationWarehouseLabel()));
        taskLinesColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().lineCountText()));
        taskWorkerColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().workerDisplay()));
        taskDocumentColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().documentNumber()));
        taskDocumentColumn.setCellFactory(
                column ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || item == null) {
                                    setText(null);
                                    setTooltip(null);
                                } else {
                                    setText(item);
                                    setTooltip(new Tooltip(item));
                                }
                            }
                        });

        tasksTable.setItems(viewModel.taskRows());
        // UNCONSTRAINED: CONSTRAINED redistributes columns when the vertical scrollbar toggles.
        tasksTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        Label placeholder = new Label("Нет задач, требующих вашего действия");
        placeholder.textProperty().bind(viewModel.statusMessageProperty());
        placeholder.getStyleClass().add("tmp-empty-state-hint");
        placeholder.setWrapText(true);
        tasksTable.setPlaceholder(placeholder);

        tasksTable.setRowFactory(
                table -> {
                    TableRow<TaskRow> row = new TableRow<>();
                    row.setOnMouseClicked(
                            event -> {
                                if (event.getButton() != MouseButton.PRIMARY
                                        || event.getClickCount() != 2
                                        || row.isEmpty()
                                        || row.getItem() == null) {
                                    return;
                                }
                                openTaskDialog(row.getItem());
                            });
                    return row;
                });

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
                            if (binding
                                    || java.util.Objects.equals(
                                            tasksTable.getSelectionModel().getSelectedItem(),
                                            newValue)) {
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

    private void openTaskDialog(TaskRow task) {
        if (task == null || openTaskDialogSession != null) {
            return;
        }
        viewModel.selectTask(task);
        viewModel.openTaskDialogDetails(task);
        WarehouseTaskDialogSupport.TaskDialogSession session =
                WarehouseTaskDialogSupport.createTaskDialog(
                        task,
                        viewModel.actionLines(),
                        viewModel.actionCellChoices(),
                        viewModel.canTakeSelectedTaskInWorkProperty(),
                        viewModel.canSendSelectedTaskProperty(),
                        viewModel.canReceiveSelectedTaskProperty(),
                        viewModel.canRejectSelectedTaskProperty(),
                        viewModel.canReturnSelectedTaskProperty(),
                        viewModel.taskDetailLoadingProperty(),
                        viewModel::takeSelectedTaskInWork,
                        viewModel::sendSelectedTask,
                        viewModel::receiveSelectedTask,
                        viewModel::rejectSelectedTask,
                        viewModel::returnSelectedTask);
        openTaskDialogSession = session;
        viewModel.errorMessageProperty()
                .addListener(
                        (obs, oldValue, newValue) -> {
                            if (openTaskDialogSession == session && newValue != null && !newValue.isBlank()) {
                                session.showError(newValue);
                            }
                        });
        session.dialog()
                .setOnHidden(
                        e -> {
                            if (openTaskDialogSession == session) {
                                openTaskDialogSession = null;
                                viewModel.setTaskDialogOpen(false);
                            }
                        });
        session.dialog().showAndWait();
    }

    private void closeOpenTaskDialog() {
        WarehouseTaskDialogSupport.TaskDialogSession session = openTaskDialogSession;
        if (session != null) {
            openTaskDialogSession = null;
            viewModel.setTaskDialogOpen(false);
            session.close();
        }
    }

    private void refreshOpenTaskDialogHeader() {
        WarehouseTaskDialogSupport.TaskDialogSession session = openTaskDialogSession;
        TaskRow task = viewModel.selectedTaskProperty().get();
        if (session != null && task != null) {
            WarehouseTaskDialogSupport.refreshHeader(session, task);
            session.clearError();
        }
    }

    private void configureStockTable() {
        stockSelectColumn.setCellValueFactory(cell -> cell.getValue().selectedProperty());
        stockSelectColumn.setCellFactory(
                column ->
                        new TableCell<>() {
                            private final CheckBox checkBox = new CheckBox();

                            {
                                checkBox.setOnAction(
                                        e -> {
                                            StockRow row = getTableRow() == null ? null : getTableRow().getItem();
                                            if (row != null) {
                                                viewModel.toggleStockRowSelection(
                                                        row, checkBox.isSelected());
                                            }
                                        });
                                setAlignment(Pos.CENTER);
                            }

                            @Override
                            protected void updateItem(Boolean item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                                    setGraphic(null);
                                    return;
                                }
                                checkBox.setSelected(Boolean.TRUE.equals(item));
                                setGraphic(checkBox);
                            }
                        });
        stockSelectColumn.setSortable(false);
        stockSelectColumn.setReorderable(false);

        warehouseColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().warehouseLabel()));
        warehouseColumn.visibleProperty().bind(viewModel.showWarehouseColumnProperty());

        cellColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().cellCode()));
        articleColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().article()));
        nameColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().name()));
        colorColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().color()));
        sizeColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().size()));
        quantityColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().quantityText()));
        quantityColumn.setCellFactory(column -> rightAlignedTextCell());
        unitColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().unitOfMeasure()));
        unitColumn.setCellFactory(column -> centerAlignedTextCell());

        stockTable.setItems(viewModel.tableRows());
        // UNCONSTRAINED + reserved vertical gutter: CONSTRAINED redistributed every column when the
        // vertical scrollbar appeared/disappeared (viewport width change → visible table jerk).
        stockTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        configureStockColumnWidths();
        installStockRefreshLayoutTrace();
        Label placeholder = new Label("Нет остатков");
        placeholder.textProperty().bind(viewModel.statusMessageProperty());
        placeholder.getStyleClass().add("tmp-empty-state-hint");
        placeholder.setWrapText(true);
        stockTable.setPlaceholder(placeholder);

        MenuItem moveItem = new MenuItem("Переместить");
        moveItem.setOnAction(e -> openMoveDialog());
        MenuItem consumeItem = new MenuItem("Списать");
        consumeItem.setOnAction(e -> openConsumeDialog());
        MenuItem adjustItem = new MenuItem("Корректировать");
        adjustItem.setOnAction(e -> openAdjustDialog());
        ContextMenu menu = new ContextMenu(moveItem, consumeItem, adjustItem);
        stockTable.setRowFactory(
                table -> {
                    TableRow<StockRow> row = new TableRow<>();
                    row.setOnContextMenuRequested(
                            event -> {
                                if (row.isEmpty() || row.getItem() == null) {
                                    return;
                                }
                                StockRow item = row.getItem();
                                if (!item.isSelected()) {
                                    viewModel.selectSingleStockRow(item);
                                }
                                moveItem.setDisable(!viewModel.canMoveSelectedStockProperty().get());
                                consumeItem.setDisable(
                                        !viewModel.canConsumeSelectedStockProperty().get());
                                adjustItem.setDisable(
                                        !viewModel.canAdjustSelectedStockProperty().get());
                                menu.show(row, event.getScreenX(), event.getScreenY());
                                event.consume();
                            });
                    row.setOnMouseClicked(
                            event -> {
                                if (event.getButton() == MouseButton.PRIMARY
                                        && event.getClickCount() == 1
                                        && !row.isEmpty()
                                        && row.getItem() != null
                                        && event.isControlDown()) {
                                    viewModel.toggleStockRowSelection(
                                            row.getItem(), !row.getItem().isSelected());
                                }
                            });
                    return row;
                });
    }

    /**
     * Flex only the name column; always subtract a vertical-scrollbar gutter so show/hide of the
     * bar does not change column geometry under adaptive window sizes.
     */
    private void configureStockColumnWidths() {
        final double verticalScrollGutter = 18.0;
        stockSelectColumn.setResizable(false);
        Runnable redistribute =
                () -> {
                    double tableWidth = stockTable.getWidth();
                    if (tableWidth <= 0) {
                        return;
                    }
                    double fixed =
                            stockSelectColumn.getPrefWidth()
                                    + (warehouseColumn.isVisible()
                                            ? warehouseColumn.getPrefWidth()
                                            : 0)
                                    + cellColumn.getPrefWidth()
                                    + articleColumn.getPrefWidth()
                                    + colorColumn.getPrefWidth()
                                    + sizeColumn.getPrefWidth()
                                    + quantityColumn.getPrefWidth()
                                    + unitColumn.getPrefWidth();
                    if (fixed <= 0) {
                        fixed = 40 + 100 + 140 + 100 + 100 + 90 + 60;
                        if (warehouseColumn.isVisible()) {
                            fixed += 100;
                        }
                    }
                    double nameWidth = Math.max(160, tableWidth - fixed - verticalScrollGutter);
                    nameColumn.setPrefWidth(nameWidth);
                };
        stockTable.widthProperty().addListener((obs, o, n) -> redistribute.run());
        warehouseColumn.visibleProperty().addListener((obs, o, n) -> redistribute.run());
        Platform.runLater(redistribute);
    }

    private void installStockRefreshLayoutTrace() {
        viewModel
                .loadingProperty()
                .addListener(
                        (obs, wasLoading, isLoading) -> {
                            if (viewModel.selectedTabProperty().get() != WorkspaceTab.STOCK) {
                                return;
                            }
                            Platform.runLater(
                                    () ->
                                            traceStockLayout(
                                                    Boolean.TRUE.equals(isLoading)
                                                            ? "loading-on"
                                                            : "loading-off"));
                        });
    }

    private void traceStockLayout(String phase) {
        if (!StocksRefreshTrace.enabled()) {
            return;
        }
        ScrollBar vBar = findScrollBar(Orientation.VERTICAL);
        ScrollBar hBar = findScrollBar(Orientation.HORIZONTAL);
        StocksRefreshTrace.layoutSnapshot(
                phase,
                stockTable.getWidth(),
                stockTable.getHeight(),
                vBar != null && vBar.isVisible(),
                hBar != null && hBar.isVisible(),
                stockLoadingLabel.isVisible(),
                stockLoadingLabel.isManaged(),
                stockLoadingLabel.getHeight(),
                estimateTopVisibleStockIndex());
    }

    private ScrollBar findScrollBar(Orientation orientation) {
        for (Node node : stockTable.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar bar && bar.getOrientation() == orientation) {
                return bar;
            }
        }
        return null;
    }

    private int estimateTopVisibleStockIndex() {
        ScrollBar vBar = findScrollBar(Orientation.VERTICAL);
        if (vBar == null || stockTable.getItems().isEmpty()) {
            return stockTable.getItems().isEmpty() ? -1 : 0;
        }
        double max = vBar.getMax();
        if (max <= 0) {
            return 0;
        }
        return (int) Math.round((vBar.getValue() / max) * (stockTable.getItems().size() - 1));
    }

    private void openReceiptDialog() {
        WarehouseReceiptDialogSupport.ReceiptDialogSession session =
                WarehouseReceiptDialogSupport.createReceiptDialog(
                        viewModel.listResponsibleWarehouseChoices(),
                        viewModel.listUnitOfMeasures(),
                        viewModel::listDestinationCells);
        Optional<ButtonType> result = session.dialog().showAndWait();
        if (result.isEmpty() || result.get() != session.submitType()) {
            return;
        }
        // Validation already ran inside the dialog (submit filter keeps it open on error).
        viewModel.executeReceipt(session.requireSubmission());
    }

    private void openMoveDialog() {
        List<StockRow> selected;
        try {
            selected = viewModel.requireSameWarehouseSelectionForMove();
        } catch (IllegalArgumentException ex) {
            viewModel.errorMessageProperty().set(ex.getMessage());
            return;
        }
        UUID sourceWarehouseId = selected.get(0).warehouseId();
        WarehouseMoveDialogSupport.MoveDialogSession session =
                WarehouseMoveDialogSupport.createMoveDialog(
                        selected,
                        sourceWarehouseId,
                        warehouseLabel(sourceWarehouseId),
                        viewModel.listActiveWarehouseChoices(),
                        viewModel::listDestinationCells);
        Optional<ButtonType> result = session.dialog().showAndWait();
        if (result.isEmpty() || result.get() != session.submitType()) {
            return;
        }
        try {
            WarehouseMoveDialogSupport.MoveSubmission submission = session.requireSubmission();
            if (submission.sameWarehouse()) {
                viewModel.executeSameWarehouseMove(
                        submission.lines(), submission.destinationStorageCellId());
            } else {
                viewModel.executeInterWarehouseMove(
                        submission.lines(), submission.destinationWarehouseId());
            }
        } catch (IllegalArgumentException ex) {
            viewModel.errorMessageProperty().set(ex.getMessage());
        }
    }

    private void openConsumeDialog() {
        List<StockRow> selected = viewModel.selectedStockRows();
        if (selected.isEmpty()) {
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Списание");
        dialog.setHeaderText("Списание материалов");
        ButtonType okType = new ButtonType("Списать", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, cancelType);
        dialog.getDialogPane().getStyleClass().add("tmp-dialog");
        VBox linesBox = new VBox(6);
        List<TextField> quantityFields = new ArrayList<>();
        for (StockRow row : selected) {
            HBox line = new HBox(8);
            line.setAlignment(Pos.CENTER_LEFT);
            Label material = new Label(row.materialLabel() + " / " + row.cellCode());
            material.setPrefWidth(280);
            Label available =
                    new Label("Доступно: " + DecimalUiFormat.formatRu(row.availableQuantity()));
            TextField qty = new TextField(DecimalUiFormat.formatRu(row.availableQuantity()));
            qty.setPrefWidth(100);
            quantityFields.add(qty);
            line.getChildren().addAll(material, available, new Label("Кол-во:"), qty);
            linesBox.getChildren().add(line);
        }
        dialog.getDialogPane().setContent(linesBox);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != okType) {
            return;
        }
        try {
            List<StockMoveLine> lines = new ArrayList<>();
            for (int i = 0; i < selected.size(); i++) {
                BigDecimal qty =
                        DecimalQuantityParser.parsePositive(
                                quantityFields.get(i).getText(), "количество");
                if (qty.compareTo(selected.get(i).availableQuantity()) > 0) {
                    throw new IllegalArgumentException(
                            "количество не может превышать доступный остаток");
                }
                lines.add(StockMoveLine.from(selected.get(i), qty));
            }
            viewModel.executeStockConsumption(lines);
        } catch (IllegalArgumentException ex) {
            viewModel.errorMessageProperty().set(ex.getMessage());
        }
    }

    private void openAdjustDialog() {
        List<StockRow> selected = viewModel.selectedStockRows();
        if (selected.size() != 1) {
            viewModel.errorMessageProperty().set("Для корректировки выберите одну строку.");
            return;
        }
        StockRow row = selected.get(0);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Корректировка");
        dialog.setHeaderText("Корректировка остатка");
        dialog.setResizable(true);
        ButtonType okType = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, cancelType);
        dialog.getDialogPane().getStyleClass().add("tmp-dialog");
        dialog.getDialogPane().setPrefWidth(900);

        javafx.beans.property.StringProperty deltaText =
                new javafx.beans.property.SimpleStringProperty("");
        TableView<StockRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(90);
        table.getItems().setAll(row);

        TableColumn<StockRow, String> articleCol = new TableColumn<>("Артикул");
        articleCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(
                                WarehouseMoveDialogSupport.displayOrDash(cell.getValue().article())));
        TableColumn<StockRow, String> nameCol = new TableColumn<>("Наименование");
        nameCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(
                                WarehouseMoveDialogSupport.displayOrDash(cell.getValue().name())));
        TableColumn<StockRow, String> colorCol = new TableColumn<>("Цвет");
        colorCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(
                                WarehouseMoveDialogSupport.displayOrDash(cell.getValue().color())));
        TableColumn<StockRow, String> sizeCol = new TableColumn<>("Размер");
        sizeCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(
                                WarehouseMoveDialogSupport.displayOrDash(cell.getValue().size())));
        TableColumn<StockRow, String> cellCol = new TableColumn<>("Ячейка");
        cellCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(
                                WarehouseMoveDialogSupport.displayOrDash(
                                        cell.getValue().cellCode())));
        TableColumn<StockRow, String> currentCol = new TableColumn<>("Текущий остаток");
        currentCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(
                                DecimalUiFormat.formatRu(cell.getValue().availableQuantity())));
        TableColumn<StockRow, String> deltaCol = new TableColumn<>("Корректировка");
        deltaCol.setCellValueFactory(cell -> deltaText);
        deltaCol.setCellFactory(
                column ->
                        new TableCell<>() {
                            private final TextField field = new TextField();

                            {
                                field.setPromptText("+/−");
                                field.textProperty()
                                        .addListener(
                                                (obs, o, n) -> {
                                                    if (!java.util.Objects.equals(
                                                            deltaText.get(), n)) {
                                                        deltaText.set(n);
                                                    }
                                                });
                            }

                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty) {
                                    setGraphic(null);
                                    return;
                                }
                                if (!java.util.Objects.equals(field.getText(), deltaText.get())) {
                                    field.setText(deltaText.get());
                                }
                                setGraphic(field);
                            }
                        });
        TableColumn<StockRow, String> unitCol = new TableColumn<>("Ед.");
        unitCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(
                                WarehouseMoveDialogSupport.displayOrDash(
                                        cell.getValue().unitOfMeasure())));
        table.getColumns()
                .setAll(
                        articleCol,
                        nameCol,
                        colorCol,
                        sizeCol,
                        cellCol,
                        currentCol,
                        deltaCol,
                        unitCol);

        VBox content = new VBox(8, table);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);
        dialog.setOnShown(
                e -> {
                    Button okButton = (Button) dialog.getDialogPane().lookupButton(okType);
                    Button cancelButton = (Button) dialog.getDialogPane().lookupButton(cancelType);
                    if (okButton != null) {
                        okButton.getStyleClass().add("tmp-button-primary");
                    }
                    if (cancelButton != null) {
                        cancelButton.getStyleClass().add("tmp-button-secondary");
                    }
                });
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != okType) {
            return;
        }
        try {
            BigDecimal delta =
                    DecimalQuantityParser.parseNonZero(deltaText.get(), "количество изменения");
            viewModel.executeStockAdjustment(StockMoveLine.from(row, row.availableQuantity()), delta);
        } catch (IllegalArgumentException ex) {
            viewModel.errorMessageProperty().set(ex.getMessage());
        }
    }

    private String warehouseLabel(UUID warehouseId) {
        return viewModel.warehouseFilterOptions().stream()
                .filter(option -> warehouseId.equals(option.warehouseId()))
                .map(WarehouseFilterOption::label)
                .findFirst()
                .orElse(warehouseId.toString());
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
        historyArticleColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().articleText()));
        historyNameColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().nameText()));
        historyColorColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().colorText()));
        historySizeColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().sizeText()));
        historyQuantityColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().quantityText()));
        historyQuantityColumn.setCellFactory(column -> rightAlignedHistoryTextCell());
        historyUnitColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().unitText()));
        historyUnitColumn.setCellFactory(column -> centerAlignedHistoryTextCell());
        historySourceColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().sourceText()));
        historyDestinationColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().destinationText()));
        historyActorColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().actorText()));

        historyTable.setItems(viewModel.historyRows());
        // Same Stocks stability pattern: UNCONSTRAINED + reserved vertical gutter.
        historyTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        configureHistoryColumnWidths();
        installHistoryRefreshLayoutTrace();
        Label placeholder = new Label("Нет операций");
        placeholder.textProperty().bind(viewModel.statusMessageProperty());
        placeholder.getStyleClass().add("tmp-empty-state-hint");
        placeholder.setWrapText(true);
        historyTable.setPlaceholder(placeholder);
    }

    /**
     * Flex only the name column; always subtract a vertical-scrollbar gutter so show/hide of the
     * bar does not change History column geometry.
     */
    private void configureHistoryColumnWidths() {
        final double verticalScrollGutter = 18.0;
        Runnable redistribute =
                () -> {
                    double tableWidth = historyTable.getWidth();
                    if (tableWidth <= 0) {
                        return;
                    }
                    double fixed =
                            historyOccurredAtColumn.getPrefWidth()
                                    + historyOperationColumn.getPrefWidth()
                                    + historyArticleColumn.getPrefWidth()
                                    + historyColorColumn.getPrefWidth()
                                    + historySizeColumn.getPrefWidth()
                                    + historyQuantityColumn.getPrefWidth()
                                    + historyUnitColumn.getPrefWidth()
                                    + historySourceColumn.getPrefWidth()
                                    + historyDestinationColumn.getPrefWidth()
                                    + historyActorColumn.getPrefWidth();
                    if (fixed <= 0) {
                        fixed = 135 + 110 + 135 + 130 + 90 + 90 + 60 + 180 + 180 + 120;
                    }
                    double nameWidth = Math.max(180, tableWidth - fixed - verticalScrollGutter);
                    historyNameColumn.setPrefWidth(nameWidth);
                };
        historyTable.widthProperty().addListener((obs, o, n) -> redistribute.run());
        Platform.runLater(redistribute);
    }

    private void installHistoryRefreshLayoutTrace() {
        viewModel
                .loadingProperty()
                .addListener(
                        (obs, wasLoading, isLoading) -> {
                            if (viewModel.selectedTabProperty().get() != WorkspaceTab.HISTORY) {
                                return;
                            }
                            Platform.runLater(
                                    () ->
                                            traceHistoryLayout(
                                                    Boolean.TRUE.equals(isLoading)
                                                            ? "loading-on"
                                                            : "loading-off"));
                        });
    }

    private void traceHistoryLayout(String phase) {
        if (!HistoryRefreshTrace.enabled()) {
            return;
        }
        ScrollBar vBar = findHistoryScrollBar(Orientation.VERTICAL);
        ScrollBar hBar = findHistoryScrollBar(Orientation.HORIZONTAL);
        HistoryRefreshTrace.layoutSnapshot(
                phase,
                historyTable.getWidth(),
                historyTable.getHeight(),
                vBar != null && vBar.isVisible(),
                hBar != null && hBar.isVisible(),
                historyLoadingLabel.isVisible(),
                historyLoadingLabel.isManaged(),
                historyLoadingLabel.getHeight(),
                estimateTopVisibleHistoryIndex());
    }

    private ScrollBar findHistoryScrollBar(Orientation orientation) {
        for (Node node : historyTable.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar bar && bar.getOrientation() == orientation) {
                return bar;
            }
        }
        return null;
    }

    private int estimateTopVisibleHistoryIndex() {
        ScrollBar vBar = findHistoryScrollBar(Orientation.VERTICAL);
        if (vBar == null || historyTable.getItems().isEmpty()) {
            return historyTable.getItems().isEmpty() ? -1 : 0;
        }
        double max = vBar.getMax();
        if (max <= 0) {
            return 0;
        }
        return (int) Math.round((vBar.getValue() / max) * (historyTable.getItems().size() - 1));
    }

    private static TableCell<StockRow, String> rightAlignedTextCell() {
        return alignedTextCell(Pos.CENTER_RIGHT);
    }

    private static TableCell<StockRow, String> centerAlignedTextCell() {
        return alignedTextCell(Pos.CENTER);
    }

    private static TableCell<StockRow, String> alignedTextCell(Pos alignment) {
        TableCell<StockRow, String> cell =
                new TableCell<>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : item);
                    }
                };
        cell.setAlignment(alignment);
        return cell;
    }

    private static TableCell<HistoryRow, String> rightAlignedHistoryTextCell() {
        return alignedHistoryTextCell(Pos.CENTER_RIGHT);
    }

    private static TableCell<HistoryRow, String> centerAlignedHistoryTextCell() {
        return alignedHistoryTextCell(Pos.CENTER);
    }

    private static TableCell<HistoryRow, String> alignedHistoryTextCell(Pos alignment) {
        TableCell<HistoryRow, String> cell =
                new TableCell<>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : item);
                    }
                };
        cell.setAlignment(alignment);
        return cell;
    }

}
