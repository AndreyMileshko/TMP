package com.tmp.ui.shell.screen.orderitemlist;

import com.tmp.order.api.OrderItemDto;
import com.tmp.ui.shell.navigation.ViewModelAware;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

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
    private Button openItemButton;

    @FXML
    private Button backButton;

    @FXML
    private TableView<OrderItemDto> itemsTable;

    @FXML
    private TableColumn<OrderItemDto, String> productCodeColumn;

    @FXML
    private TableColumn<OrderItemDto, String> nameColumn;

    @FXML
    private TableColumn<OrderItemDto, String> statusColumn;

    @FXML
    private TableColumn<OrderItemDto, String> activeRevisionColumn;

    @FXML
    private Label errorLabel;

    @Override
    public void setViewModel(OrderItemListViewModel viewModel) {
        titleLabel.textProperty().bind(viewModel.titleProperty());
        itemsTable.setItems(viewModel.items());
        itemsTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> viewModel.selectedItemProperty().set(newValue));

        productCodeColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                nullToEmpty(cell.getValue().productCode())));
        nameColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                nullToEmpty(cell.getValue().name())));
        statusColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().status().name()));
        activeRevisionColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue()
                                        .activeRevisionNumber()
                                        .map(Object::toString)
                                        .orElse("")));

        createItemButton.disableProperty().bind(viewModel.canCreateProperty().not());
        openItemButton.disableProperty().bind(viewModel.canOpenSelectedProperty().not());

        createItemButton.setOnAction(e -> viewModel.createItem());
        openItemButton.setOnAction(e -> viewModel.openSelected());
        backButton.setOnAction(e -> viewModel.backToOrder());

        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    String message = viewModel.errorMessageProperty().get();
                    return message != null && !message.isBlank();
                },
                viewModel.errorMessageProperty()));
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
