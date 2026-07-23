package com.tmp.ui.shell.screen.roleadmin;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.PermissionSummary;
import com.tmp.security.api.RoleSummary;
import com.tmp.ui.shell.navigation.ViewModelAware;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"}, justification = "JavaFX ViewModel/Controller intentionally expose observable properties and retain ViewModel for FXML wiring")
/**
 * Role administration FXML controller. No Spring imports.
 */
public final class RoleAdministrationController implements ViewModelAware<RoleAdministrationViewModel> {

    @FXML
    private TableView<RoleSummary> roleTable;

    @FXML
    private TableColumn<RoleSummary, String> nameColumn;

    @FXML
    private TableColumn<RoleSummary, String> descriptionColumn;

    @FXML
    private TableColumn<RoleSummary, String> permissionCountColumn;

    @FXML
    private TextField nameField;

    @FXML
    private TextField descriptionField;

    @FXML
    private TextField assignLoginField;

    @FXML
    private Button createButton;

    @FXML
    private Button updateButton;

    @FXML
    private Button deleteButton;

    @FXML
    private Button assignButton;

    @FXML
    private Button revokeButton;

    @FXML
    private Button refreshButton;

    @FXML
    private VBox permissionBox;

    @FXML
    private Label errorLabel;

    private RoleAdministrationViewModel viewModel;

    @Override
    public void setViewModel(RoleAdministrationViewModel viewModel) {
        this.viewModel = viewModel;
        nameColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().name()));
        descriptionColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().description()));
        permissionCountColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        String.valueOf(cell.getValue().permissionIds().size())));

        roleTable.setItems(viewModel.roleList());
        roleTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            viewModel.select(selected);
            rebuildPermissionChecks();
        });

        nameField.textProperty().bindBidirectional(viewModel.nameInputProperty());
        descriptionField.textProperty().bindBidirectional(viewModel.descriptionInputProperty());
        assignLoginField.textProperty().bindBidirectional(viewModel.assignLoginInputProperty());

        createButton.disableProperty().bind(viewModel.canCreateProperty().not());
        updateButton.disableProperty().bind(viewModel.canUpdateProperty().not());
        deleteButton.disableProperty().bind(viewModel.canDeleteProperty().not());
        assignButton.disableProperty().bind(viewModel.canAssignProperty().not());
        revokeButton.disableProperty().bind(viewModel.canAssignProperty().not());

        createButton.setOnAction(e -> viewModel.createRole());
        updateButton.setOnAction(e -> viewModel.updateSelected());
        deleteButton.setOnAction(e -> viewModel.deleteSelected());
        assignButton.setOnAction(e -> viewModel.assignRoleToLogin());
        revokeButton.setOnAction(e -> viewModel.revokeRoleFromLogin());
        refreshButton.setOnAction(e -> {
            viewModel.refresh();
            rebuildPermissionChecks();
        });

        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    String message = viewModel.errorMessageProperty().get();
                    return message != null && !message.isBlank();
                },
                viewModel.errorMessageProperty()));
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
        viewModel.refresh();
        rebuildPermissionChecks();
    }

    private void rebuildPermissionChecks() {
        permissionBox.getChildren().clear();
        if (viewModel == null) {
            return;
        }
        for (PermissionSummary permission : viewModel.permissionCatalogue()) {
            CheckBox check = new CheckBox(permission.displayName() + " (" + permission.permissionId().value() + ")");
            PermissionId id = permission.permissionId();
            check.setSelected(viewModel.isPermissionGrantedOnSelected(id));
            check.setOnAction(e -> viewModel.togglePermission(id, check.isSelected()));
            permissionBox.getChildren().add(check);
        }
    }
}
