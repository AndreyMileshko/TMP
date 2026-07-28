package com.tmp.ui.shell.screen.orderlist;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrderListControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void loadsOrderListFxml() throws Exception {
        OrderListViewModel viewModel = new OrderListViewModel(new EmptyOrderQuery(), new FakeAuthorization());
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                UiShellScreens.ORDER_LIST_SCREEN_ID,
                UiShellScreens.ORDER_LIST_FXML,
                () -> viewModel));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Label> title = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Parent root = navigation.load(UiShellScreens.ORDER_LIST_SCREEN_ID);
                Stage stage = new Stage();
                stage.setScene(new Scene(root));
                title.set((Label) root.lookup("#titleLabel"));
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Order list FX load failed", error.get());
        }
        assertNotNull(title.get());
    }

    @Test
    void createOrderButtonReflectsAuthorizationAfterRefresh() throws Exception {
        OrderListViewModel viewModel = new OrderListViewModel(
                new EmptyOrderQuery(),
                new FakeAuthorization(
                        PermissionId.of(UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION),
                        PermissionId.of(UiShellScreens.ORDER_CREATE_PERMISSION)));
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                UiShellScreens.ORDER_LIST_SCREEN_ID,
                UiShellScreens.ORDER_LIST_FXML,
                () -> viewModel));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Button> createButton = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Parent root = navigation.load(UiShellScreens.ORDER_LIST_SCREEN_ID);
                Stage stage = new Stage();
                stage.setScene(new Scene(root));
                createButton.set((Button) root.lookup("#createOrderButton"));
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Order list FX load failed", error.get());
        }
        assertNotNull(createButton.get());
        assertFalse(createButton.get().isDisabled());

        OrderListViewModel viewOnly = new OrderListViewModel(
                new EmptyOrderQuery(),
                new FakeAuthorization(PermissionId.of(UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION)));
        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicReference<Button> disabledButton = new AtomicReference<>();
        navigation.register(new ScreenRegistration(
                "order-list-view-only",
                UiShellScreens.ORDER_LIST_FXML,
                () -> viewOnly));
        Platform.runLater(() -> {
            try {
                Parent root = navigation.load("order-list-view-only");
                disabledButton.set((Button) root.lookup("#createOrderButton"));
            } finally {
                latch2.countDown();
            }
        });
        assertTrue(latch2.await(10, TimeUnit.SECONDS));
        assertTrue(disabledButton.get().isDisabled());
    }
}
