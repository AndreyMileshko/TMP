package com.tmp.ui.shell.screen.roleadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import javafx.event.ActionEvent;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Real FXML/Controller regression for DEFECT-5: permission checkbox must not clear role selection.
 */
class RoleAdministrationSelectionFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void checkboxOnKeepsTableSelectionAndSelectedRoleId() throws Exception {
        assertTogglePreservesSelection(true);
    }

    @Test
    void checkboxOffKeepsTableSelectionAndSelectedRoleId() throws Exception {
        assertTogglePreservesSelection(false);
    }

    @Test
    void permissionPresentationUsesDisplayNameAndTechnicalId() {
        PermissionSummary summary = new PermissionSummary(
                PermissionId.of("order.order.create"), "Создание заказов", "", true);
        assertEquals("Создание заказов", RoleAdministrationController.permissionDisplayName(summary));
        assertEquals("order.order.create", RoleAdministrationController.permissionTechnicalId(summary));
    }

    private void assertTogglePreservesSelection(boolean grant) throws Exception {
        RecordingRoles roles = new RecordingRoles();
        PermissionId permission = SecurityPermissions.USERS_VIEW;
        RoleSummary role = roles.addRole(
                "Security Administrator",
                "admin role",
                grant ? Set.of() : Set.of(permission));
        roles.permissions.add(new PermissionSummary(permission, "Просмотр пользователей", "", true));

        RoleAdministrationViewModel viewModel =
                new RoleAdministrationViewModel(roles, new EmptyUsers(), new AllowAll());
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                "roles",
                "com/tmp/ui/shell/screen/roleadmin/RoleAdministrationScreen.fxml",
                () -> viewModel));

        LoadedScreen loaded = loadScreen(navigation);
        CountDownLatch actionLatch = new CountDownLatch(1);
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
                VBox permissionBox = (VBox) loaded.root().lookup("#permissionBox");
                assertNotNull(permissionBox);
                assertEquals(role.id(), viewModel.selectedRoleId());
                assertNotNull(roleTable.getSelectionModel().getSelectedItem());
                assertEquals(role.id(), roleTable.getSelectionModel().getSelectedItem().id());

                CheckBox permissionCheck = (CheckBox) permissionBox.getChildren().get(0);
                assertEquals(!grant, permissionCheck.isSelected());
                permissionCheck.setSelected(grant);
                permissionCheck.getOnAction().handle(new ActionEvent(permissionCheck, permissionCheck));

                assertEquals(role.id(), viewModel.selectedRoleId());
                assertNotNull(
                        roleTable.getSelectionModel().getSelectedItem(),
                        "TableView selection must remain after checkbox toggle");
                assertEquals(role.id(), roleTable.getSelectionModel().getSelectedItem().id());
                assertEquals(grant, viewModel.isPermissionGrantedOnSelected(permission));
                assertEquals(grant, ((CheckBox) permissionBox.getChildren().get(0)).isSelected());

                viewModel.updateRole(role, "Renamed Role", role.description());
                assertEquals(role.id(), viewModel.selectedRoleId());
                assertEquals(role.id(), roles.lastUpdatedRoleId);
                assertEquals("Renamed Role", roles.lastUpdatedName);
                roleTable.getSelectionModel().select(
                        roleTable.getItems().stream()
                                .filter(item -> item.id().equals(role.id()))
                                .findFirst()
                                .orElseThrow());
                assertNotNull(roleTable.getSelectionModel().getSelectedItem());
                assertEquals(role.id(), roleTable.getSelectionModel().getSelectedItem().id());
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                actionLatch.countDown();
            }
        });

        assertTrue(actionLatch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Role selection FX interaction failed", error.get());
        }
        if (grant) {
            assertEquals(permission, roles.lastGrantedPermission);
        } else {
            assertEquals(permission, roles.lastRevokedPermission);
        }
    }

    private static LoadedScreen loadScreen(
            com.tmp.ui.shell.navigation.NavigationService navigation) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<TableView<RoleSummary>> table = new AtomicReference<>();
        AtomicReference<Parent> rootRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Parent root = navigation.load("roles");
                Stage stage = new Stage();
                Scene scene = new Scene(root, 1024, 700);
                com.tmp.ui.shell.theme.TmpTheme.apply(scene);
                stage.setScene(scene);
                root.applyCss();
                root.layout();
                rootRef.set(root);
                table.set((TableView<RoleSummary>) root.lookup("#roleTable"));
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
        assertNotNull(table.get());
        return new LoadedScreen(rootRef.get(), table.get());
    }

    private record LoadedScreen(Parent root, TableView<RoleSummary> table) {}

    private static final class RecordingRoles implements RoleAdministrationService {
        private final List<RoleSummary> roles = new ArrayList<>();
        private final List<PermissionSummary> permissions = new ArrayList<>();
        private RoleId lastUpdatedRoleId;
        private String lastUpdatedName;
        private PermissionId lastGrantedPermission;
        private PermissionId lastRevokedPermission;

        RoleSummary addRole(String name, String description, Set<PermissionId> permissionIds) {
            RoleSummary created = new RoleSummary(
                    RoleId.generate(),
                    name,
                    description,
                    permissionIds,
                    0L,
                    Instant.parse("2026-07-23T04:00:00Z"),
                    Instant.parse("2026-07-23T04:00:00Z"));
            roles.add(created);
            return created;
        }

        private void replace(RoleSummary updated) {
            for (int i = 0; i < roles.size(); i++) {
                if (roles.get(i).id().equals(updated.id())) {
                    roles.set(i, updated);
                    return;
                }
            }
            roles.add(updated);
        }

        @Override
        public RoleSummary createRole(String name, String description) {
            return addRole(name, description, Set.of());
        }

        @Override
        public RoleSummary updateRole(RoleId roleId, String name, String description) {
            lastUpdatedRoleId = roleId;
            lastUpdatedName = name;
            RoleSummary current = roles.stream().filter(r -> r.id().equals(roleId)).findFirst().orElseThrow();
            RoleSummary updated = new RoleSummary(
                    current.id(),
                    name,
                    description,
                    current.permissionIds(),
                    current.version() + 1,
                    current.createdAt(),
                    Instant.parse("2026-07-23T05:00:00Z"));
            replace(updated);
            return updated;
        }

        @Override
        public RoleSummary grantPermissionToRole(RoleId roleId, PermissionId permissionId) {
            lastGrantedPermission = permissionId;
            RoleSummary current = roles.stream().filter(r -> r.id().equals(roleId)).findFirst().orElseThrow();
            Set<PermissionId> next = new HashSet<>(current.permissionIds());
            next.add(permissionId);
            RoleSummary updated = new RoleSummary(
                    current.id(),
                    current.name(),
                    current.description(),
                    next,
                    current.version() + 1,
                    current.createdAt(),
                    Instant.parse("2026-07-23T05:00:00Z"));
            replace(updated);
            return updated;
        }

        @Override
        public RoleSummary revokePermissionFromRole(RoleId roleId, PermissionId permissionId) {
            lastRevokedPermission = permissionId;
            RoleSummary current = roles.stream().filter(r -> r.id().equals(roleId)).findFirst().orElseThrow();
            Set<PermissionId> next = new HashSet<>(current.permissionIds());
            next.remove(permissionId);
            RoleSummary updated = new RoleSummary(
                    current.id(),
                    current.name(),
                    current.description(),
                    next,
                    current.version() + 1,
                    current.createdAt(),
                    Instant.parse("2026-07-23T05:00:00Z"));
            replace(updated);
            return updated;
        }

        @Override
        public void deleteRole(RoleId roleId) {
            roles.removeIf(r -> r.id().equals(roleId));
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
}
