package com.tmp.ui.shell.screen.login;

import com.tmp.ui.shell.navigation.ViewModelAware;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"}, justification = "JavaFX ViewModel/Controller intentionally expose observable properties and retain ViewModel for FXML wiring")
/**
 * Login FXML controller. No Spring imports or annotations.
 */
public final class LoginController implements ViewModelAware<LoginViewModel> {

    @FXML
    private TextField loginField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    @FXML
    private Label errorLabel;

    private LoginViewModel viewModel;

    @Override
    public void setViewModel(LoginViewModel viewModel) {
        this.viewModel = viewModel;
        loginField.textProperty().bindBidirectional(viewModel.loginProperty());
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    String message = viewModel.errorMessageProperty().get();
                    return message != null && !message.isBlank();
                },
                viewModel.errorMessageProperty()));
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
        loginButton.setOnAction(event -> onLogin());
    }

    private void onLogin() {
        if (viewModel == null) {
            return;
        }
        char[] password = passwordField.getText().toCharArray();
        passwordField.clear();
        viewModel.submit(password);
    }
}
