package com.tmp.ui.shell.screen.orderitemeditor;

import com.tmp.ui.shell.navigation.ViewModelAware;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Order item / revision editor FXML controller. Document-driven; no Spring imports.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX Controller retains ViewModel for FXML wiring")
public final class OrderItemEditorController implements ViewModelAware<OrderItemEditorViewModel> {

    @FXML
    private Label titleLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private TextField productCodeField;

    @FXML
    private TextField nameField;

    @FXML
    private TextField commentsField;

    @FXML
    private TextField externalPositionNumberField;

    @FXML
    private TextField orderedQuantityField;

    @FXML
    private Label activeRevisionLabel;

    @FXML
    private Label draftRevisionLabel;

    @FXML
    private Label draftSpecLineCountLabel;

    @FXML
    private TextField copyFromRevisionField;

    @FXML
    private Button saveCommercialButton;

    @FXML
    private Button postCommercialButton;

    @FXML
    private Button cancelItemButton;

    @FXML
    private Button createRevisionButton;

    @FXML
    private Button saveRevisionButton;

    @FXML
    private Button postRevisionButton;

    @FXML
    private Button approveRevisionButton;

    @FXML
    private Button openActiveSpecificationButton;

    @FXML
    private Button openDraftSpecificationButton;

    @FXML
    private Label successLabel;

    @FXML
    private Label errorLabel;

    @Override
    public void setViewModel(OrderItemEditorViewModel viewModel) {
        titleLabel.textProperty().bind(viewModel.titleProperty());
        statusLabel.textProperty().bind(viewModel.statusTextProperty());

        productCodeField.textProperty().bindBidirectional(viewModel.productCodeProperty());
        nameField.textProperty().bindBidirectional(viewModel.nameProperty());
        commentsField.textProperty().bindBidirectional(viewModel.commentsProperty());
        externalPositionNumberField
                .textProperty()
                .bindBidirectional(viewModel.externalPositionNumberProperty());
        orderedQuantityField.textProperty().bindBidirectional(viewModel.orderedQuantityProperty());
        copyFromRevisionField.textProperty().bindBidirectional(viewModel.copyFromRevisionProperty());

        activeRevisionLabel.textProperty().bind(viewModel.activeRevisionTextProperty());
        draftRevisionLabel.textProperty().bind(viewModel.draftRevisionTextProperty());
        draftSpecLineCountLabel.textProperty().bind(viewModel.draftSpecLineCountTextProperty());

        productCodeField.disableProperty().bind(viewModel.commercialEditableProperty().not());
        nameField.disableProperty().bind(viewModel.commercialEditableProperty().not());
        commentsField.disableProperty().bind(viewModel.commercialEditableProperty().not());
        externalPositionNumberField.disableProperty().bind(viewModel.commercialEditableProperty().not());
        orderedQuantityField.disableProperty().bind(viewModel.quantityEditableProperty().not());

        saveCommercialButton.disableProperty().bind(viewModel.canSaveCommercialDraftProperty().not());
        postCommercialButton.disableProperty().bind(viewModel.canPostCommercialProperty().not());
        cancelItemButton.disableProperty().bind(viewModel.canCancelItemProperty().not());
        createRevisionButton.disableProperty().bind(viewModel.canCreateRevisionProperty().not());
        saveRevisionButton.disableProperty().bind(viewModel.canSaveRevisionDraftProperty().not());
        postRevisionButton.disableProperty().bind(viewModel.canPostRevisionUpdateProperty().not());
        approveRevisionButton.disableProperty().bind(viewModel.canApproveRevisionProperty().not());
        openActiveSpecificationButton
                .disableProperty()
                .bind(viewModel.canOpenActiveSpecificationProperty().not());
        openDraftSpecificationButton
                .disableProperty()
                .bind(viewModel.canOpenDraftSpecificationProperty().not());

        saveCommercialButton.setOnAction(e -> viewModel.saveCommercialDraft());
        postCommercialButton.setOnAction(e -> viewModel.postCommercialDocument());
        cancelItemButton.setOnAction(e -> viewModel.cancelItem());
        createRevisionButton.setOnAction(e -> viewModel.createNextRevision());
        saveRevisionButton.setOnAction(e -> viewModel.saveRevisionQuantityDraft());
        postRevisionButton.setOnAction(e -> viewModel.postRevisionUpdate());
        approveRevisionButton.setOnAction(e -> viewModel.approveDraftRevision());
        openActiveSpecificationButton.setOnAction(e -> viewModel.openActiveSpecification());
        openDraftSpecificationButton.setOnAction(e -> viewModel.openDraftSpecification());

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
