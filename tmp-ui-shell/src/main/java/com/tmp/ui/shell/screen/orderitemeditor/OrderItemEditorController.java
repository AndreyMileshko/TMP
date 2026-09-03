package com.tmp.ui.shell.screen.orderitemeditor;

import com.tmp.ui.shell.navigation.ViewModelAware;
import com.tmp.ui.shell.order.worklist.OperationalStatusIndicator;
import com.tmp.ui.shell.order.worklist.OrderItemOperationalStatus;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

/**
 * Order item card FXML controller. Document-driven via application facades; no Spring imports.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX Controller retains ViewModel for FXML wiring")
public final class OrderItemEditorController implements ViewModelAware<OrderItemEditorViewModel> {

    @FXML
    private Label titleLabel;

    @FXML
    private HBox statusIndicatorBox;

    @FXML
    private TextField productCodeField;

    @FXML
    private Label productCodeValueLabel;

    @FXML
    private TextField nameField;

    @FXML
    private Label nameValueLabel;

    @FXML
    private TextField commentsField;

    @FXML
    private Label commentsValueLabel;

    @FXML
    private TextField externalPositionNumberField;

    @FXML
    private Label externalPositionNumberValueLabel;

    @FXML
    private TextField orderedQuantityField;

    @FXML
    private Label orderedQuantityValueLabel;

    @FXML
    private Button saveButton;

    @FXML
    private Button openSpecificationButton;

    @FXML
    private Button cancelItemButton;

    @FXML
    private Label successLabel;

    @FXML
    private Label errorLabel;

    @Override
    public void setViewModel(OrderItemEditorViewModel viewModel) {
        titleLabel.textProperty().bind(viewModel.titleProperty());

        productCodeField.textProperty().bindBidirectional(viewModel.productCodeProperty());
        nameField.textProperty().bindBidirectional(viewModel.nameProperty());
        commentsField.textProperty().bindBidirectional(viewModel.commentsProperty());
        externalPositionNumberField
                .textProperty()
                .bindBidirectional(viewModel.externalPositionNumberProperty());
        orderedQuantityField.textProperty().bindBidirectional(viewModel.orderedQuantityProperty());

        productCodeValueLabel.textProperty().bind(viewModel.productCodeProperty());
        nameValueLabel.textProperty().bind(viewModel.nameProperty());
        commentsValueLabel.textProperty().bind(viewModel.commentsProperty());
        externalPositionNumberValueLabel.textProperty().bind(viewModel.externalPositionNumberProperty());
        orderedQuantityValueLabel.textProperty().bind(viewModel.orderedQuantityProperty());

        bindEditableChrome(viewModel);

        viewModel
                .operationalStatusProperty()
                .addListener((obs, oldValue, newValue) -> refreshStatusGraphic(newValue));
        refreshStatusGraphic(viewModel.operationalStatusProperty().get());

        saveButton.disableProperty().bind(viewModel.canSaveProperty().not());
        saveButton.visibleProperty().bind(viewModel.canSaveProperty());
        saveButton.managedProperty().bind(saveButton.visibleProperty());

        cancelItemButton.disableProperty().bind(viewModel.canCancelItemProperty().not());
        cancelItemButton.visibleProperty().bind(viewModel.canCancelItemProperty());
        cancelItemButton.managedProperty().bind(cancelItemButton.visibleProperty());

        openSpecificationButton
                .disableProperty()
                .bind(viewModel.canOpenSpecificationProperty().not());
        openSpecificationButton.visibleProperty().bind(viewModel.canOpenSpecificationProperty());
        openSpecificationButton.managedProperty().bind(openSpecificationButton.visibleProperty());

        saveButton.setOnAction(e -> viewModel.save());
        cancelItemButton.setOnAction(e -> viewModel.cancelItem());
        openSpecificationButton.setOnAction(e -> viewModel.openSpecification());

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

    private void bindEditableChrome(OrderItemEditorViewModel viewModel) {
        productCodeField.visibleProperty().bind(viewModel.fieldsEditableProperty());
        productCodeField.managedProperty().bind(productCodeField.visibleProperty());
        nameField.visibleProperty().bind(viewModel.fieldsEditableProperty());
        nameField.managedProperty().bind(nameField.visibleProperty());
        commentsField.visibleProperty().bind(viewModel.fieldsEditableProperty());
        commentsField.managedProperty().bind(commentsField.visibleProperty());
        externalPositionNumberField.visibleProperty().bind(viewModel.fieldsEditableProperty());
        externalPositionNumberField.managedProperty().bind(externalPositionNumberField.visibleProperty());
        orderedQuantityField.visibleProperty().bind(viewModel.fieldsEditableProperty());
        orderedQuantityField.managedProperty().bind(orderedQuantityField.visibleProperty());

        productCodeValueLabel.visibleProperty().bind(viewModel.fieldsEditableProperty().not());
        productCodeValueLabel.managedProperty().bind(productCodeValueLabel.visibleProperty());
        nameValueLabel.visibleProperty().bind(viewModel.fieldsEditableProperty().not());
        nameValueLabel.managedProperty().bind(nameValueLabel.visibleProperty());
        commentsValueLabel.visibleProperty().bind(viewModel.fieldsEditableProperty().not());
        commentsValueLabel.managedProperty().bind(commentsValueLabel.visibleProperty());
        externalPositionNumberValueLabel.visibleProperty().bind(viewModel.fieldsEditableProperty().not());
        externalPositionNumberValueLabel
                .managedProperty()
                .bind(externalPositionNumberValueLabel.visibleProperty());
        orderedQuantityValueLabel.visibleProperty().bind(viewModel.fieldsEditableProperty().not());
        orderedQuantityValueLabel.managedProperty().bind(orderedQuantityValueLabel.visibleProperty());
    }

    private void refreshStatusGraphic(OrderItemOperationalStatus status) {
        statusIndicatorBox.getChildren().clear();
        if (status != null) {
            statusIndicatorBox.getChildren().add(OperationalStatusIndicator.create(status));
        }
    }
}
