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
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserSummary;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RoleAdministrationSelectionFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void togglePermissionKeepsSelectedRoleAndUpdateAppliesToIt() throws Exception {
        RecordingRoles roles = new RecordingRoles();
        RoleSummary role = roles.addRole("Security Administrator", "admin role");
        roles.permissions.add(new PermissionSummary(
                SecurityPermissions.USERS_VIEW, "View users", "", true));

        RoleAdministrationViewModel viewModel =
                new RoleAdministrationViewModel(roles, new EmptyUsers(), new AllowAll());
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                "roles",
                "com/tmp/ui/shell/screen/roleadmin/RoleAdministrationScreen.fxml",
                () -> viewModel));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<TableView<RoleSummary>> table = new AtomicReference<>();
        AtomicReference<VBox> permissionBox = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Parent root = navigation.load("roles");
                Stage stage = new Stage();
                stage.setScene(new Scene(root));
                table.set((TableView<RoleSummary>) root.lookup("#roleTable"));
                permissionBox.set((VBox) root.lookup("#permissionBox"));
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

        CountDownLatch actionLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                TableView<RoleSummary> roleTable = table.get();
                roleTable.getSelectionModel().select(role);
                assertEquals(role.id(), viewModel.selectedRoleId());

                CheckBox permissionCheck = (CheckBox) permissionBox.get().getChildren().get(0);
                permissionCheck.setSelected(true);
                permissionCheck.getOnAction().handle(new ActionEvent(permissionCheck, permissionCheck));

                assertEquals(role.id(), viewModel.selectedRoleId());
                assertEquals(role, roleTable.getSelectionModel().getSelectedItem());

                viewModel.nameInputProperty().set("Renamed Role");
                viewModel.updateSelected();

                assertEquals(role.id(), viewModel.selectedRoleId());
                assertEquals(role.id(), roles.lastUpdatedRoleId);
                assertEquals("Renamed Role", roles.lastUpdatedName);
                assertEquals(role, roleTable.getSelectionModel().getSelectedItem());
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
        assertNotNull(roles.lastGrantedPermission);
        assertEquals(SecurityPermissions.USERS_VIEW, roles.lastGrantedPermission);
    }

    private static final class RecordingRoles implements RoleAdministrationService {
        private final List<RoleSummary> roles = new ArrayList<>();
        private final List<PermissionSummary> permissions = new ArrayList<>();
        private RoleId lastUpdatedRoleId;
        private String lastUpdatedName;
        private PermissionId lastGrantedPermission;

        RoleSummary addRole(String name, String description) {
            RoleSummary created = new RoleSummary(
                    RoleId.generate(),
                    name,
                    description,
                    Set.of(),
                    0L,
                    Instant.parse("2026-07-23T04:00:00Z"),
                    Instant.parse("2026-07-23T04:00:00Z"));
            roles.add(created);
            return created;
        }

        @Override
        public RoleSummary createRole(String name, String description) {
            return addRole(name, description);
        }

        @Override
        public RoleSummary updateRole(RoleId roleId, String name, String description) {
            lastUpdatedRoleId = roleId;
            lastUpdatedName = name;
            return roles.get(0);
        }

        @Override
        public RoleSummary grantPermissionToRole(RoleId roleId, PermissionId permissionId) {
            lastGrantedPermission = permissionId;
            return roles.get(0);
        }

        @Override
        public RoleSummary revokePermissionFromRole(RoleId roleId, PermissionId permissionId) {
            return roles.get(0);
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
            return List.copyOf(permissions);
        }
    }

    private static final class EmptyUsers implements UserAdministrationService {
        @Override
        public UserSummary createUser(Login login, DisplayName displayName, char[] initialPassword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserSummary updateUser(UserId userId, DisplayName newDisplayName) {
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
        public void resetPassword(UserId targetUserId, char[] newPassword) {
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
