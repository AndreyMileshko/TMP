package com.tmp.ui.shell.screen.main;

import com.tmp.ui.shell.navigation.ViewModelAware;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"}, justification = "JavaFX ViewModel/Controller intentionally expose observable properties and retain ViewModel for FXML wiring")
/**
 * Main window FXML controller. No Spring imports or annotations.
 */
public final class MainWindowController implements ViewModelAware<MainWindowViewModel> {

    @FXML
    private ListView<NavigationItem> navigationList;

    @FXML
    private StackPane contentArea;

    @FXML
    private Button logoutButton;

    @FXML
    private Label userAvatarLabel;

    @FXML
    private Label userLoginLabel;

    private MainWindowViewModel viewModel;

    @Override
    public void setViewModel(MainWindowViewModel viewModel) {
        this.viewModel = viewModel;
        viewModel.refreshShellState();
        navigationList.setItems(viewModel.navigationItems());
        navigationList.setFixedCellSize(42);
        navigationList.setCellFactory(list -> new NavigationListCell());
        navigationList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                viewModel.selectNavigation(selected.navigationId());
            }
        });
        viewModel.contentProperty().addListener((obs, old, root) -> {
            contentArea.getChildren().clear();
            if (root != null) {
                contentArea.getChildren().add(root);
            }
        });
        userLoginLabel.textProperty().bind(viewModel.currentUserLoginProperty());
        userAvatarLabel.textProperty().bind(viewModel.currentUserInitialProperty());
        logoutButton.setOnAction(event -> viewModel.logout());
    }
}
