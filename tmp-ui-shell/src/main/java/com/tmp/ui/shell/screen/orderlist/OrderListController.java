package com.tmp.ui.shell.screen.orderlist;

import com.tmp.order.api.OrderSummaryDto;
import com.tmp.ui.shell.navigation.ViewModelAware;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/**
 * Order list FXML controller. Read-only; binds filters/pagination to {@link OrderListViewModel}.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX Controller retains ViewModel for FXML wiring")
public final class OrderListController implements ViewModelAware<OrderListViewModel> {

    @FXML
    private Label titleLabel;

    @FXML
    private TextField orderNumberFilterField;

    @FXML
    private TextField orderStatusFilterField;

    @FXML
    private TextField customerRefFilterField;

    @FXML
    private TextField customerNameFilterField;

    @FXML
    private TextField createdFromFilterField;

    @FXML
    private TextField createdToFilterField;

    @FXML
    private Button applyFilterButton;

    @FXML
    private Button clearFilterButton;

    @FXML
    private Label loadingLabel;

    @FXML
    private Label emptyLabel;

    @FXML
    private TableView<OrderSummaryDto> ordersTable;

    @FXML
    private TableColumn<OrderSummaryDto, String> orderNumberColumn;

    @FXML
    private TableColumn<OrderSummaryDto, String> customerNameColumn;

    @FXML
    private TableColumn<OrderSummaryDto, String> statusColumn;

    @FXML
    private TableColumn<OrderSummaryDto, String> createdAtColumn;

    @FXML
    private TableColumn<OrderSummaryDto, String> customerRefColumn;

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

    @Override
    public void setViewModel(OrderListViewModel viewModel) {
        titleLabel.textProperty().bind(viewModel.titleProperty());
        statusLabel.textProperty().bind(viewModel.statusMessageProperty());

        orderNumberColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().orderNumber()));
        customerNameColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().customerName()));
        statusColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().status().name()));
        createdAtColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().createdAt().toString()));
        customerRefColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().customerRef() == null ? "" : cell.getValue().customerRef()));

        ordersTable.setItems(viewModel.orders());
        orderNumberFilterField.textProperty().bindBidirectional(viewModel.orderNumberFilterProperty());
        orderStatusFilterField.textProperty().bindBidirectional(viewModel.orderStatusFilterProperty());
        customerRefFilterField.textProperty().bindBidirectional(viewModel.customerRefFilterProperty());
        customerNameFilterField.textProperty().bindBidirectional(viewModel.customerNameFilterProperty());
        createdFromFilterField.textProperty().bindBidirectional(viewModel.createdFromFilterProperty());
        createdToFilterField.textProperty().bindBidirectional(viewModel.createdToFilterProperty());

        applyFilterButton.setOnAction(e -> viewModel.applyFilters());
        clearFilterButton.setOnAction(e -> viewModel.clearFilters());
        previousPageButton.setOnAction(e -> viewModel.previousPage());
        nextPageButton.setOnAction(e -> viewModel.nextPage());

        pageLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "Страница "
                        + (viewModel.pageIndexProperty().get() + 1)
                        + " / записей: "
                        + viewModel.totalElementsProperty().get(),
                viewModel.pageIndexProperty(),
                viewModel.totalElementsProperty()));

        loadingLabel.visibleProperty().bind(viewModel.loadingProperty());
        loadingLabel.managedProperty().bind(loadingLabel.visibleProperty());
        emptyLabel.visibleProperty().bind(viewModel.emptyResultProperty());
        emptyLabel.managedProperty().bind(emptyLabel.visibleProperty());

        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    String message = viewModel.errorMessageProperty().get();
                    return message != null && !message.isBlank();
                },
                viewModel.errorMessageProperty()));
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        viewModel.refresh();
    }
}
