package com.tmp.ui.shell.screen.warehouse;

import com.tmp.ui.shell.navigation.ViewModelAware;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.CellDetailRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.SummaryRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.WarehouseFilterOption;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.WorkspaceTab;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Modern warehouse workspace controller (Остатки tab and placeholders).
 */
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
    private VBox stockPane;

    @FXML
    private Label tasksPlaceholderLabel;

    @FXML
    private Label historyPlaceholderLabel;

    @FXML
    private ComboBox<WarehouseFilterOption> warehouseCombo;

    @FXML
    private TextField searchField;

    @FXML
    private Button searchButton;

    @FXML
    private Label loadingLabel;

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

        loadingLabel.visibleProperty().bind(viewModel.loadingProperty());
        loadingLabel.managedProperty().bind(loadingLabel.visibleProperty());

        ToggleGroup tabGroup = new ToggleGroup();
        tasksTabButton.setToggleGroup(tabGroup);
        stockTabButton.setToggleGroup(tabGroup);
        historyTabButton.setToggleGroup(tabGroup);
        stockTabButton.setSelected(true);

        tasksTabButton.setOnAction(e -> selectTab(WorkspaceTab.TASKS));
        stockTabButton.setOnAction(e -> selectTab(WorkspaceTab.STOCK));
        historyTabButton.setOnAction(e -> selectTab(WorkspaceTab.HISTORY));

        viewModel.selectedTabProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null || binding) {
                return;
            }
            binding = true;
            try {
                switch (newValue) {
                    case TASKS -> tasksTabButton.setSelected(true);
                    case STOCK -> stockTabButton.setSelected(true);
                    case HISTORY -> historyTabButton.setSelected(true);
                }
                updateTabVisibility(newValue);
            } finally {
                binding = false;
            }
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

        configureStockTable();

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

        binding = false;
        viewModel.onScreenOpened();
    }

    private void selectTab(WorkspaceTab tab) {
        if (binding) {
            return;
        }
        viewModel.selectTab(tab);
        updateTabVisibility(tab);
    }

    private void updateTabVisibility(WorkspaceTab tab) {
        stockPane.setVisible(tab == WorkspaceTab.STOCK);
        stockPane.setManaged(tab == WorkspaceTab.STOCK);
        tasksPlaceholderLabel.setVisible(tab == WorkspaceTab.TASKS);
        tasksPlaceholderLabel.setManaged(tab == WorkspaceTab.TASKS);
        historyPlaceholderLabel.setVisible(tab == WorkspaceTab.HISTORY);
        historyPlaceholderLabel.setManaged(tab == WorkspaceTab.HISTORY);
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
