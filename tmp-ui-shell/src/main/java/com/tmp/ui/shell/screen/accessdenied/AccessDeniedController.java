package com.tmp.ui.shell.screen.accessdenied;

import com.tmp.ui.shell.navigation.ViewModelAware;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"}, justification = "JavaFX ViewModel/Controller intentionally expose observable properties and retain ViewModel for FXML wiring")
/**
 * Access Denied FXML controller. No Spring imports or annotations.
 */
public final class AccessDeniedController implements ViewModelAware<AccessDeniedViewModel> {

    @FXML
    private Label messageLabel;

    @FXML
    private Button backButton;

    @Override
    public void setViewModel(AccessDeniedViewModel viewModel) {
        messageLabel.textProperty().bind(viewModel.messageProperty());
        backButton.setOnAction(event -> viewModel.back());
    }
}
