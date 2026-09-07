package com.tmp.ui.shell.screen.roleadmin;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.PermissionSummary;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.RoleSummary;
import com.tmp.security.api.UserSummary;
import com.tmp.ui.shell.navigation.ViewModelAware;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Role administration FXML controller. No Spring imports.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX Controller retains ViewModel for FXML wiring")
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
    private VBox detailContent;
    @FXML
    private Label selectedRoleLabel;
    @FXML
    private Label selectedRoleDescriptionLabel;
    @FXML
    private VBox assignmentSection;
    @FXML
    private TextField userSearchField;
    @FXML
    private ListView<UserSummary> userSearchResults;
    @FXML
    private CheckBox roleAssignedCheck;
    @FXML
    private Button applyAssignmentButton;
    @FXML
    private TextField permissionSearchField;
    @FXML
    private TreeView<PermissionTreeNode> permissionTree;
    @FXML
    private Button applyPermissionsButton;
    @FXML
    private Label statusLabel;
    @FXML
    private Label errorLabel;

    private RoleAdministrationViewModel viewModel;
    private boolean syncingSelection;
    private boolean syncingPermissionTree;
    private boolean syncingAssignment;
    private String permissionSearchFilter = "";
    private final PauseTransition userSearchDebounce = new PauseTransition(Duration.millis(250));
    private final Map<String, Boolean> groupExpandedState = new HashMap<>();

    @Override
    public void setViewModel(RoleAdministrationViewModel viewModel) {
        this.viewModel = viewModel;
        loadScreenStylesheet();
        roleTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        roleTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
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
            if (syncingSelection || selected == null) {
                return;
            }
            if (!trySelectRole(selected)) {
                restoreTableSelection();
            } else {
                updateDetailPanel();
            }
        });

        createRoleButton.visibleProperty().bind(viewModel.canCreateProperty());
        createRoleButton.managedProperty().bind(viewModel.canCreateProperty());
        createRoleButton.setOnAction(e -> onCreateRole());

        assignmentSection.visibleProperty().bind(viewModel.canUseUserAssignmentProperty());
        assignmentSection.managedProperty().bind(viewModel.canUseUserAssignmentProperty());
        userSearchField.textProperty().bindBidirectional(viewModel.userSearchQueryProperty());
        userSearchDebounce.setOnFinished(e -> {
            if (viewModel.selectedUserProperty().get() != null) {
                return;
            }
            viewModel.searchUsers(userSearchField.getText());
        });
        userSearchField.textProperty().addListener((obs, old, value) -> {
            if (syncingAssignment) {
                return;
            }
            UserSummary current = viewModel.selectedUserProperty().get();
            if (current != null) {
                String label = RoleAdministrationViewModel.formatUserLabel(current);
                if (value == null || !value.equals(label)) {
                    viewModel.selectUser(null);
                } else {
                    return;
                }
            }
            userSearchDebounce.playFromStart();
        });
        userSearchResults.setItems(viewModel.userSearchResults());
        userSearchResults.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(UserSummary item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : RoleAdministrationViewModel.formatUserLabel(item));
            }
        });
        userSearchResults.getSelectionModel().selectedItemProperty().addListener((obs, old, user) -> {
            if (user != null) {
                syncingAssignment = true;
                try {
                    viewModel.selectUser(user);
                } finally {
                    syncingAssignment = false;
                }
                hideUserResults();
                syncAssignmentControls();
            }
        });
        viewModel.userSearchResults().addListener((javafx.collections.ListChangeListener<UserSummary>) c -> {
            boolean show = !viewModel.userSearchResults().isEmpty()
                    && viewModel.selectedUserProperty().get() == null;
            userSearchResults.setVisible(show);
            userSearchResults.setManaged(show);
        });
        roleAssignedCheck.selectedProperty().addListener((obs, old, selected) -> {
            if (syncingAssignment) {
                return;
            }
            viewModel.desiredRoleAssignedProperty().set(Boolean.TRUE.equals(selected));
        });
        applyAssignmentButton.disableProperty().bind(
                viewModel.assignmentDirtyProperty().not()
                        .or(viewModel.hasSelectedRoleProperty().not())
                        .or(viewModel.selectedUserProperty().isNull()));
        applyAssignmentButton.setOnAction(e -> {
            viewModel.applyRoleAssignment();
            syncAssignmentControls();
        });

        permissionSearchField.textProperty().addListener((obs, old, value) -> {
            permissionSearchFilter = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            rebuildPermissionTree();
        });
        permissionTree.setShowRoot(false);
        permissionTree.setCellFactory(tree -> new PermissionCheckBoxTreeCell());
        applyPermissionsButton.visibleProperty().bind(viewModel.canManageRolePermissionsProperty());
        applyPermissionsButton.managedProperty().bind(viewModel.canManageRolePermissionsProperty());
        applyPermissionsButton.disableProperty().bind(
                viewModel.permissionsDirtyProperty().not().or(viewModel.hasSelectedRoleProperty().not()));
        applyPermissionsButton.setOnAction(e -> {
            viewModel.applyPermissions();
            restoreTableSelection();
            updateDetailPanel();
        });
        viewModel.canManageRolePermissionsProperty().addListener((obs, old, value) -> rebuildPermissionTree());

        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
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

    private boolean trySelectRole(RoleSummary selected) {
        if (viewModel.select(selected)) {
            return true;
        }
        Optional<ButtonType> decision = confirmUnsavedPermissions();
        if (decision.isEmpty() || decision.get().getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) {
            return false;
        }
        if (decision.get().getButtonData() == ButtonBar.ButtonData.YES) {
            viewModel.applyPermissions();
            if (viewModel.hasUnsavedPermissionChanges()) {
                return false;
            }
            return viewModel.select(selected, true);
        }
        return viewModel.select(selected, true);
    }

    private Optional<ButtonType> confirmUnsavedPermissions() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Несохранённые изменения");
        alert.setHeaderText(RoleAdministrationMessages.UNSAVED_PERMISSIONS);
        ButtonType save = new ButtonType("Сохранить", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Не сохранять", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(save, discard, cancel);
        return alert.showAndWait();
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
        detailEmptyState.setManaged(!hasSelection);
        detailContent.setVisible(hasSelection);
        detailContent.setManaged(hasSelection);
        if (hasSelection) {
            selectedRoleLabel.setText("Роль: " + selected.name());
            String description = selected.description();
            boolean hasDescription = description != null && !description.isBlank();
            selectedRoleDescriptionLabel.setText(hasDescription ? description : "");
            selectedRoleDescriptionLabel.setVisible(hasDescription);
            selectedRoleDescriptionLabel.setManaged(hasDescription);
            syncAssignmentControls();
            rebuildPermissionTree();
        } else {
            permissionTree.setRoot(null);
        }
    }

    private void syncAssignmentControls() {
        syncingAssignment = true;
        try {
            UserSummary user = viewModel.selectedUserProperty().get();
            if (user != null) {
                userSearchField.setText(RoleAdministrationViewModel.formatUserLabel(user));
            }
            roleAssignedCheck.setSelected(viewModel.desiredRoleAssignedProperty().get());
            boolean enabled = viewModel.canUseUserAssignmentProperty().get()
                    && viewModel.selectedUserProperty().get() != null;
            roleAssignedCheck.setDisable(!enabled);
            hideUserResults();
        } finally {
            syncingAssignment = false;
        }
    }

    private void hideUserResults() {
        userSearchResults.setVisible(false);
        userSearchResults.setManaged(false);
    }

    private void rebuildPermissionTree() {
        if (viewModel == null || viewModel.selectedRoleId() == null) {
            permissionTree.setRoot(null);
            return;
        }
        rememberExpandedState();
        syncingPermissionTree = true;
        try {
            CheckBoxTreeItem<PermissionTreeNode> root = new CheckBoxTreeItem<>(PermissionTreeNode.root());
            root.setExpanded(true);
            boolean canModify = viewModel.canManageRolePermissionsProperty().get();
            boolean filtering = !permissionSearchFilter.isEmpty();
            List<PermissionNamespaceGroup> groups =
                    PermissionNamespaceGroup.group(viewModel.permissionCatalogue());
            for (PermissionNamespaceGroup group : groups) {
                List<PermissionSummary> visibleLeaves = new ArrayList<>();
                for (PermissionSummary permission : group.permissions()) {
                    if (matchesPermissionSearch(permission)) {
                        visibleLeaves.add(permission);
                    }
                }
                if (visibleLeaves.isEmpty()) {
                    continue;
                }
                CheckBoxTreeItem<PermissionTreeNode> groupItem =
                        new CheckBoxTreeItem<>(PermissionTreeNode.group(group));
                groupItem.setIndependent(true);
                boolean expanded = filtering
                        || groupExpandedState.getOrDefault(group.namespace(), false);
                groupItem.setExpanded(expanded);
                for (PermissionSummary permission : visibleLeaves) {
                    CheckBoxTreeItem<PermissionTreeNode> leaf =
                            new CheckBoxTreeItem<>(PermissionTreeNode.leaf(permission));
                    leaf.setIndependent(true);
                    leaf.setSelected(viewModel.isPermissionDesired(permission.permissionId()));
                    if (canModify) {
                        leaf.selectedProperty().addListener((obs, old, selected) -> {
                            if (syncingPermissionTree) {
                                return;
                            }
                            viewModel.setDesiredPermission(
                                    permission.permissionId(), Boolean.TRUE.equals(selected));
                            refreshGroupCheckState(groupItem);
                        });
                    } else {
                        installReadOnlyGuard(leaf);
                    }
                    groupItem.getChildren().add(leaf);
                }
                refreshGroupCheckState(groupItem);
                if (canModify) {
                    groupItem.selectedProperty().addListener((obs, old, selected) -> {
                        if (syncingPermissionTree) {
                            return;
                        }
                        onGroupCheckClicked(groupItem, Boolean.TRUE.equals(selected));
                    });
                } else {
                    installReadOnlyGuard(groupItem);
                }
                root.getChildren().add(groupItem);
            }
            permissionTree.setRoot(root);
        } finally {
            syncingPermissionTree = false;
        }
    }

    private void onGroupCheckClicked(CheckBoxTreeItem<PermissionTreeNode> groupItem, boolean selected) {
        syncingPermissionTree = true;
        try {
            List<PermissionId> ids = new ArrayList<>();
            for (TreeItem<PermissionTreeNode> child : groupItem.getChildren()) {
                PermissionTreeNode node = child.getValue();
                if (node != null && node.permission() != null) {
                    ids.add(node.permission().permissionId());
                    if (child instanceof CheckBoxTreeItem<?> checkLeaf) {
                        checkLeaf.setSelected(selected);
                        checkLeaf.setIndeterminate(false);
                    }
                }
            }
            viewModel.setDesiredPermissionsInGroup(ids, selected);
            groupItem.setIndeterminate(false);
            groupItem.setSelected(selected);
        } finally {
            syncingPermissionTree = false;
        }
    }

    private void installReadOnlyGuard(CheckBoxTreeItem<PermissionTreeNode> item) {
        item.selectedProperty().addListener((obs, old, selected) -> {
            if (syncingPermissionTree) {
                return;
            }
            syncingPermissionTree = true;
            try {
                item.setSelected(Boolean.TRUE.equals(old));
            } finally {
                syncingPermissionTree = false;
            }
        });
        item.indeterminateProperty().addListener((obs, old, value) -> {
            if (syncingPermissionTree) {
                return;
            }
            syncingPermissionTree = true;
            try {
                item.setIndeterminate(Boolean.TRUE.equals(old));
            } finally {
                syncingPermissionTree = false;
            }
        });
    }

    private boolean canEditPermissionTree() {
        return viewModel != null && viewModel.canManageRolePermissionsProperty().get();
    }

    /**
     * CheckBoxTreeCell that keeps disclosure/expand usable while making checkboxes read-only when
     * the user lacks {@code PERMISSIONS_ASSIGN}. Does not disable the TreeView itself.
     */
    private final class PermissionCheckBoxTreeCell extends CheckBoxTreeCell<PermissionTreeNode> {
        private PermissionCheckBoxTreeCell() {
            addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.SPACE && !canEditPermissionTree()) {
                    event.consume();
                }
            });
        }

        @Override
        public void updateItem(PermissionTreeNode item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().remove("permission-tree-readonly");
            if (empty || item == null) {
                return;
            }
            boolean editable = canEditPermissionTree();
            if (getGraphic() instanceof CheckBox checkBox) {
                checkBox.setDisable(!editable);
                checkBox.setMouseTransparent(!editable);
                checkBox.setFocusTraversable(editable);
                if (!editable) {
                    checkBox.setOpacity(1.0);
                }
            }
            if (!editable) {
                getStyleClass().add("permission-tree-readonly");
            }
        }
    }

    private void refreshGroupCheckState(CheckBoxTreeItem<PermissionTreeNode> groupItem) {
        int selectedCount = 0;
        int total = groupItem.getChildren().size();
        for (TreeItem<PermissionTreeNode> child : groupItem.getChildren()) {
            if (child instanceof CheckBoxTreeItem<?> checkLeaf && checkLeaf.isSelected()) {
                selectedCount++;
            }
        }
        boolean wasSyncing = syncingPermissionTree;
        syncingPermissionTree = true;
        try {
            if (selectedCount == 0) {
                groupItem.setIndeterminate(false);
                groupItem.setSelected(false);
            } else if (selectedCount == total) {
                groupItem.setIndeterminate(false);
                groupItem.setSelected(true);
            } else {
                groupItem.setSelected(false);
                groupItem.setIndeterminate(true);
            }
        } finally {
            syncingPermissionTree = wasSyncing;
        }
    }

    private void rememberExpandedState() {
        TreeItem<PermissionTreeNode> root = permissionTree.getRoot();
        if (root == null) {
            return;
        }
        for (TreeItem<PermissionTreeNode> child : root.getChildren()) {
            PermissionTreeNode node = child.getValue();
            if (node != null && node.group() != null) {
                groupExpandedState.put(node.group().namespace(), child.isExpanded());
            }
        }
    }

    private boolean matchesPermissionSearch(PermissionSummary permission) {
        if (permissionSearchFilter.isEmpty()) {
            return true;
        }
        return permission.displayName().toLowerCase(Locale.ROOT).contains(permissionSearchFilter)
                || permission.permissionId().value().toLowerCase(Locale.ROOT).contains(permissionSearchFilter);
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

    /** Tree node payload: invisible root, namespace group, or permission leaf. */
    static final class PermissionTreeNode {
        private final PermissionNamespaceGroup group;
        private final PermissionSummary permission;
        private final String label;

        private PermissionTreeNode(PermissionNamespaceGroup group, PermissionSummary permission, String label) {
            this.group = group;
            this.permission = permission;
            this.label = label;
        }

        static PermissionTreeNode root() {
            return new PermissionTreeNode(null, null, "root");
        }

        static PermissionTreeNode group(PermissionNamespaceGroup group) {
            return new PermissionTreeNode(group, null, group.displayName());
        }

        static PermissionTreeNode leaf(PermissionSummary permission) {
            return new PermissionTreeNode(null, permission, permission.displayName());
        }

        PermissionNamespaceGroup group() {
            return group;
        }

        PermissionSummary permission() {
            return permission;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
