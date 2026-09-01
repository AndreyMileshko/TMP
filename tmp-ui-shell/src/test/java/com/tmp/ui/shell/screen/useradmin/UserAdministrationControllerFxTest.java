package com.tmp.ui.shell.screen.useradmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.PasswordResetResult;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserCreationResult;
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserSummary;
import com.tmp.security.api.SecurityPermissions;
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
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UserAdministrationControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void loadsStandardUsersScreenStructure() throws Exception {
        UserAdministrationViewModel viewModel = new UserAdministrationViewModel(
                new EmptyUsers(), new AllowAll());
        Parent root = loadScreen(viewModel, 960, 640);

        assertTrue(root.getStyleClass().contains("tmp-screen"));
        assertNotNull(findByStyleClass(root, "tmp-screen-title"));
        assertEquals("Пользователи", labelText(findByStyleClass(root, "tmp-screen-title")));
        assertNotNull(findByStyleClass(root, "tmp-screen-subtitle"));
        assertNotNull(findByStyleClass(root, "tmp-toolbar"));
        assertNotNull(root.lookup("#createUserButton"));
        assertNotNull(root.lookup("#showDeletedCheckBox"));
        assertNotNull(root.lookup("#userTable"));
        Label errorLabel = (Label) root.lookup("#errorLabel");
        assertNotNull(errorLabel);
        assertTrue(errorLabel.getStyleClass().contains("tmp-message-error"));
        Button createButton = (Button) root.lookup("#createUserButton");
        assertTrue(createButton.getText().contains("Создать пользователя"));
        assertTrue(createButton.getStyleClass().contains("tmp-button-action"));
        assertNull(root.lookup("#loginField"));
        assertNull(root.lookup("#passwordField"));
    }

    @Test
    void createButtonHiddenWithoutPermission() throws Exception {
        UserAdministrationViewModel viewModel = new UserAdministrationViewModel(
                new EmptyUsers(), new DenyAll());
        Parent root = loadScreen(viewModel, 960, 640);
        Button createButton = (Button) root.lookup("#createUserButton");
        assertFalse(createButton.isVisible());
        assertFalse(createButton.isManaged());
    }

    @Test
    void showDeletedToggleFiltersTableItems() throws Exception {
        UsersWithActiveAndDeleted service = new UsersWithActiveAndDeleted();
        UserAdministrationViewModel viewModel = new UserAdministrationViewModel(service, new AllowAll());
        AtomicReference<TableView<UserSummary>> table = new AtomicReference<>();
        AtomicReference<CheckBox> showDeleted = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Parent root = loadScreen(viewModel, 800, 600);
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
        assertEquals("Показывать удалённых", showDeleted.get().getText());
        assertFalse(showDeleted.get().isSelected());
        assertEquals(1, table.get().getItems().size());

        CountDownLatch toggleLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                showDeleted.get().setSelected(true);
                assertEquals(2, table.get().getItems().size());
                showDeleted.get().setSelected(false);
                assertEquals(1, table.get().getItems().size());
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                toggleLatch.countDown();
            }
        });
        assertTrue(toggleLatch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError(error.get());
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
            throw new AssertionError(error.get());
        }
    }

    @Test
    void statusBadgesRenderRussianLabelsAndSemanticClasses() throws Exception {
        UsersWithActiveAndDeleted service = new UsersWithActiveAndDeleted();
        UserAdministrationViewModel viewModel = new UserAdministrationViewModel(service, new AllowAll());
        AtomicReference<TableView<UserSummary>> table = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Parent root = loadScreen(viewModel, 960, 640);
                table.set((TableView<UserSummary>) root.lookup("#userTable"));
                CheckBox showDeleted = (CheckBox) root.lookup("#showDeletedCheckBox");
                showDeleted.setSelected(true);
                viewModel.refresh();
                table.get().layout();
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

        CountDownLatch badgeLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Label activeBadge = findStatusBadge(table.get(), 0);
                assertEquals("Активен", activeBadge.getText());
                assertTrue(activeBadge.getStyleClass().contains("tmp-badge-success"));

                Label deletedBadge = findStatusBadge(table.get(), 1);
                assertEquals("Удалён", deletedBadge.getText());
                assertTrue(deletedBadge.getStyleClass().contains("tmp-badge-danger"));
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                badgeLatch.countDown();
            }
        });
        assertTrue(badgeLatch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
    }

    @Test
    void unknownStatusUsesNeutralBadgeWithoutException() {
        Label badge = UserStatusPresentation.createStatusBadge("ARCHIVED");
        assertEquals("ARCHIVED", badge.getText());
        assertTrue(badge.getStyleClass().contains("tmp-badge-neutral"));
    }

    @Test
    void emptyStateIsHumanReadable() throws Exception {
        UserAdministrationViewModel viewModel = new UserAdministrationViewModel(
                new EmptyUsers(), new AllowAll());
        AtomicReference<TableView<UserSummary>> table = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Parent root = loadScreen(viewModel, 960, 640);
                table.set((TableView<UserSummary>) root.lookup("#userTable"));
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

        Node placeholder = table.get().getPlaceholder();
        assertTrue(placeholder instanceof VBox);
        VBox emptyState = (VBox) placeholder;
        assertTrue(emptyState.getStyleClass().contains("tmp-empty-state"));
        Label title = (Label) emptyState.getChildren().get(0);
        assertTrue(title.getStyleClass().contains("tmp-empty-state-title"));
        assertEquals("Нет пользователей для отображения", title.getText());
        assertFalse(title.getText().toLowerCase().contains("no content"));
    }

    @Test
    void contextMenuHasSeparatorAndDangerDeleteWithPermissions() throws Exception {
        UsersWithActiveAndDeleted service = new UsersWithActiveAndDeleted();
        UserAdministrationViewModel viewModel = new UserAdministrationViewModel(service, new AllowPermissions(
                SecurityPermissions.USERS_UPDATE,
                SecurityPermissions.USERS_RESET_PASSWORD,
                SecurityPermissions.USERS_DELETE));
        AtomicReference<TableView<UserSummary>> table = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Parent root = loadScreen(viewModel, 960, 640);
                table.set((TableView<UserSummary>) root.lookup("#userTable"));
                viewModel.refresh();
                table.get().layout();
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

        CountDownLatch menuLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                TableRow<UserSummary> activeRow = firstNonEmptyRow(table.get());
                ContextMenu menu = activeRow.getContextMenu();
                assertNotNull(menu);
                assertEquals(4, menu.getItems().size());
                assertTrue(menu.getItems().get(2) instanceof SeparatorMenuItem);
                MenuItem deleteItem = (MenuItem) menu.getItems().get(3);
                assertTrue(deleteItem.getStyleClass().contains("tmp-menu-item-danger"));
                assertFalse(deleteItem.isDisable());

                TableRow<UserSummary> emptyRow = findEmptyRow(table.get());
                assertNull(emptyRow.getContextMenu());
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                menuLatch.countDown();
            }
        });
        assertTrue(menuLatch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
    }

    @Test
    void contextMenuItemsHiddenWithoutPermissions() throws Exception {
        UserAdministrationViewModel viewModel = new UserAdministrationViewModel(
                new UsersWithActiveAndDeleted(), new DenyAll());
        AtomicReference<TableView<UserSummary>> table = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Parent root = loadScreen(viewModel, 960, 640);
                table.set((TableView<UserSummary>) root.lookup("#userTable"));
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

        CountDownLatch menuLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                TableRow<UserSummary> row = firstNonEmptyRow(table.get());
                assertNull(row.getContextMenu());
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                menuLatch.countDown();
            }
        });
        assertTrue(menuLatch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
    }

    @Test
    void deletedUserActionsDisabledWhenVisible() throws Exception {
        UsersWithActiveAndDeleted service = new UsersWithActiveAndDeleted();
        UserAdministrationViewModel viewModel = new UserAdministrationViewModel(service, new AllowAll());
        AtomicReference<TableView<UserSummary>> table = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Parent root = loadScreen(viewModel, 960, 640);
                table.set((TableView<UserSummary>) root.lookup("#userTable"));
                CheckBox showDeleted = (CheckBox) root.lookup("#showDeletedCheckBox");
                showDeleted.setSelected(true);
                table.get().layout();
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

        CountDownLatch menuLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                TableRow<UserSummary> deletedRow = rowForLogin(table.get(), "deleted");
                ContextMenu menu = deletedRow.getContextMenu();
                assertNotNull(menu);
                MenuItem editItem = (MenuItem) menu.getItems().get(0);
                MenuItem resetItem = (MenuItem) menu.getItems().get(1);
                MenuItem deleteItem = (MenuItem) menu.getItems().get(3);
                assertTrue(editItem.isDisable());
                assertTrue(resetItem.isDisable());
                assertTrue(deleteItem.isDisable());
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                menuLatch.countDown();
            }
        });
        assertTrue(menuLatch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
    }

    @Test
    void tableUsesBoundedCompactWidthOnWideScene() throws Exception {
        UserAdministrationViewModel viewModel = new UserAdministrationViewModel(
                new UsersWithActiveAndDeleted(), new AllowAll());
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<Double> tableWidth = new AtomicReference<>();
        AtomicReference<Double> sceneWidth = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Parent root = loadScreen(viewModel, 1600, 900);
                TableView<?> table = (TableView<?>) root.lookup("#userTable");
                tableWidth.set(table.getWidth() > 0 ? table.getWidth() : table.prefWidth(-1));
                sceneWidth.set(root.getLayoutBounds().getWidth());
                rootRef.set(root);
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
        assertTrue(tableWidth.get() < sceneWidth.get() - 48);
        assertTrue(tableWidth.get() <= 760 + 32);
    }

    @Test
    void layoutRemainsUsableAtCommonResolutions() throws Exception {
        UserAdministrationViewModel viewModel = new UserAdministrationViewModel(
                new UsersWithActiveAndDeleted(), new AllowAll());
        int[][] sizes = {{1024, 700}, {1366, 768}, {1600, 900}};
        for (int[] size : sizes) {
            AtomicReference<Parent> rootRef = new AtomicReference<>();
            AtomicReference<Throwable> error = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    Parent root = loadScreen(viewModel, size[0], size[1]);
                    root.applyCss();
                    root.layout();
                    rootRef.set(root);
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
            Parent root = rootRef.get();
            TableView<?> table = (TableView<?>) root.lookup("#userTable");
            Button createButton = (Button) root.lookup("#createUserButton");
            CheckBox showDeleted = (CheckBox) root.lookup("#showDeletedCheckBox");
            assertTrue(createButton.isVisible());
            assertTrue(showDeleted.isVisible());
            assertTrue(table.getWidth() > 0 || table.prefWidth(-1) > 0);
            assertTrue(table.getHeight() > 0 || table.prefHeight(-1) > 0);
        }
    }

    private static Parent loadScreen(UserAdministrationViewModel viewModel, double width, double height)
            throws Exception {
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                "users",
                "com/tmp/ui/shell/screen/useradmin/UserAdministrationScreen.fxml",
                () -> viewModel));
        Parent root = navigation.load("users");
        Scene scene = new Scene(root, width, height);
        TmpTheme.apply(scene);
        root.applyCss();
        root.layout();
        return root;
    }

    private static Node findByStyleClass(Parent root, String styleClass) {
        if (root.getStyleClass().contains(styleClass)) {
            return root;
        }
        for (Node child : root.lookupAll("*")) {
            if (child.getStyleClass().contains(styleClass)) {
                return child;
            }
        }
        return null;
    }

    private static String labelText(Node node) {
        return node instanceof Label label ? label.getText() : "";
    }

    @SuppressWarnings("unchecked")
    private static Label findStatusBadge(TableView<UserSummary> table, int rowIndex) {
        table.layout();
        for (Node node : table.lookupAll(".table-row-cell")) {
            if (node instanceof TableRow<?> row && row.getIndex() == rowIndex && !row.isEmpty()) {
                for (Node cellNode : row.lookupAll(".table-cell")) {
                    if (cellNode instanceof TableCell<?, ?> cell && cell.getGraphic() instanceof Parent graphic) {
                        for (Node badgeCandidate : graphic.lookupAll(".label")) {
                            if (badgeCandidate instanceof Label label
                                    && label.getStyleClass().contains("tmp-badge")) {
                                return label;
                            }
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("Status badge not found for row " + rowIndex);
    }

    @SuppressWarnings("unchecked")
    private static TableRow<UserSummary> firstNonEmptyRow(TableView<UserSummary> table) {
        table.layout();
        for (Node node : table.lookupAll(".table-row-cell")) {
            if (node instanceof TableRow<?> row && !row.isEmpty()) {
                return (TableRow<UserSummary>) row;
            }
        }
        throw new IllegalStateException("No data row found");
    }

    @SuppressWarnings("unchecked")
    private static TableRow<UserSummary> findEmptyRow(TableView<UserSummary> table) {
        table.layout();
        for (Node node : table.lookupAll(".table-row-cell")) {
            if (node instanceof TableRow<?> row && row.isEmpty()) {
                return (TableRow<UserSummary>) row;
            }
        }
        throw new IllegalStateException("No empty row found");
    }

    @SuppressWarnings("unchecked")
    private static TableRow<UserSummary> rowForLogin(TableView<UserSummary> table, String login) {
        table.layout();
        for (Node node : table.lookupAll(".table-row-cell")) {
            if (node instanceof TableRow<?> row
                    && !row.isEmpty()
                    && row.getItem() instanceof UserSummary user
                    && login.equals(user.login().value())) {
                return (TableRow<UserSummary>) row;
            }
        }
        throw new IllegalStateException("Row not found for login " + login);
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

    private static final class DenyAll implements AuthorizationService {
        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return false;
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of();
        }
    }

    private static final class AllowPermissions implements AuthorizationService {
        private final Set<PermissionId> allowed;

        @SafeVarargs
        private AllowPermissions(PermissionId... permissions) {
            allowed = Set.of(permissions);
        }

        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return allowed.contains(permissionId);
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return allowed;
        }
    }
}
