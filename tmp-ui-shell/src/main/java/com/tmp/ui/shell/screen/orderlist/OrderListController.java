package com.tmp.ui.shell.screen.orderlist;

import com.tmp.ui.shell.navigation.ViewModelAware;
import com.tmp.ui.shell.order.worklist.DateTimePresentation;
import com.tmp.ui.shell.order.worklist.OperationalStatusIndicator;
import com.tmp.ui.shell.order.worklist.OrderListPeriod;
import com.tmp.ui.shell.order.worklist.OrderOperationalStatus;
import com.tmp.ui.shell.order.worklist.OrderOperationalSummary;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.util.Duration;
import javafx.util.StringConverter;

/**
 * Order list FXML controller. Binds operational list filters, table and pagination.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX Controller retains ViewModel for FXML wiring")
public final class OrderListController implements ViewModelAware<OrderListViewModel> {

    @FXML
    private Label titleLabel;
    @FXML
    private Label subtitleLabel;
    @FXML
    private Button createOrderButton;
    @FXML
    private Button importOrderButton;
    @FXML
    private TextField quickSearchField;
    @FXML
    private ComboBox<OrderListPeriod.Preset> periodCombo;
    @FXML
    private DatePicker periodFromPicker;
    @FXML
    private DatePicker periodToPicker;
    @FXML
    private Button customerFilterButton;
    @FXML
    private CheckBox statusEditingCheck;
    @FXML
    private CheckBox statusAwaitingCheck;
    @FXML
    private CheckBox statusInProductionCheck;
    @FXML
    private CheckBox statusCompletedCheck;
    @FXML
    private CheckBox statusPartialCheck;
    @FXML
    private CheckBox statusCancelledCheck;
    @FXML
    private Label loadingLabel;
    @FXML
    private TableView<OrderOperationalSummary> ordersTable;
    @FXML
    private TableColumn<OrderOperationalSummary, String> orderNumberColumn;
    @FXML
    private TableColumn<OrderOperationalSummary, String> customerNameColumn;
    @FXML
    private TableColumn<OrderOperationalSummary, String> createdAtColumn;
    @FXML
    private TableColumn<OrderOperationalSummary, String> itemCountColumn;
    @FXML
    private TableColumn<OrderOperationalSummary, OrderOperationalSummary> statusColumn;
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

    private OrderListViewModel viewModel;
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(300));
    private boolean binding;

    @Override
    public void setViewModel(OrderListViewModel viewModel) {
        this.viewModel = viewModel;
        binding = true;
        titleLabel.textProperty().bind(viewModel.titleProperty());
        subtitleLabel.textProperty().bind(viewModel.subtitleProperty());
        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
        customerFilterButton.textProperty().bind(viewModel.customerFilterLabelProperty());

        orderNumberColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().orderNumber()));
        customerNameColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        DateTimePresentation.customerDisplay(cell.getValue().customerName())));
        createdAtColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        DateTimePresentation.format(cell.getValue().createdAt())));
        itemCountColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        Long.toString(cell.getValue().itemQuantity())));
        statusColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleObjectProperty<>(cell.getValue()));
        statusColumn.setCellFactory(column -> new StatusTableCell());

        ordersTable.setItems(viewModel.orders());
        ordersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Label placeholder = new Label();
        placeholder.textProperty().bind(viewModel.statusMessageProperty());
        placeholder.getStyleClass().add("tmp-empty-state-hint");
        placeholder.setWrapText(true);
        ordersTable.setPlaceholder(placeholder);
        ordersTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> viewModel.selectedOrderProperty().set(newValue));
        viewModel.selectedOrderProperty().addListener(
                (obs, oldValue, newValue) -> applyViewModelSelection(newValue));
        applyViewModelSelection(viewModel.selectedOrderProperty().get());

        createOrderButton.disableProperty().bind(viewModel.canCreateProperty().not());
        importOrderButton.disableProperty().bind(viewModel.canImportProperty().not());
        createOrderButton.setOnAction(e -> viewModel.createOrder());
        importOrderButton.setOnAction(e -> viewModel.importOrder());

        searchDebounce.setOnFinished(e -> viewModel.onSearchChanged());
        quickSearchField.setText(viewModel.quickSearchProperty().get());
        quickSearchField.textProperty().addListener((obs, old, value) -> {
            viewModel.quickSearchProperty().set(value);
            searchDebounce.playFromStart();
        });

        periodCombo.setItems(FXCollections.observableArrayList(OrderListPeriod.Preset.values()));
        periodCombo.setConverter(periodConverter());
        periodCombo.setValue(viewModel.periodPresetProperty().get());
        periodCombo.valueProperty().addListener((obs, old, value) -> {
            if (value == null || binding) {
                return;
            }
            viewModel.periodPresetProperty().set(value);
            updateCustomPeriodVisibility(value);
            viewModel.onFiltersChanged();
        });
        periodFromPicker.valueProperty().bindBidirectional(viewModel.customFromProperty());
        periodToPicker.valueProperty().bindBidirectional(viewModel.customToProperty());
        periodFromPicker.valueProperty().addListener((obs, old, value) -> {
            if (!binding && viewModel.periodPresetProperty().get() == OrderListPeriod.Preset.CUSTOM) {
                viewModel.onFiltersChanged();
            }
        });
        periodToPicker.valueProperty().addListener((obs, old, value) -> {
            if (!binding && viewModel.periodPresetProperty().get() == OrderListPeriod.Preset.CUSTOM) {
                viewModel.onFiltersChanged();
            }
        });
        updateCustomPeriodVisibility(viewModel.periodPresetProperty().get());

        bindStatus(statusEditingCheck, OrderOperationalStatus.EDITING);
        bindStatus(statusAwaitingCheck, OrderOperationalStatus.AWAITING_PRODUCTION);
        bindStatus(statusInProductionCheck, OrderOperationalStatus.IN_PRODUCTION);
        bindStatus(statusCompletedCheck, OrderOperationalStatus.COMPLETED);
        bindStatus(statusPartialCheck, OrderOperationalStatus.PARTIALLY_COMPLETED);
        bindStatus(statusCancelledCheck, OrderOperationalStatus.CANCELLED);
        refreshStatusCheckEnablement();
        viewModel.selectedStatuses().addListener((javafx.collections.SetChangeListener<OrderOperationalStatus>) change -> {
            if (binding) {
                return;
            }
            binding = true;
            try {
                syncStatusCheck(statusEditingCheck, OrderOperationalStatus.EDITING);
                syncStatusCheck(statusAwaitingCheck, OrderOperationalStatus.AWAITING_PRODUCTION);
                syncStatusCheck(statusInProductionCheck, OrderOperationalStatus.IN_PRODUCTION);
                syncStatusCheck(statusCompletedCheck, OrderOperationalStatus.COMPLETED);
                syncStatusCheck(statusPartialCheck, OrderOperationalStatus.PARTIALLY_COMPLETED);
                syncStatusCheck(statusCancelledCheck, OrderOperationalStatus.CANCELLED);
                refreshStatusCheckEnablement();
            } finally {
                binding = false;
            }
        });

        customerFilterButton.setOnAction(e -> openCustomerFilterPopup());

        ordersTable.setRowFactory(table -> {
            TableRow<OrderOperationalSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2
                        && !row.isEmpty()) {
                    viewModel.selectedOrderProperty().set(row.getItem());
                    viewModel.openSelectedOrder();
                }
            });
            return row;
        });
        ordersTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && viewModel.selectedOrderProperty().get() != null) {
                viewModel.openSelectedOrder();
            }
        });

        previousPageButton.setOnAction(e -> viewModel.previousPage());
        nextPageButton.setOnAction(e -> viewModel.nextPage());
        previousPageButton.disableProperty().bind(viewModel.canGoPreviousProperty().not());
        nextPageButton.disableProperty().bind(viewModel.canGoNextProperty().not());
        pageLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "Страница "
                        + (viewModel.pageIndexProperty().get() + 1)
                        + " · "
                        + viewModel.totalElementsProperty().get()
                        + " заказов",
                viewModel.pageIndexProperty(),
                viewModel.totalElementsProperty()));

        loadingLabel.visibleProperty().bind(viewModel.loadingProperty());
        loadingLabel.managedProperty().bind(loadingLabel.visibleProperty());
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    String message = viewModel.errorMessageProperty().get();
                    return message != null && !message.isBlank();
                },
                viewModel.errorMessageProperty()));
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
        binding = false;
    }

    /**
     * Synchronizes TableView selection from the ViewModel. {@code scrollTo} runs only when the
     * table does not already have that order selected — ordinary click/keyboard selection must
     * not force a viewport jump. Programmatic restore (Back memento / initial bind) still
     * scrolls the target into view.
     */
    private void applyViewModelSelection(OrderOperationalSummary newValue) {
        if (newValue == null) {
            ordersTable.getSelectionModel().clearSelection();
            return;
        }
        OrderOperationalSummary tableItem = tableItemFor(newValue);
        if (tableItem == null) {
            return;
        }
        if (sameOrder(ordersTable.getSelectionModel().getSelectedItem(), tableItem)) {
            return;
        }
        ordersTable.getSelectionModel().select(tableItem);
        ordersTable.scrollTo(tableItem);
    }

    private OrderOperationalSummary tableItemFor(OrderOperationalSummary wanted) {
        if (ordersTable.getItems().contains(wanted)) {
            return wanted;
        }
        for (OrderOperationalSummary item : ordersTable.getItems()) {
            if (sameOrder(item, wanted)) {
                return item;
            }
        }
        return null;
    }

    private static boolean sameOrder(OrderOperationalSummary left, OrderOperationalSummary right) {
        return left != null && right != null && left.orderId().equals(right.orderId());
    }

    private void openCustomerFilterPopup() {
        var loaded = viewModel.loadCustomerFilterOptions();
        if (loaded.isEmpty()) {
            return;
        }
        OrderListCustomerFilterPopup.show(
                customerFilterButton,
                loaded.get(),
                viewModel.selectAllCustomersProperty().get(),
                viewModel.selectedCustomerKeys(),
                selection -> viewModel.applyCustomerSelection(selection.selectAll(), selection.keys()));
    }

    private void bindStatus(CheckBox checkBox, OrderOperationalStatus status) {
        checkBox.setSelected(viewModel.selectedStatuses().contains(status));
        checkBox.selectedProperty().addListener((obs, old, selected) -> {
            if (binding) {
                return;
            }
            boolean wantSelected = Boolean.TRUE.equals(selected);
            if (!wantSelected && !viewModel.canDeselectStatus(status)) {
                binding = true;
                try {
                    checkBox.setSelected(true);
                } finally {
                    binding = false;
                }
                return;
            }
            viewModel.toggleStatus(status, wantSelected);
            refreshStatusCheckEnablement();
        });
    }

    private void syncStatusCheck(CheckBox checkBox, OrderOperationalStatus status) {
        checkBox.setSelected(viewModel.selectedStatuses().contains(status));
    }

    private void refreshStatusCheckEnablement() {
        disableIfLast(statusEditingCheck, OrderOperationalStatus.EDITING);
        disableIfLast(statusAwaitingCheck, OrderOperationalStatus.AWAITING_PRODUCTION);
        disableIfLast(statusInProductionCheck, OrderOperationalStatus.IN_PRODUCTION);
        disableIfLast(statusCompletedCheck, OrderOperationalStatus.COMPLETED);
        disableIfLast(statusPartialCheck, OrderOperationalStatus.PARTIALLY_COMPLETED);
        disableIfLast(statusCancelledCheck, OrderOperationalStatus.CANCELLED);
    }

    private void disableIfLast(CheckBox checkBox, OrderOperationalStatus status) {
        boolean lastSelected = checkBox.isSelected() && !viewModel.canDeselectStatus(status);
        checkBox.setDisable(lastSelected);
    }

    private void updateCustomPeriodVisibility(OrderListPeriod.Preset preset) {
        boolean custom = preset == OrderListPeriod.Preset.CUSTOM;
        periodFromPicker.setVisible(custom);
        periodFromPicker.setManaged(custom);
        periodToPicker.setVisible(custom);
        periodToPicker.setManaged(custom);
    }

    private static StringConverter<OrderListPeriod.Preset> periodConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(OrderListPeriod.Preset preset) {
                if (preset == null) {
                    return "";
                }
                return switch (preset) {
                    case TODAY -> "Сегодня";
                    case LAST_7_DAYS -> "Последние 7 дней";
                    case LAST_30_DAYS -> "Последние 30 дней";
                    case CURRENT_MONTH -> "Текущий месяц";
                    case CUSTOM -> "Другой период";
                };
            }

            @Override
            public OrderListPeriod.Preset fromString(String string) {
                return OrderListPeriod.Preset.LAST_30_DAYS;
            }
        };
    }

    private static final class StatusTableCell extends TableCell<OrderOperationalSummary, OrderOperationalSummary> {
        @Override
        protected void updateItem(OrderOperationalSummary item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            setGraphic(OperationalStatusIndicator.create(item.operationalStatus()));
            setText(null);
        }
    }
}
