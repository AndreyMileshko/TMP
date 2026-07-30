package com.tmp.ui.shell.screen.roleadmin;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.PermissionSummary;
import com.tmp.security.api.RoleId;
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

    @FXML
    private Label statusLabel;

    private RoleAdministrationViewModel viewModel;
    private boolean syncingSelection;

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
            if (syncingSelection) {
                return;
            }
            if (selected == null) {
                // Ignore transient clears caused by list item replacement.
                return;
            }
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

        createButton.setOnAction(e -> {
            viewModel.createRole();
            restoreTableSelection();
            rebuildPermissionChecks();
        });
        updateButton.setOnAction(e -> {
            viewModel.updateSelected();
            restoreTableSelection();
            rebuildPermissionChecks();
        });
        deleteButton.setOnAction(e -> {
            viewModel.deleteSelected();
            restoreTableSelection();
            rebuildPermissionChecks();
        });
        assignButton.setOnAction(e -> viewModel.assignRoleToLogin());
        revokeButton.setOnAction(e -> viewModel.revokeRoleFromLogin());
        refreshButton.setOnAction(e -> {
            viewModel.refresh();
            restoreTableSelection();
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

        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
        statusLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    String message = viewModel.statusMessageProperty().get();
                    return message != null && !message.isBlank();
                },
                viewModel.statusMessageProperty()));
        statusLabel.managedProperty().bind(statusLabel.visibleProperty());

        viewModel.refresh();
        restoreTableSelection();
        rebuildPermissionChecks();
    }

    static String permissionLabel(PermissionSummary permission) {
        return permission.displayName() + " (" + permission.permissionId().value() + ")";
    }

    private void rebuildPermissionChecks() {
        permissionBox.getChildren().clear();
        if (viewModel == null) {
            return;
        }
        for (PermissionSummary permission : viewModel.permissionCatalogue()) {
            CheckBox check = new CheckBox(permissionLabel(permission));
            PermissionId id = permission.permissionId();
            check.setFocusTraversable(false);
            check.setSelected(viewModel.isPermissionGrantedOnSelected(id));
            check.setOnAction(e -> onPermissionToggled(check, id));
            permissionBox.getChildren().add(check);
        }
    }

    private void onPermissionToggled(CheckBox check, PermissionId id) {
        boolean intended = check.isSelected();
        RoleId before = viewModel.selectedRoleId();
        viewModel.togglePermission(id, intended);
        restoreTableSelection();
        // Sync checkbox from ViewModel (handles failure / actual grant state).
        check.setSelected(viewModel.isPermissionGrantedOnSelected(id));
        // Refresh labels/counts for the selected role without clearing selection.
        syncingSelection = true;
        try {
            rebuildPermissionChecks();
        } finally {
            syncingSelection = false;
        }
        restoreTableSelection();
        if (before != null
                && (viewModel.selectedRoleId() == null
                        || !before.equals(viewModel.selectedRoleId())
                        || roleTable.getSelectionModel().getSelectedItem() == null
                        || !before.equals(roleTable.getSelectionModel().getSelectedItem().id()))) {
            restoreTableSelection();
        }
    }

    private void restoreTableSelection() {
        if (viewModel == null || roleTable == null) {
            return;
        }
        RoleId selectedId = viewModel.selectedRoleId();
        if (selectedId == null) {
            syncingSelection = true;
            try {
                roleTable.getSelectionModel().clearSelection();
            } finally {
                syncingSelection = false;
            }
            return;
        }
        roleTable.getItems().stream()
                .filter(role -> role.id().equals(selectedId))
                .findFirst()
                .ifPresent(role -> {
                    syncingSelection = true;
                    try {
                        roleTable.getSelectionModel().select(role);
                        roleTable.getFocusModel().focus(roleTable.getItems().indexOf(role));
                    } finally {
                        syncingSelection = false;
                    }
                });
    }
}
