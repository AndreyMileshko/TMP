package com.tmp.ui.shell.screen.roleadmin;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.PermissionSummary;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.RoleSummary;
import com.tmp.ui.shell.navigation.ViewModelAware;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"}, justification = "JavaFX ViewModel/Controller intentionally expose observable properties and retain ViewModel for FXML wiring")
/**
 * Role administration FXML controller. No Spring imports.
 */
public final class RoleAdministrationController implements ViewModelAware<RoleAdministrationViewModel> {

    @FXML
    private VBox root;

    @FXML
    private SplitPane roleSplitPane;

    @FXML
    private TableView<RoleSummary> roleTable;

    @FXML
    private TableColumn<RoleSummary, String> nameColumn;

    @FXML
    private TableColumn<RoleSummary, String> descriptionColumn;

    @FXML
    private TableColumn<RoleSummary, String> permissionCountColumn;

    @FXML
    private Button createRoleButton;

    @FXML
    private StackPane detailStack;

    @FXML
    private VBox detailEmptyState;

    @FXML
    private ScrollPane detailScroll;

    @FXML
    private Label selectedRoleLabel;

    @FXML
    private TextField permissionSearchField;

    @FXML
    private VBox permissionBox;

    @FXML
    private TextField assignLoginField;

    @FXML
    private Button assignButton;

    @FXML
    private Button revokeButton;

    @FXML
    private Label errorLabel;

    private RoleAdministrationViewModel viewModel;
    private boolean syncingSelection;
    private String permissionSearchFilter = "";

