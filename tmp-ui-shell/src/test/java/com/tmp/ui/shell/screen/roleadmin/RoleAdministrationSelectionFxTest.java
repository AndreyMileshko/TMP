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
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.api.PasswordResetResult;
import com.tmp.security.api.UserCreationResult;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserSummary;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Real FXML/Controller regression: permission tree edits must not clear role selection; Apply
 * persists the target set.
 */
class RoleAdministrationSelectionFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void applyPermissionsKeepsTableSelectionAndSelectedRoleId() throws Exception {
        RecordingRoles roles = new RecordingRoles();
        PermissionId permission = SecurityPermissions.USERS_VIEW;
        RoleSummary role = roles.addRole("Security Administrator", "admin role", Set.of());
        roles.permissions.add(new PermissionSummary(permission, "Просмотр пользователей", "", true));

        RoleAdministrationViewModel viewModel =
                new RoleAdministrationViewModel(roles, new EmptyUsers(), new AllowAll());
        LoadedScreen loaded = loadScreen(viewModel);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                TableView<RoleSummary> roleTable = loaded.table();
                roleTable.getSelectionModel().select(
                        roleTable.getItems().stream()
                                .filter(item -> item.id().equals(role.id()))
                                .findFirst()
                                .orElseThrow());
                loaded.root().applyCss();
                loaded.root().layout();

                @SuppressWarnings("unchecked")
                TreeView<RoleAdministrationController.PermissionTreeNode> tree =
                        (TreeView<RoleAdministrationController.PermissionTreeNode>)
                                loaded.root().lookup("#permissionTree");
                assertNotNull(tree);
                assertNotNull(tree.getRoot());
                CheckBoxTreeItem<RoleAdministrationController.PermissionTreeNode> leaf =
                        findLeaf(tree.getRoot(), permission);
                assertNotNull(leaf);
                assertFalse(leaf.isSelected());
                leaf.setSelected(true);
                assertTrue(viewModel.permissionsDirtyProperty().get());

                Button apply = (Button) loaded.root().lookup("#applyPermissionsButton");
                assertNotNull(apply);
                apply.fire();

                assertEquals(role.id(), viewModel.selectedRoleId());
                assertNotNull(roleTable.getSelectionModel().getSelectedItem());
                assertEquals(role.id(), roleTable.getSelectionModel().getSelectedItem().id());
                assertTrue(viewModel.isPermissionDesired(permission));
                assertFalse(viewModel.permissionsDirtyProperty().get());
                assertNull(loaded.root().lookup("#revokeButton"));
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Permission apply selection regression failed", error.get());
        }
    }

    @Test
    void permissionPresentationUsesDisplayName() {
        PermissionSummary summary = new PermissionSummary(
                PermissionId.of("order.order.create"), "Создание заказов", "", true);
        assertEquals("Создание заказов", summary.displayName());
        assertEquals("order.order.create", summary.permissionId().value());
    }

    private static CheckBoxTreeItem<RoleAdministrationController.PermissionTreeNode> findLeaf(
            TreeItem<RoleAdministrationController.PermissionTreeNode> root, PermissionId id) {
        for (TreeItem<RoleAdministrationController.PermissionTreeNode> group : root.getChildren()) {
            for (TreeItem<RoleAdministrationController.PermissionTreeNode> child : group.getChildren()) {
                if (child.getValue() != null
                        && child.getValue().permission() != null
                        && id.equals(child.getValue().permission().permissionId())
                        && child instanceof CheckBoxTreeItem<?> check) {
                    @SuppressWarnings("unchecked")
                    CheckBoxTreeItem<RoleAdministrationController.PermissionTreeNode> typed =
                            (CheckBoxTreeItem<RoleAdministrationController.PermissionTreeNode>) check;
                    return typed;
                }
            }
        }
        return null;
    }

    private static LoadedScreen loadScreen(RoleAdministrationViewModel viewModel) throws Exception {
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                "roles",
                "com/tmp/ui/shell/screen/roleadmin/RoleAdministrationScreen.fxml",
                () -> viewModel));
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<LoadedScreen> loaded = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                Parent root = navigation.load("roles");
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 1024, 700));
                stage.show();
                @SuppressWarnings("unchecked")
                TableView<RoleSummary> table = (TableView<RoleSummary>) root.lookup("#roleTable");
                loaded.set(new LoadedScreen(root, table));
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Failed to load Roles screen", error.get());
        }
        return loaded.get();
    }

    private record LoadedScreen(Parent root, TableView<RoleSummary> table) {}

    private static final class RecordingRoles implements RoleAdministrationService {
        private final List<RoleSummary> roles = new ArrayList<>();
        private final List<PermissionSummary> permissions = new ArrayList<>();

        RoleSummary addRole(String name, String description, Set<PermissionId> granted) {
            RoleSummary created = new RoleSummary(
                    RoleId.generate(),
                    name,
                    description,
                    new HashSet<>(granted),
                    0L,
                    Instant.parse("2026-07-23T04:00:00Z"),
                    Instant.parse("2026-07-23T04:00:00Z"));
            roles.add(created);
            return created;
        }

        @Override
        public RoleSummary createRole(String name, String description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RoleSummary updateRole(RoleId roleId, String name, String description) {
            RoleSummary current = roles.stream().filter(r -> r.id().equals(roleId)).findFirst().orElseThrow();
            RoleSummary updated = new RoleSummary(
                    current.id(),
                    name,
                    description,
                    current.permissionIds(),
                    current.version() + 1,
                    current.createdAt(),
                    Instant.parse("2026-07-23T05:00:00Z"));
            roles.set(roles.indexOf(current), updated);
            return updated;
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
        public RoleSummary setRolePermissions(RoleId roleId, Set<PermissionId> targetPermissions) {
            RoleSummary current = roles.stream().filter(r -> r.id().equals(roleId)).findFirst().orElseThrow();
            RoleSummary updated = new RoleSummary(
                    current.id(),
                    current.name(),
                    current.description(),
                    Set.copyOf(targetPermissions),
                    current.version() + 1,
                    current.createdAt(),
                    Instant.parse("2026-07-23T05:00:00Z"));
            roles.set(roles.indexOf(current), updated);
            return updated;
        }

        @Override
        public void deleteRole(RoleId roleId) {}

        @Override
        public List<RoleSummary> listRoles() {
            return List.copyOf(roles);
        }

        @Override
        public void assignRole(UserId userId, RoleId roleId) {}

        @Override
        public void revokeRole(UserId userId, RoleId roleId) {}

        @Override
        public Set<RoleId> listRolesForUser(UserId userId) {
            return Set.of();
        }

        @Override
        public void grantIndividualPermission(UserId userId, PermissionId permissionId) {}

        @Override
        public void revokeIndividualPermission(UserId userId, PermissionId permissionId) {}

        @Override
        public void removeOverride(UserId userId, PermissionId permissionId) {}

        @Override
        public List<PermissionSummary> listAllPermissionDefinitions() {
            return List.copyOf(permissions);
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
        public List<UserSummary> searchUsers(String query, int limit) {
            return List.of();
        }

        @Override
        public void changeOwnPassword(char[] currentPassword, char[] newPassword) {}

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
        public void requirePermission(PermissionId permissionId) {}

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of();
        }
    }
}
