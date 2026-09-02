package com.tmp.ui.shell.screen.roleadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.PermissionSummary;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.RoleSummary;
import com.tmp.security.api.PasswordResetResult;
import com.tmp.security.api.UserCreationResult;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserSummary;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.api.RoleInUseException;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import com.tmp.ui.shell.theme.TmpTheme;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RoleAdministrationControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void loadsStandardRolesScreenStructure() throws Exception {
        RoleAdministrationViewModel viewModel = new RoleAdministrationViewModel(
                new EmptyRoles(), new EmptyUsers(), new AllowAll());
        Parent root = loadScreen(viewModel, 1024, 700);

        assertTrue(root.getStyleClass().contains("tmp-screen"));
        assertNotNull(findByStyleClass(root, "tmp-screen-title"));
        assertEquals("Роли", labelText(findByStyleClass(root, "tmp-screen-title")));
        assertNotNull(findByStyleClass(root, "tmp-screen-subtitle"));
        assertEquals(
                "Управление ролями и правами доступа",
                labelText(findByStyleClass(root, "tmp-screen-subtitle")));
        assertNotNull(root.lookup("#createRoleButton"));
        assertNotNull(root.lookup("#roleTable"));
        assertNotNull(root.lookup("#detailEmptyState"));
        assertNotNull(root.lookup("#permissionSearchField"));
        Label errorLabel = (Label) root.lookup("#errorLabel");
        assertNotNull(errorLabel);
        assertTrue(errorLabel.getStyleClass().contains("tmp-message-error"));
        Button createButton = (Button) root.lookup("#createRoleButton");
        assertEquals("Создать роль", createButton.getText());
        assertTrue(createButton.getStyleClass().contains("tmp-button-action"));
        assertNull(root.lookup("#nameField"));
        assertNull(root.lookup("#descriptionField"));
        assertNull(root.lookup("#updateButton"));
        assertNull(root.lookup("#deleteButton"));
        assertNull(root.lookup("#refreshButton"));
    }

    @Test
    void createButtonHiddenWithoutPermission() throws Exception {
        RoleAdministrationViewModel viewModel = new RoleAdministrationViewModel(
                new EmptyRoles(), new EmptyUsers(), new DenyAll());
        Parent root = loadScreen(viewModel, 1024, 700);
        Button createButton = (Button) root.lookup("#createRoleButton");
        assertFalse(createButton.isVisible());
        assertFalse(createButton.isManaged());
    }

    @Test
    void contextMenuHasEditAndDeleteWithDangerStyle() throws Exception {
        FakeRoles roles = new FakeRoles();
        roles.roles.add(sampleRole("Operator", "ops"));
        RoleAdministrationViewModel viewModel = new RoleAdministrationViewModel(
                roles, new EmptyUsers(), new AllowAll());
        Parent root = loadScreen(viewModel, 1024, 700);
        TableView<RoleSummary> table = (TableView<RoleSummary>) root.lookup("#roleTable");

        CountDownLatch rowLatch = new CountDownLatch(1);
        AtomicReference<TableRow<RoleSummary>> rowRef = new AtomicReference<>();
        Platform.runLater(() -> {
            if (!table.getItems().isEmpty()) {
                table.getSelectionModel().select(0);
            }
            table.applyCss();
            table.layout();
            rowRef.set((TableRow<RoleSummary>) table.lookup("TableRow"));
            rowLatch.countDown();
        });
        assertTrue(rowLatch.await(10, TimeUnit.SECONDS));

        TableRow<RoleSummary> row = rowRef.get();
        assertNotNull(row);
        ContextMenu menu = row.getContextMenu();
        assertNotNull(menu);
        assertEquals(3, menu.getItems().size());
        assertTrue(menu.getItems().get(0) instanceof MenuItem);
        assertEquals("Редактировать", ((MenuItem) menu.getItems().get(0)).getText());
        assertTrue(menu.getItems().get(1) instanceof SeparatorMenuItem);
        MenuItem deleteItem = (MenuItem) menu.getItems().get(2);
        assertEquals("Удалить", deleteItem.getText());
        assertTrue(deleteItem.getStyleClass().contains("tmp-menu-item-danger"));
    }

    @Test
    void detailPanelShowsEmptyStateWithoutSelection() throws Exception {
        RoleAdministrationViewModel viewModel = new RoleAdministrationViewModel(
                new EmptyRoles(), new EmptyUsers(), new AllowAll());
        Parent root = loadScreen(viewModel, 1024, 700);
        VBox emptyState = (VBox) root.lookup("#detailEmptyState");
        assertNotNull(emptyState);
        assertTrue(emptyState.isVisible());
    }

    @Test
    void loadsTableAgainstViewModel() throws Exception {
        RoleAdministrationViewModel viewModel = new RoleAdministrationViewModel(
                new EmptyRoles(), new EmptyUsers(), new AllowAll());
        Parent root = loadScreen(viewModel, 1024, 700);
        TableView<?> table = (TableView<?>) root.lookup("#roleTable");
        assertNotNull(table);
        assertNotNull(table.getItems());
    }

    private static Parent loadScreen(RoleAdministrationViewModel viewModel, double width, double height)
            throws Exception {
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                "roles",
                "com/tmp/ui/shell/screen/roleadmin/RoleAdministrationScreen.fxml",
                () -> viewModel));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Parent> root = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                Parent loaded = navigation.load("roles");
                Scene scene = new Scene(loaded, width, height);
                TmpTheme.apply(scene);
                loaded.applyCss();
                loaded.layout();
                root.set(loaded);
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Role admin FX load failed", error.get());
        }
        return root.get();
    }

    private static Label findByStyleClass(Parent root, String styleClass) {
        for (javafx.scene.Node node : root.lookupAll("." + styleClass)) {
            if (node instanceof Label label) {
                return label;
            }
        }
        return null;
    }

    private static String labelText(Label label) {
        return label == null ? null : label.getText();
    }

    private static RoleSummary sampleRole(String name, String description) {
        return new RoleSummary(
                RoleId.generate(),
                name,
                description,
                Set.of(),
                0L,
                Instant.parse("2026-07-23T04:00:00Z"),
                Instant.parse("2026-07-23T04:00:00Z"));
    }

    private static class FakeRoles implements RoleAdministrationService {
        private final List<RoleSummary> roles = new ArrayList<>();

        @Override
        public RoleSummary createRole(String name, String description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RoleSummary updateRole(RoleId roleId, String name, String description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RoleSummary grantPermissionToRole(RoleId roleId, PermissionId permissionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RoleSummary revokePermissionFromRole(RoleId roleId, PermissionId permissionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteRole(RoleId roleId) {
        }

        @Override
        public List<RoleSummary> listRoles() {
            return List.copyOf(roles);
        }

        @Override
        public void assignRole(UserId userId, RoleId roleId) {
        }

        @Override
        public void revokeRole(UserId userId, RoleId roleId) {
        }

        @Override
        public void grantIndividualPermission(UserId userId, PermissionId permissionId) {
        }

        @Override
        public void revokeIndividualPermission(UserId userId, PermissionId permissionId) {
        }

        @Override
        public void removeOverride(UserId userId, PermissionId permissionId) {
        }

        @Override
        public List<PermissionSummary> listAllPermissionDefinitions() {
            return List.of();
        }
    }

    private static final class EmptyRoles implements RoleAdministrationService {
        @Override
        public RoleSummary createRole(String name, String description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RoleSummary updateRole(RoleId roleId, String name, String description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RoleSummary grantPermissionToRole(RoleId roleId, PermissionId permissionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RoleSummary revokePermissionFromRole(RoleId roleId, PermissionId permissionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteRole(RoleId roleId) {
        }

        @Override
        public List<RoleSummary> listRoles() {
            return List.of();
        }

        @Override
        public void assignRole(UserId userId, RoleId roleId) {
        }

        @Override
        public void revokeRole(UserId userId, RoleId roleId) {
        }

        @Override
        public void grantIndividualPermission(UserId userId, PermissionId permissionId) {
        }

        @Override
        public void revokeIndividualPermission(UserId userId, PermissionId permissionId) {
        }

        @Override
        public void removeOverride(UserId userId, PermissionId permissionId) {
        }

        @Override
        public List<PermissionSummary> listAllPermissionDefinitions() {
            return List.of();
        }
    }

    private static final class EmptyUsers implements UserAdministrationService {
        @Override
        public UserCreationResult createUser(Login login, DisplayName displayName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserSummary updateUser(UserId userId, Login login, DisplayName newDisplayName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserSummary deleteUser(UserId userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<UserSummary> listUsers(int pageIndex, int pageSize, String statusFilter) {
            return List.of();
        }

        @Override
        public void changeOwnPassword(char[] currentPassword, char[] newPassword) {
        }

        @Override
        public PasswordResetResult requestPasswordReset(UserId targetUserId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class AllowAll implements AuthorizationService {
        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return true;
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of();
        }
    }

    private static final class DenyAll implements AuthorizationService {
        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return permissionId.equals(SecurityPermissions.ROLES_VIEW);
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of(SecurityPermissions.ROLES_VIEW);
        }
    }
}
