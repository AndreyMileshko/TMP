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
import javafx.scene.control.TextField;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
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

    @Test
    void readOnlyPermissionTreeBlocksMutationWithoutPermissionsAssign() throws Exception {
        RecordingRoles roles = new RecordingRoles();
        PermissionId granted = SecurityPermissions.USERS_VIEW;
        PermissionId other = SecurityPermissions.ROLES_VIEW;
        RoleSummary role = roles.addRole("Viewer", "view", Set.of(granted));
        roles.permissions.add(new PermissionSummary(granted, "Просмотр пользователей", "", true));
        roles.permissions.add(new PermissionSummary(other, "Просмотр ролей", "", true));

        RoleAdministrationViewModel viewModel =
                new RoleAdministrationViewModel(roles, new EmptyUsers(), new RolesViewOnly());
        LoadedScreen loaded = loadScreen(viewModel);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                selectRole(loaded, role);
                @SuppressWarnings("unchecked")
                TreeView<RoleAdministrationController.PermissionTreeNode> tree =
                        (TreeView<RoleAdministrationController.PermissionTreeNode>)
                                loaded.root().lookup("#permissionTree");
                CheckBoxTreeItem<RoleAdministrationController.PermissionTreeNode> grantedLeaf =
                        findLeaf(tree.getRoot(), granted);
                CheckBoxTreeItem<RoleAdministrationController.PermissionTreeNode> otherLeaf =
                        findLeaf(tree.getRoot(), other);
                CheckBoxTreeItem<RoleAdministrationController.PermissionTreeNode> group =
                        findGroup(tree.getRoot(), "security");
                assertTrue(grantedLeaf.isSelected());
                assertFalse(otherLeaf.isSelected());

                Button apply = (Button) loaded.root().lookup("#applyPermissionsButton");
                assertFalse(apply.isVisible());
                assertFalse(apply.isManaged());

                grantedLeaf.setSelected(false);
                otherLeaf.setSelected(true);
                group.setSelected(true);
                assertTrue(grantedLeaf.isSelected());
                assertFalse(otherLeaf.isSelected());
                assertFalse(viewModel.permissionsDirtyProperty().get());
                assertTrue(viewModel.isPermissionDesired(granted));
                assertFalse(viewModel.isPermissionDesired(other));

                group.setExpanded(false);
                assertFalse(group.isExpanded());
                group.setExpanded(true);
                assertTrue(group.isExpanded());

                tree.getSelectionModel().select(grantedLeaf);
                tree.fireEvent(
                        new KeyEvent(
                                KeyEvent.KEY_PRESSED,
                                "",
                                "",
                                KeyCode.SPACE,
                                false,
                                false,
                                false,
                                false));
                assertTrue(grantedLeaf.isSelected());
                assertFalse(viewModel.permissionsDirtyProperty().get());

                TextField search = (TextField) loaded.root().lookup("#permissionSearchField");
                search.setText("Просмотр пользователей");
                loaded.root().applyCss();
                loaded.root().layout();
                assertEquals(1, countLeaves(tree.getRoot()));
                search.setText("");
                loaded.root().applyCss();
                loaded.root().layout();
                assertEquals(2, countLeaves(tree.getRoot()));
                assertTrue(viewModel.isPermissionDesired(granted));
                assertFalse(viewModel.isPermissionDesired(other));
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Read-only permission tree regression failed", error.get());
        }
    }

    @Test
    void filteredGroupCheckboxAffectsOnlyVisiblePermissions() throws Exception {
        RecordingRoles roles = new RecordingRoles();
        PermissionId p1 = PermissionId.of("warehouse.stock.view");
        PermissionId p2 = PermissionId.of("warehouse.stock.adjust");
        PermissionId p3 = PermissionId.of("warehouse.transfer.create");
        PermissionId p4 = PermissionId.of("warehouse.structure.view");
        RoleSummary role = roles.addRole("Wh", "wh", Set.of(p3, p4));
        roles.permissions.add(new PermissionSummary(p1, "Сток P1", "", true));
        roles.permissions.add(new PermissionSummary(p2, "Сток P2", "", true));
        roles.permissions.add(new PermissionSummary(p3, "Перемещение P3", "", true));
        roles.permissions.add(new PermissionSummary(p4, "Структура P4", "", true));

        RoleAdministrationViewModel viewModel =
                new RoleAdministrationViewModel(roles, new EmptyUsers(), new AllowAll());
        LoadedScreen loaded = loadScreen(viewModel);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                selectRole(loaded, role);
                TextField search = (TextField) loaded.root().lookup("#permissionSearchField");
                search.setText("Сток");
                loaded.root().applyCss();
                loaded.root().layout();

                @SuppressWarnings("unchecked")
                TreeView<RoleAdministrationController.PermissionTreeNode> tree =
                        (TreeView<RoleAdministrationController.PermissionTreeNode>)
                                loaded.root().lookup("#permissionTree");
                CheckBoxTreeItem<RoleAdministrationController.PermissionTreeNode> group =
                        findGroup(tree.getRoot(), "warehouse");
                assertNotNull(group);
                assertEquals(2, group.getChildren().size());
                assertTrue(group.isIndeterminate() || !group.isSelected());

                group.setSelected(true);
                assertTrue(viewModel.isPermissionDesired(p1));
                assertTrue(viewModel.isPermissionDesired(p2));
                assertTrue(viewModel.isPermissionDesired(p3));
                assertTrue(viewModel.isPermissionDesired(p4));

                group.setSelected(false);
                assertFalse(viewModel.isPermissionDesired(p1));
                assertFalse(viewModel.isPermissionDesired(p2));
                assertTrue(viewModel.isPermissionDesired(p3));
                assertTrue(viewModel.isPermissionDesired(p4));

                search.setText("");
                loaded.root().applyCss();
                loaded.root().layout();
                CheckBoxTreeItem<RoleAdministrationController.PermissionTreeNode> fullGroup =
                        findGroup(tree.getRoot(), "warehouse");
                assertEquals(4, fullGroup.getChildren().size());
                assertTrue(fullGroup.isIndeterminate());
                assertTrue(viewModel.isPermissionDesired(p3));
                assertTrue(viewModel.isPermissionDesired(p4));
                assertFalse(viewModel.isPermissionDesired(p1));
                assertFalse(viewModel.isPermissionDesired(p2));
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Filtered group visible-only semantics failed", error.get());
        }
    }

    @Test
    void assignmentSectionVisibleOnlyWithRolesAssignAndUsersView() throws Exception {
        RecordingRoles roles = new RecordingRoles();
        roles.addRole("Ops", "ops", Set.of());

        RoleAdministrationViewModel assignOnly =
                new RoleAdministrationViewModel(roles, new EmptyUsers(), new AssignOnly());
        Parent assignOnlyRoot = loadScreen(assignOnly).root();
        VBox assignOnlySection = (VBox) assignOnlyRoot.lookup("#assignmentSection");
        assertFalse(assignOnlySection.isVisible());
        assertFalse(assignOnlySection.isManaged());

        RoleAdministrationViewModel both =
                new RoleAdministrationViewModel(roles, new EmptyUsers(), new AssignAndViewUsers());
        Parent bothRoot = loadScreen(both).root();
        VBox bothSection = (VBox) bothRoot.lookup("#assignmentSection");
        assertTrue(bothSection.isVisible());
        assertTrue(bothSection.isManaged());
        assertNull(bothRoot.lookup("#revokeButton"));

        RoleAdministrationViewModel viewOnly =
                new RoleAdministrationViewModel(roles, new EmptyUsers(), new UsersViewOnly());
        Parent viewOnlyRoot = loadScreen(viewOnly).root();
        VBox viewOnlySection = (VBox) viewOnlyRoot.lookup("#assignmentSection");
        assertFalse(viewOnlySection.isVisible());
        assertFalse(viewOnlySection.isManaged());
    }

    private static void selectRole(LoadedScreen loaded, RoleSummary role) {
        TableView<RoleSummary> roleTable = loaded.table();
        roleTable.getSelectionModel().select(
                roleTable.getItems().stream()
                        .filter(item -> item.id().equals(role.id()))
                        .findFirst()
                        .orElseThrow());
        loaded.root().applyCss();
        loaded.root().layout();
    }

    private static int countLeaves(TreeItem<RoleAdministrationController.PermissionTreeNode> root) {
        int count = 0;
        for (TreeItem<RoleAdministrationController.PermissionTreeNode> group : root.getChildren()) {
            count += group.getChildren().size();
        }
        return count;
    }

    private static CheckBoxTreeItem<RoleAdministrationController.PermissionTreeNode> findGroup(
            TreeItem<RoleAdministrationController.PermissionTreeNode> root, String namespace) {
        for (TreeItem<RoleAdministrationController.PermissionTreeNode> child : root.getChildren()) {
            if (child.getValue() != null
                    && child.getValue().group() != null
                    && namespace.equals(child.getValue().group().namespace())
                    && child instanceof CheckBoxTreeItem<?> check) {
                @SuppressWarnings("unchecked")
                CheckBoxTreeItem<RoleAdministrationController.PermissionTreeNode> typed =
                        (CheckBoxTreeItem<RoleAdministrationController.PermissionTreeNode>) check;
                return typed;
            }
        }
        return null;
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

    private static final class RolesViewOnly implements AuthorizationService {
        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return SecurityPermissions.ROLES_VIEW.equals(permissionId);
        }

        @Override
        public void requirePermission(PermissionId permissionId) {}

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of(SecurityPermissions.ROLES_VIEW);
        }
    }

    private static final class AssignOnly implements AuthorizationService {
        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return SecurityPermissions.ROLES_VIEW.equals(permissionId)
                    || SecurityPermissions.ROLES_ASSIGN.equals(permissionId);
        }

        @Override
        public void requirePermission(PermissionId permissionId) {}

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of(SecurityPermissions.ROLES_VIEW, SecurityPermissions.ROLES_ASSIGN);
        }
    }

    private static final class UsersViewOnly implements AuthorizationService {
        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return SecurityPermissions.ROLES_VIEW.equals(permissionId)
                    || SecurityPermissions.USERS_VIEW.equals(permissionId);
        }

        @Override
        public void requirePermission(PermissionId permissionId) {}

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of(SecurityPermissions.ROLES_VIEW, SecurityPermissions.USERS_VIEW);
        }
    }

    private static final class AssignAndViewUsers implements AuthorizationService {
        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return SecurityPermissions.ROLES_VIEW.equals(permissionId)
                    || SecurityPermissions.ROLES_ASSIGN.equals(permissionId)
                    || SecurityPermissions.USERS_VIEW.equals(permissionId);
        }

        @Override
        public void requirePermission(PermissionId permissionId) {}

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of(
                    SecurityPermissions.ROLES_VIEW,
                    SecurityPermissions.ROLES_ASSIGN,
                    SecurityPermissions.USERS_VIEW);
        }
    }
}
