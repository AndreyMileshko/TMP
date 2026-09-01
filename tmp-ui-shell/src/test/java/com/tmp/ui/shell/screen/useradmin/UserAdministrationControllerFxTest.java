package com.tmp.ui.shell.screen.useradmin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserSummary;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
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

    private static final class EmptyUsers implements UserAdministrationService {
        @Override
        public UserSummary createUser(Login login, DisplayName displayName) {
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
        public void requestPasswordReset(UserId targetUserId) {
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
