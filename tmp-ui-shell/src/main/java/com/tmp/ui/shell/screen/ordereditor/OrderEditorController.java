package com.tmp.ui.shell.screen.ordereditor;

import com.tmp.ui.shell.navigation.ViewModelAware;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Order editor FXML controller. Document-driven; no Spring imports.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX Controller retains ViewModel for FXML wiring")
public final class OrderEditorController implements ViewModelAware<OrderEditorViewModel> {

    @FXML
    private Label titleLabel;

    @FXML
    private Label statusLabel;

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
    private Button saveDraftButton;

    @FXML
    private Button postButton;

    @FXML
    private Button approveButton;

    @FXML
    private Button cancelButton;

    @FXML
    private Button itemsButton;

    @FXML
    private Button backButton;

    @FXML
    private Label successLabel;

    @FXML
    private Label errorLabel;

    @Override
    public void setViewModel(OrderEditorViewModel viewModel) {
        titleLabel.textProperty().bind(viewModel.titleProperty());
        statusLabel.textProperty().bind(viewModel.statusTextProperty());

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

        saveDraftButton.disableProperty().bind(viewModel.canSaveDraftProperty().not());
        postButton.disableProperty().bind(viewModel.canPostProperty().not());
        approveButton.disableProperty().bind(viewModel.canApproveProperty().not());
        cancelButton.disableProperty().bind(viewModel.canCancelProperty().not());
        itemsButton.disableProperty().bind(viewModel.canOpenItemsProperty().not());

        saveDraftButton.setOnAction(e -> viewModel.saveDraft());
        postButton.setOnAction(e -> viewModel.postCurrentDocument());
        approveButton.setOnAction(e -> viewModel.approveOrder());
        cancelButton.setOnAction(e -> viewModel.cancelOrder());
        itemsButton.setOnAction(e -> viewModel.openItems());
        backButton.setOnAction(e -> viewModel.backToList());

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
}
