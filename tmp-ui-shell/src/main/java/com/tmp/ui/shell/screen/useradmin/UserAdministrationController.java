package com.tmp.ui.shell.screen.useradmin;

import com.tmp.security.api.UserSummary;
import com.tmp.ui.shell.navigation.ViewModelAware;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
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
    private Button createUserButton;

    @FXML
    private CheckBox showDeletedCheckBox;

    @FXML
    private Label errorLabel;

    private UserAdministrationViewModel viewModel;

    @Override
    public void setViewModel(UserAdministrationViewModel viewModel) {
        this.viewModel = viewModel;
        loginColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().login().value()));
        displayNameColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().displayName().value()));
        statusColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().status()));

        userTable.setItems(viewModel.filteredUserList());
        userTable.setRowFactory(table -> createContextMenuRow());

        createUserButton.disableProperty().bind(viewModel.canCreateProperty().not());
        createUserButton.setOnAction(e -> onCreateUser());
        showDeletedCheckBox.selectedProperty().bindBidirectional(viewModel.showDeletedProperty());

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

    private TableRow<UserSummary> createContextMenuRow() {
        TableRow<UserSummary> row = new TableRow<>();
        ContextMenu menu = new ContextMenu();
        MenuItem editItem = new MenuItem("Редактировать");
        MenuItem resetItem = new MenuItem("Сбросить пароль");
        MenuItem deleteItem = new MenuItem("Удалить");
        menu.getItems().addAll(editItem, resetItem, deleteItem);

        editItem.setOnAction(e -> {
            UserSummary user = row.getItem();
            if (user != null) {
                onEditUser(user);
            }
        });
        resetItem.setOnAction(e -> {
            UserSummary user = row.getItem();
            if (user != null) {
                onResetPassword(user);
            }
        });
        deleteItem.setOnAction(e -> {
            UserSummary user = row.getItem();
            if (user != null) {
                onDeleteUser(user);
            }
        });

        row.contextMenuProperty().bind(Bindings.createObjectBinding(
                () -> row.isEmpty() ? null : menu,
                row.emptyProperty(), row.itemProperty()));

        row.setOnContextMenuRequested(event -> {
            if (!row.isEmpty()) {
                userTable.getSelectionModel().select(row.getItem());
            }
        });

        editItem.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !viewModel.canEdit(row.getItem()), row.itemProperty()));
        resetItem.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !viewModel.canReset(row.getItem()), row.itemProperty()));
        deleteItem.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !viewModel.canDeleteUser(row.getItem()), row.itemProperty()));

        return row;
    }

    private void onCreateUser() {
        UserAdministrationDialogs.showCreateDialog(
                        userTable.getScene().getWindow(), viewModel.canCreateProperty().get())
                .ifPresent(result -> viewModel.createUser(result.login(), result.displayName())
                        .ifPresent(code -> UserAdministrationDialogs.showUserCreatedDialog(
                                userTable.getScene().getWindow(), result.login(), code)));
    }

    private void onEditUser(UserSummary user) {
        UserAdministrationDialogs.showEditDialog(
                        userTable.getScene().getWindow(), viewModel.canEdit(user), user)
                .ifPresent(result -> viewModel.updateUser(user, result.login(), result.displayName()));
    }

    private void onResetPassword(UserSummary user) {
        if (!viewModel.canReset(user)) {
            return;
        }
        if (UserAdministrationDialogs.confirmPasswordReset(userTable.getScene().getWindow(), user)) {
            viewModel.requestPasswordReset(user).ifPresent(code ->
                    UserAdministrationDialogs.showPasswordResetDialog(
                            userTable.getScene().getWindow(), user.login().value(), code));
        }
    }

    private void onDeleteUser(UserSummary user) {
        if (!viewModel.canDeleteUser(user)) {
            return;
        }
        UserAdministrationDialogs.showDeleteConfirmation(userTable.getScene().getWindow(), user)
                .ifPresent(confirmed -> viewModel.deleteUser(user));
    }
}
