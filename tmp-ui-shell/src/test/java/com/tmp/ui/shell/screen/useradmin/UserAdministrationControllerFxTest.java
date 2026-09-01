package com.tmp.ui.shell.screen.useradmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UserAdministrationControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void loadsSimplifiedUsersScreen() throws Exception {
        UserAdministrationViewModel viewModel = new UserAdministrationViewModel(
                new EmptyUsers(), new AllowAll());
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                "users",
                "com/tmp/ui/shell/screen/useradmin/UserAdministrationScreen.fxml",
                () -> viewModel));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<TableView<?>> table = new AtomicReference<>();
        AtomicReference<Button> createButton = new AtomicReference<>();
        AtomicBoolean hasInlineLogin = new AtomicBoolean(true);
        AtomicBoolean hasInlinePassword = new AtomicBoolean(true);

        Platform.runLater(() -> {
            try {
                Parent root = navigation.load("users");
                Stage stage = new Stage();
                stage.setScene(new Scene(root));
                table.set((TableView<?>) root.lookup("#userTable"));
                createButton.set((Button) root.lookup("#createUserButton"));
                hasInlineLogin.set(root.lookup("#loginField") != null);
                hasInlinePassword.set(root.lookup("#passwordField") != null);
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("User admin FX load failed", error.get());
        }
        assertNotNull(table.get());
        assertNotNull(createButton.get());
        assertTrue(createButton.get().getText().contains("Создать пользователя"));
        assertFalse(hasInlineLogin.get());
        assertFalse(hasInlinePassword.get());
    }

    @Test
    void showDeletedToggleFiltersTableItems() throws Exception {
        UsersWithActiveAndDeleted service = new UsersWithActiveAndDeleted();
        UserAdministrationViewModel viewModel = new UserAdministrationViewModel(service, new AllowAll());
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                "users",
                "com/tmp/ui/shell/screen/useradmin/UserAdministrationScreen.fxml",
                () -> viewModel));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<TableView<UserSummary>> table = new AtomicReference<>();
        AtomicReference<CheckBox> showDeleted = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Parent root = navigation.load("users");
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 800, 600));
                stage.show();
                table.set((TableView<UserSummary>) root.lookup("#userTable"));
                showDeleted.set((CheckBox) root.lookup("#showDeletedCheckBox"));
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("User admin deleted toggle FX test failed", error.get());
        }
        assertNotNull(table.get());
        assertNotNull(showDeleted.get());
        assertEquals("Показывать удалённых", showDeleted.get().getText());
        assertFalse(showDeleted.get().isSelected());
        assertNotNull(table.get().getItems());
        assertEquals(1, table.get().getItems().size());
        assertEquals("active", table.get().getItems().get(0).login().value());

        CountDownLatch toggleOnLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                showDeleted.get().setSelected(true);
                assertEquals(2, table.get().getItems().size());
                showDeleted.get().setSelected(false);
                assertEquals(1, table.get().getItems().size());
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                toggleOnLatch.countDown();
            }
        });
        assertTrue(toggleOnLatch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("User admin deleted toggle FX test failed", error.get());
        }

        UserSummary active = service.users.get(0);
        CountDownLatch deleteLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                viewModel.deleteUser(active);
                assertEquals(0, table.get().getItems().size());
                showDeleted.get().setSelected(true);
                assertEquals(2, table.get().getItems().size());
                assertTrue(table.get().getItems().stream()
                        .anyMatch(user -> "active".equals(user.login().value()) && "DELETED".equals(user.status())));
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                deleteLatch.countDown();
            }
        });
        assertTrue(deleteLatch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("User admin deleted toggle FX test failed", error.get());
        }
    }

    @Test
    void tableRowsHaveContextMenuFactory() throws Exception {
        UserAdministrationViewModel viewModel = new UserAdministrationViewModel(
                new EmptyUsers(), new AllowAll());
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                "users",
                "com/tmp/ui/shell/screen/useradmin/UserAdministrationScreen.fxml",
                () -> viewModel));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<TableView<?>> table = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Parent root = navigation.load("users");
                table.set((TableView<?>) root.lookup("#userTable"));
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
        assertNotNull(table.get().getRowFactory());
    }

    private static final class UsersWithActiveAndDeleted implements UserAdministrationService {
        private final List<UserSummary> users = new ArrayList<>();

        private UsersWithActiveAndDeleted() {
            users.add(summary("active", "Active User", "ACTIVE"));
            users.add(summary("deleted", "Deleted User", "DELETED"));
        }

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
            UserSummary found = users.stream().filter(u -> u.id().equals(userId)).findFirst().orElseThrow();
            UserSummary deleted = new UserSummary(
                    found.id(),
                    found.login(),
                    found.displayName(),
                    "DELETED",
                    found.version(),
                    found.createdAt(),
                    found.updatedAt());
            users.set(users.indexOf(found), deleted);
            return deleted;
        }

        @Override
        public List<UserSummary> listUsers(int pageIndex, int pageSize, String statusFilter) {
            return List.copyOf(users);
        }

        @Override
        public void changeOwnPassword(char[] currentPassword, char[] newPassword) {
        }

        @Override
        public PasswordResetResult requestPasswordReset(UserId targetUserId) {
            throw new UnsupportedOperationException();
        }

        private static UserSummary summary(String login, String name, String status) {
            return new UserSummary(
                    UserId.generate(),
                    Login.of(login),
                    DisplayName.of(name),
                    status,
                    0L,
                    Instant.parse("2026-07-23T04:00:00Z"),
                    Instant.parse("2026-07-23T04:00:00Z"));
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
