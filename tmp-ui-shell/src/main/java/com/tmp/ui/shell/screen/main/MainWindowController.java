package com.tmp.ui.shell.screen.main;

import com.tmp.ui.shell.navigation.ViewModelAware;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
    private Button historyBackButton;

    @FXML
    private Button historyForwardButton;

    @FXML
    private Label userAvatarLabel;

    @FXML
    private Label userLoginLabel;

    private MainWindowViewModel viewModel;
    private boolean acceleratorsInstalled;

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
        historyBackButton.disableProperty().bind(viewModel.canGoBackProperty().not());
        historyForwardButton.disableProperty().bind(viewModel.canGoForwardProperty().not());
        historyBackButton.setOnAction(event -> viewModel.goBack());
        historyForwardButton.setOnAction(event -> viewModel.goForward());
        installAccelerators();
    }

    private void installAccelerators() {
        if (acceleratorsInstalled) {
            return;
        }
        Runnable attach = () -> {
            Scene scene = contentArea.getScene();
            if (scene == null || acceleratorsInstalled) {
                return;
            }
            scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleHistoryKeys);
            acceleratorsInstalled = true;
        };
        if (contentArea.getScene() != null) {
            attach.run();
            return;
        }
        contentArea.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) {
                attach.run();
            }
        });
    }

    private void handleHistoryKeys(KeyEvent event) {
        if (!event.isAltDown()) {
            return;
        }
        if (event.getCode() == KeyCode.LEFT) {
            viewModel.goBack();
            event.consume();
        } else if (event.getCode() == KeyCode.RIGHT) {
            viewModel.goForward();
            event.consume();
        }
    }
}
