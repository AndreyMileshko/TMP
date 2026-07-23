package com.tmp.ui.shell.screen.useradmin;

import com.tmp.security.api.UserSummary;
import com.tmp.ui.shell.navigation.ViewModelAware;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"}, justification = "JavaFX ViewModel/Controller intentionally expose observable properties and retain ViewModel for FXML wiring")
/**
 * User administration FXML controller. No Spring imports.
 */
public final class UserAdministrationController implements ViewModelAware<UserAdministrationViewModel> {

    @FXML
    private TableView<UserSummary> userTable;

    @FXML
    private TableColumn<UserSummary, String> loginColumn;

    @FXML
    private TableColumn<UserSummary, String> displayNameColumn;

    @FXML
    private TableColumn<UserSummary, String> statusColumn;

    @FXML
    private TextField loginField;

    @FXML
    private TextField displayNameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button createButton;

    @FXML
    private Button updateButton;

    @FXML
    private Button deleteButton;

    @FXML
    private Button resetPasswordButton;

    @FXML
    private Button refreshButton;

    @FXML
    private Label errorLabel;

    @Override
    public void setViewModel(UserAdministrationViewModel viewModel) {
        loginColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().login().value()));
        displayNameColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().displayName().value()));
        statusColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().status()));

        userTable.setItems(viewModel.userList());
        userTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) ->
                viewModel.select(selected));

        loginField.textProperty().bindBidirectional(viewModel.loginInputProperty());
        displayNameField.textProperty().bindBidirectional(viewModel.displayNameInputProperty());
        passwordField.textProperty().bindBidirectional(viewModel.passwordInputProperty());

        createButton.disableProperty().bind(viewModel.canCreateProperty().not());
        updateButton.disableProperty().bind(viewModel.canUpdateProperty().not());
        deleteButton.disableProperty().bind(viewModel.canDeleteProperty().not());
        resetPasswordButton.disableProperty().bind(viewModel.canResetPasswordProperty().not());

        createButton.setOnAction(e -> viewModel.createUser());
        updateButton.setOnAction(e -> viewModel.updateSelected());
        deleteButton.setOnAction(e -> viewModel.deleteSelected());
        resetPasswordButton.setOnAction(e -> viewModel.resetPasswordSelected());
        refreshButton.setOnAction(e -> viewModel.refresh());

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
