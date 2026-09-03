package com.tmp.ui.shell.screen.orderitemlist;

import com.tmp.ui.shell.navigation.ViewModelAware;
import com.tmp.ui.shell.order.worklist.OperationalStatusIndicator;
import com.tmp.ui.shell.order.worklist.OrderItemOperationalStatus;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;

/**
 * Order item list FXML controller. Query-driven; no Spring imports.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX Controller retains ViewModel for FXML wiring")
public final class OrderItemListController implements ViewModelAware<OrderItemListViewModel> {

    @FXML
    private Label titleLabel;

    @FXML
    private Button createItemButton;

    @FXML
    private TableView<OrderItemListRow> itemsTable;

    @FXML
    private TableColumn<OrderItemListRow, String> productCodeColumn;

    @FXML
    private TableColumn<OrderItemListRow, String> nameColumn;

    @FXML
    private TableColumn<OrderItemListRow, String> quantityColumn;

    @FXML
    private TableColumn<OrderItemListRow, OrderItemOperationalStatus> statusColumn;

    @FXML
    private Button previousPageButton;

    @FXML
    private Button nextPageButton;

    @FXML
    private Label pageLabel;

    @FXML
    private Label errorLabel;

    private OrderItemListViewModel viewModel;

    @Override
    public void setViewModel(OrderItemListViewModel viewModel) {
        this.viewModel = viewModel;
        titleLabel.textProperty().bind(viewModel.titleProperty());
        itemsTable.setItems(viewModel.items());
        itemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        nameColumn.setMaxWidth(Double.MAX_VALUE);
        itemsTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> viewModel.selectedItemProperty().set(newValue));
        viewModel.selectedItemProperty()
                .addListener(
                        (obs, oldValue, newValue) -> {
                            if (newValue == null) {
                                itemsTable.getSelectionModel().clearSelection();
                            } else if (itemsTable.getSelectionModel().getSelectedItem() != newValue) {
                                itemsTable.getSelectionModel().select(newValue);
                            }
                        });

        productCodeColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().productCode()));
        nameColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(cell.getValue().name()));
        quantityColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().quantityDisplay()));
        statusColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleObjectProperty<>(
                                cell.getValue().operationalStatus()));
        statusColumn.setCellFactory(column -> new StatusTableCell());

        createItemButton.disableProperty().bind(viewModel.canCreateProperty().not());
        createItemButton.visibleProperty().bind(viewModel.canCreateProperty());
        createItemButton.managedProperty().bind(createItemButton.visibleProperty());
        createItemButton.setOnAction(e -> viewModel.createItem());

        previousPageButton.setOnAction(e -> viewModel.previousPage());
        nextPageButton.setOnAction(e -> viewModel.nextPage());
        previousPageButton.disableProperty().bind(viewModel.canGoPreviousProperty().not());
        nextPageButton.disableProperty().bind(viewModel.canGoNextProperty().not());
        pageLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "Страница "
                        + (viewModel.pageIndexProperty().get() + 1)
                        + " · "
                        + viewModel.totalElementsProperty().get()
                        + " позиций",
                viewModel.pageIndexProperty(),
                viewModel.totalElementsProperty()));

        itemsTable.setRowFactory(table -> createItemRow());
        itemsTable.setOnKeyPressed(
                event -> {
                    if (event.getCode() == KeyCode.ENTER
                            && viewModel.selectedItemProperty().get() != null) {
                        viewModel.openSelected();
                    }
                });

        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    String message = viewModel.errorMessageProperty().get();
                    return message != null && !message.isBlank();
                },
                viewModel.errorMessageProperty()));
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
    }

    private TableRow<OrderItemListRow> createItemRow() {
        TableRow<OrderItemListRow> row = new TableRow<>();
        MenuItem openSpec = new MenuItem("Открыть спецификацию");
        MenuItem edit = new MenuItem("Изменить данные позиции");
        MenuItem cancel = new MenuItem("Отменить позицию");
        cancel.getStyleClass().add("tmp-menu-item-danger");
        ContextMenu menu = new ContextMenu(openSpec, edit, cancel);

        openSpec.visibleProperty()
                .bind(
                        Bindings.createBooleanBinding(
                                () -> !row.isEmpty() && viewModel.hasSpecificationViewPermission(),
                                row.emptyProperty(),
                                row.itemProperty()));
        edit.visibleProperty()
                .bind(
                        Bindings.createBooleanBinding(
                                () -> !row.isEmpty() && viewModel.canEditItem(row.getItem()),
                                row.emptyProperty(),
                                row.itemProperty()));
        cancel.visibleProperty()
                .bind(
                        Bindings.createBooleanBinding(
                                () -> !row.isEmpty() && viewModel.canCancelItem(row.getItem()),
                                row.emptyProperty(),
                                row.itemProperty()));
        row.contextMenuProperty()
                .bind(
                        Bindings.createObjectBinding(
                                () -> {
                                    if (row.isEmpty()) {
                                        return null;
                                    }
                                    if (!openSpec.isVisible() && !edit.isVisible() && !cancel.isVisible()) {
                                        return null;
                                    }
                                    return menu;
                                },
                                row.emptyProperty(),
                                openSpec.visibleProperty(),
                                edit.visibleProperty(),
                                cancel.visibleProperty()));

        openSpec.setOnAction(
                e -> {
                    if (!row.isEmpty()) {
                        viewModel.selectedItemProperty().set(row.getItem());
                        viewModel.openSpecification(row.getItem());
                    }
                });
        edit.setOnAction(
                e -> {
                    if (!row.isEmpty()) {
                        viewModel.editItem(row.getItem());
                    }
                });
        cancel.setOnAction(
                e -> {
                    if (!row.isEmpty()) {
                        viewModel.cancelItem(row.getItem());
                    }
                });

        row.setOnContextMenuRequested(
                event -> {
                    if (!row.isEmpty()) {
                        itemsTable.getSelectionModel().select(row.getItem());
                        viewModel.selectedItemProperty().set(row.getItem());
                    }
                });
        row.setOnMouseClicked(
                event -> {
                    if (event.getButton() == MouseButton.PRIMARY
                            && event.getClickCount() == 2
                            && !row.isEmpty()) {
                        viewModel.selectedItemProperty().set(row.getItem());
                        viewModel.openSelected();
                    }
                });
        return row;
    }

    private static final class StatusTableCell
            extends TableCell<OrderItemListRow, OrderItemOperationalStatus> {
        @Override
        protected void updateItem(OrderItemOperationalStatus status, boolean empty) {
            super.updateItem(status, empty);
            if (empty || status == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            setGraphic(OperationalStatusIndicator.create(status));
            setText(null);
        }
    }
}