    @Override
    public void setViewModel(RoleAdministrationViewModel viewModel) {
        this.viewModel = viewModel;
        loadScreenStylesheet();
        roleTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        roleTable.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.SINGLE);
        roleTable.setPlaceholder(createEmptyState());
        nameColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().name()));
        descriptionColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().description()));
        permissionCountColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        String.valueOf(cell.getValue().permissionIds().size())));

        roleTable.setItems(viewModel.roleList());
        roleTable.setRowFactory(table -> createContextMenuRow());
        roleTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (syncingSelection) {
                return;
            }
            if (selected == null) {
                return;
            }
            viewModel.select(selected);
            updateDetailPanel();
        });

        createRoleButton.visibleProperty().bind(viewModel.canCreateProperty());
        createRoleButton.managedProperty().bind(viewModel.canCreateProperty());
        createRoleButton.setOnAction(e -> onCreateRole());

        assignLoginField.textProperty().bindBidirectional(viewModel.assignLoginInputProperty());
        assignButton.visibleProperty().bind(viewModel.canAssignRoleProperty());
        assignButton.managedProperty().bind(viewModel.canAssignRoleProperty());
        revokeButton.visibleProperty().bind(viewModel.canAssignRoleProperty());
        revokeButton.managedProperty().bind(viewModel.canAssignRoleProperty());
        assignButton.disableProperty().bind(viewModel.hasSelectedRoleProperty().not());
        revokeButton.disableProperty().bind(viewModel.hasSelectedRoleProperty().not());
        assignButton.setOnAction(e -> viewModel.assignRoleToLogin());
        revokeButton.setOnAction(e -> viewModel.revokeRoleFromLogin());

        permissionSearchField.textProperty().addListener((obs, old, value) -> {
            permissionSearchFilter = value == null ? "" : value.trim().toLowerCase();
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
        restoreTableSelection();
        updateDetailPanel();
    }

    private void loadScreenStylesheet() {
        var resource = getClass().getResource("RoleAdministrationScreen.css");
        if (resource != null && root != null) {
            String url = resource.toExternalForm();
            if (!root.getStylesheets().contains(url)) {
                root.getStylesheets().add(url);
            }
        }
    }

    private static VBox createEmptyState() {
        Label title = new Label("Нет ролей для отображения");
        title.getStyleClass().add("tmp-empty-state-title");
        Label hint = new Label("Создайте новую роль, чтобы начать.");
        hint.getStyleClass().add("tmp-empty-state-hint");
        hint.setWrapText(true);
        VBox emptyState = new VBox(8, title, hint);
        emptyState.getStyleClass().add("tmp-empty-state");
        emptyState.setAlignment(Pos.CENTER);
        return emptyState;
    }

    private TableRow<RoleSummary> createContextMenuRow() {
        TableRow<RoleSummary> row = new TableRow<>();
        ContextMenu menu = new ContextMenu();
        MenuItem editItem = new MenuItem("Редактировать");
        SeparatorMenuItem separator = new SeparatorMenuItem();
        MenuItem deleteItem = new MenuItem("Удалить");
        deleteItem.getStyleClass().add("tmp-menu-item-danger");
        menu.getItems().addAll(editItem, separator, deleteItem);

        editItem.visibleProperty().bind(viewModel.canUpdateProperty());
        deleteItem.visibleProperty().bind(viewModel.canDeleteProperty());
        separator.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> editItem.isVisible() && deleteItem.isVisible(),
                editItem.visibleProperty(),
                deleteItem.visibleProperty()));

        editItem.setOnAction(e -> {
            RoleSummary role = row.getItem();
            if (role != null) {
                onEditRole(role);
            }
        });
        deleteItem.setOnAction(e -> {
            RoleSummary role = row.getItem();
            if (role != null) {
                onDeleteRole(role);
            }
        });

        row.contextMenuProperty().bind(Bindings.createObjectBinding(
                () -> {
                    if (row.isEmpty()) {
                        return null;
                    }
                    if (!editItem.isVisible() && !deleteItem.isVisible()) {
                        return null;
                    }
                    return menu;
                },
                row.emptyProperty(),
                editItem.visibleProperty(),
                deleteItem.visibleProperty()));

        row.setOnContextMenuRequested(event -> {
            if (!row.isEmpty()) {
                roleTable.getSelectionModel().select(row.getItem());
            }
        });

        return row;
    }

    private void onCreateRole() {
        RoleAdministrationDialogs.showCreateDialog(
                        roleTable.getScene().getWindow(), viewModel.canCreateProperty().get())
                .ifPresent(result -> {
                    viewModel.createRole(result.name(), result.description());
                    restoreTableSelection();
                    updateDetailPanel();
                });
    }

    private void onEditRole(RoleSummary role) {
        RoleAdministrationDialogs.showEditDialog(
                        roleTable.getScene().getWindow(), viewModel.canUpdateProperty().get(), role)
                .ifPresent(result -> {
                    viewModel.updateRole(role, result.name(), result.description());
                    restoreTableSelection();
                    updateDetailPanel();
                });
    }

    private void onDeleteRole(RoleSummary role) {
        if (!viewModel.canDeleteProperty().get()) {
            return;
        }
        if (RoleAdministrationDialogs.showDeleteConfirmation(roleTable.getScene().getWindow(), role)) {
            viewModel.deleteRole(role);
            restoreTableSelection();
            updateDetailPanel();
        }
    }

    private void updateDetailPanel() {
        RoleSummary selected = viewModel.selectedRole();
        boolean hasSelection = selected != null;
        detailEmptyState.setVisible(!hasSelection);
        detailScroll.setVisible(hasSelection);
        if (hasSelection) {
            selectedRoleLabel.setText("Роль: " + selected.name());
            rebuildPermissionChecks();
        } else {
            permissionBox.getChildren().clear();
        }
    }

    static String permissionDisplayName(PermissionSummary permission) {
        return permission.displayName();
    }

    static String permissionTechnicalId(PermissionSummary permission) {
        return permission.permissionId().value();
    }

    private void rebuildPermissionChecks() {
        permissionBox.getChildren().clear();
        if (viewModel == null || viewModel.selectedRoleId() == null) {
            return;
        }
        boolean canModify = viewModel.canManageRolePermissionsProperty().get();
        for (PermissionSummary permission : viewModel.permissionCatalogue()) {
            if (!matchesPermissionSearch(permission)) {
                continue;
            }
            CheckBox check = createPermissionCheckBox(permission, canModify);
            permissionBox.getChildren().add(check);
        }
    }

    private boolean matchesPermissionSearch(PermissionSummary permission) {
        if (permissionSearchFilter.isEmpty()) {
            return true;
        }
        return permission.displayName().toLowerCase().contains(permissionSearchFilter)
                || permission.permissionId().value().toLowerCase().contains(permissionSearchFilter);
    }

    private CheckBox createPermissionCheckBox(PermissionSummary permission, boolean canModify) {
        Label displayName = new Label(permissionDisplayName(permission));
        Label technicalId = new Label(permissionTechnicalId(permission));
        technicalId.getStyleClass().addAll("tmp-text-muted", "role-admin-permission-id");
        VBox labels = new VBox(2, displayName, technicalId);
        HBox row = new HBox(8, labels);
        row.getStyleClass().add("role-admin-permission-row");
        row.setAlignment(Pos.CENTER_LEFT);

        CheckBox check = new CheckBox();
        check.setGraphic(row);
        check.setFocusTraversable(false);
        PermissionId id = permission.permissionId();
        check.setSelected(viewModel.isPermissionGrantedOnSelected(id));
        check.setDisable(!canModify);
        if (canModify) {
            check.setOnAction(e -> onPermissionToggled(check, id));
        }
        return check;
    }

    private void onPermissionToggled(CheckBox check, PermissionId id) {
        boolean intended = check.isSelected();
        RoleId before = viewModel.selectedRoleId();
        viewModel.togglePermission(id, intended);
        restoreTableSelection();
        check.setSelected(viewModel.isPermissionGrantedOnSelected(id));
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
        updateDetailPanel();
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
