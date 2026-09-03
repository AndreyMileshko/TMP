package com.tmp.ui.shell.screen.ordereditor;

import com.tmp.ui.shell.navigation.ViewModelAware;
import com.tmp.ui.shell.order.worklist.OrderOperationalStatus;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.shape.Circle;
import javafx.stage.Window;

/**
 * Order editor FXML controller. Document orchestration stays in the ViewModel.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX Controller retains ViewModel for FXML wiring")
public final class OrderEditorController implements ViewModelAware<OrderEditorViewModel> {

    @FXML
    private Label titleLabel;
    @FXML
    private Label subtitleLabel;
    @FXML
    private Circle statusDot;
    @FXML
    private TextField orderNumberField;
    @FXML
    private TextField customerNameField;
    @FXML
    private TextField customerRefField;
    @FXML
    private TextField contractRefField;
    @FXML
    private TextField siteRefField;
    @FXML
    private TextField responsibleManagerField;
    @FXML
    private TextField directionField;
    @FXML
    private TextField currencyField;
    @FXML
    private Button saveButton;
    @FXML
    private Button transferButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Button itemsButton;
    @FXML
    private Label successLabel;
    @FXML
    private Label errorLabel;

    @Override
    public void setViewModel(OrderEditorViewModel viewModel) {
        titleLabel.textProperty().bind(viewModel.titleProperty());
        subtitleLabel.textProperty().bind(viewModel.subtitleProperty());
        applyStatusDot(viewModel.operationalStatus());
        viewModel.statusTextProperty().addListener((obs, old, value) ->
                applyStatusDot(viewModel.operationalStatus()));

        orderNumberField.textProperty().bindBidirectional(viewModel.orderNumberProperty());
        customerNameField.textProperty().bindBidirectional(viewModel.customerNameProperty());
        customerRefField.textProperty().bindBidirectional(viewModel.customerRefProperty());
        contractRefField.textProperty().bindBidirectional(viewModel.contractRefProperty());
        siteRefField.textProperty().bindBidirectional(viewModel.siteRefProperty());
        responsibleManagerField
                .textProperty()
                .bindBidirectional(viewModel.responsibleManagerProperty());
        directionField.textProperty().bindBidirectional(viewModel.directionProperty());
        currencyField.textProperty().bindBidirectional(viewModel.currencyProperty());

        orderNumberField.disableProperty().bind(viewModel.orderNumberEditableProperty().not());
        customerNameField.disableProperty().bind(viewModel.fieldsEditableProperty().not());
        customerRefField.disableProperty().bind(viewModel.fieldsEditableProperty().not());
        contractRefField.disableProperty().bind(viewModel.fieldsEditableProperty().not());
        siteRefField.disableProperty().bind(viewModel.fieldsEditableProperty().not());
        responsibleManagerField.disableProperty().bind(viewModel.fieldsEditableProperty().not());
        directionField.disableProperty().bind(viewModel.fieldsEditableProperty().not());
        currencyField.disableProperty().bind(viewModel.fieldsEditableProperty().not());

        saveButton.disableProperty().bind(viewModel.canSaveProperty().not());
        transferButton.disableProperty().bind(viewModel.canTransferToWorkProperty().not());
        cancelButton.disableProperty().bind(viewModel.canCancelProperty().not());
        itemsButton.disableProperty().bind(viewModel.canOpenItemsProperty().not());

        saveButton.setOnAction(e -> viewModel.save());
        transferButton.setOnAction(e -> {
            Window owner = transferButton.getScene() == null ? null : transferButton.getScene().getWindow();
            var confirmed =
                    OrderEditorDialogs.confirmTransferToWork(
                            owner,
                            viewModel.transferConfirmationTitle(),
                            OrderEditorViewModel.TRANSFER_IMMUTABILITY_HINT);
            if (confirmed.orElse(false)) {
                viewModel.transferToWork();
            }
        });
        cancelButton.setOnAction(e -> {
            Window owner = cancelButton.getScene() == null ? null : cancelButton.getScene().getWindow();
            var confirmed =
                    OrderEditorDialogs.confirmCancelOrder(owner, viewModel.orderNumberProperty().get());
            if (confirmed.orElse(false)) {
                viewModel.cancelOrder();
            }
        });
        itemsButton.setOnAction(e -> viewModel.openItems());

        successLabel.textProperty().bind(viewModel.successMessageProperty());
        successLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    String message = viewModel.successMessageProperty().get();
                    return message != null && !message.isBlank();
                },
                viewModel.successMessageProperty()));
        successLabel.managedProperty().bind(successLabel.visibleProperty());

        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    String message = viewModel.errorMessageProperty().get();
                    return message != null && !message.isBlank();
                },
                viewModel.errorMessageProperty()));
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
    }

    private void applyStatusDot(OrderOperationalStatus status) {
        statusDot.getStyleClass().setAll("tmp-status-dot");
        if (status != null) {
            statusDot.getStyleClass().add(status.indicatorStyleClass());
        } else {
            statusDot.getStyleClass().add("tmp-status-dot-neutral");
        }
    }
}
