package com.tmp.ui.shell.screen.orderlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderStatus;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import com.tmp.ui.shell.order.worklist.DateTimePresentation;
import com.tmp.ui.shell.order.worklist.OrderOperationalStatus;
import com.tmp.ui.shell.order.worklist.OrderOperationalSummary;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrderListControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void modernListHasRequiredColumnsAndNoOpenOrCustomerRef() throws Exception {
        OrderListViewModel viewModel = OrderListTestSupport.viewModel();
        Parent root = load(viewModel);
        assertEquals("Заказы", ((Label) root.lookup("#titleLabel")).getText());
        assertTrue(((Label) root.lookup("#subtitleLabel")).getText().contains("заказами"));
        assertNotNull(root.lookup(".tmp-screen-header"));
        assertNull(root.lookup("#openOrderButton"));
        assertNull(root.lookup("#customerRefColumn"));
        TableView<?> table = (TableView<?>) root.lookup("#ordersTable");
        assertEquals(5, table.getColumns().size());
        assertEquals("Номер заказа", ((TableColumn<?, ?>) table.getColumns().get(0)).getText());
        assertEquals("Заказчик", ((TableColumn<?, ?>) table.getColumns().get(1)).getText());
        assertEquals("Дата создания", ((TableColumn<?, ?>) table.getColumns().get(2)).getText());
        assertEquals("Изделий", ((TableColumn<?, ?>) table.getColumns().get(3)).getText());
        assertEquals("Статус", ((TableColumn<?, ?>) table.getColumns().get(4)).getText());
        assertNotNull(root.lookup("#quickSearchField"));
        assertNotNull(root.lookup("#customerFilterButton"));
        assertNotNull(root.lookup("#periodCombo"));
        assertTrue(((CheckBox) root.lookup("#statusEditingCheck")).isSelected());
        assertFalse(((CheckBox) root.lookup("#statusCancelledCheck")).isSelected());
        assertNotNull(table.getItems());
        assertEquals("← Предыдущая", ((Button) root.lookup("#previousPageButton")).getText());
        assertEquals("Следующая →", ((Button) root.lookup("#nextPageButton")).getText());
    }

    @Test
    void createOrderButtonReflectsAuthorizationAfterRefresh() throws Exception {
        OrderListViewModel allowed = OrderListTestSupport.viewModel(
                new FakeAuthorization(
                        PermissionId.of(UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION),
                        PermissionId.of(UiShellScreens.ORDER_CREATE_PERMISSION)));
        Parent root = load(allowed);
        assertFalse(((Button) root.lookup("#createOrderButton")).isDisabled());

        OrderListViewModel viewOnly = OrderListTestSupport.viewModel(
                new FakeAuthorization(PermissionId.of(UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION)));
        Parent disabledRoot = load(viewOnly, "order-list-view-only");
        assertTrue(((Button) disabledRoot.lookup("#createOrderButton")).isDisabled());
    }

    @Test
    void dateFormatIsDayMonthYearHoursMinutes() throws Exception {
        Instant instant = Instant.parse("2026-09-02T10:19:00Z");
        assertEquals("02.09.2026 10:19", DateTimePresentation.format(instant, ZoneOffset.UTC));
    }

    @Test
    void enterOnSelectedRowOpensOrder() throws Exception {
        OrderListTestSupport.InMemoryWorklistQuery worklist = new OrderListTestSupport.InMemoryWorklistQuery();
        worklist.rows.add(
                OrderListTestSupport.row(
                        "OPEN-1",
                        OrderStatus.DRAFT,
                        "c-1",
                        "Alpha",
                        Instant.parse("2026-09-01T10:00:00Z")));
        OrderListViewModel viewModel =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(
                                PermissionId.of(UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION),
                                PermissionId.of(UiShellScreens.ORDER_CREATE_PERMISSION)),
                        new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId()),
                        new OrderListTestSupport.InMemoryPreferences());
        viewModel.refresh();
        AtomicBoolean opened = new AtomicBoolean();
        viewModel.setOnOpenOrder(id -> opened.set(true));
        Parent root = load(viewModel);
        JavaFxTestSupport.runOnFxThread(() -> {
            @SuppressWarnings("unchecked")
            TableView<OrderOperationalSummary> table =
                    (TableView<OrderOperationalSummary>) root.lookup("#ordersTable");
            table.getSelectionModel().select(0);
            table.fireEvent(
                    new KeyEvent(
                            KeyEvent.KEY_PRESSED,
                            "",
                            "",
                            KeyCode.ENTER,
                            false,
                            false,
                            false,
                            false));
        });
        assertTrue(opened.get());
    }

    @Test
    void searchFieldIsPresentWithPlaceholder() throws Exception {
        Parent root = load(OrderListTestSupport.viewModel());
        TextField search = (TextField) root.lookup("#quickSearchField");
        assertEquals("Поиск по номеру или заказчику...", search.getPromptText());
    }

    @Test
    void lastStatusCheckboxCannotBeClearedInUi() throws Exception {
        OrderListViewModel viewModel = OrderListTestSupport.viewModel();
        viewModel.selectedStatuses().clear();
        viewModel.selectedStatuses().add(OrderOperationalStatus.EDITING);
        Parent root = load(viewModel, "order-list-last-status");
        JavaFxTestSupport.runOnFxThread(() -> {
            CheckBox editing = (CheckBox) root.lookup("#statusEditingCheck");
            assertTrue(editing.isSelected());
            assertTrue(editing.isDisable());
            editing.setSelected(false);
            assertTrue(editing.isSelected());
            assertTrue(viewModel.selectedStatuses().contains(OrderOperationalStatus.EDITING));
        });
    }

    @Test
    void layoutAtRepresentativeSizesKeepsCriticalControls() throws Exception {
        layoutAtSize(1024, 700);
        layoutAtSize(1366, 768);
        layoutAtSize(1920, 1080);
    }

    @Test
    void selectingAlreadyVisibleRowDoesNotMoveVerticalScrollbar() throws Exception {
        OrderListViewModel viewModel = populatedListViewModel(100);
        ShownTable shown = showTable(viewModel, "order-list-scroll-visible");
        try {
            JavaFxTestSupport.runOnFxThread(
                    () -> {
                        shown.table().scrollTo(35);
                        shown.table().applyCss();
                        shown.root().applyCss();
                        shown.root().layout();
                    });
            JavaFxTestSupport.runOnFxThread(
                    () -> {
                        javafx.scene.control.ScrollBar bar = verticalBar(shown.table());
                        assertNotNull(bar);
                        assertTrue(bar.isVisible());
                        double before = bar.getValue();
                        shown.table().getSelectionModel().select(38);
                        shown.table().layout();
                        assertEquals(
                                viewModel.orders().get(38).orderId(),
                                viewModel.selectedOrderProperty().get().orderId());
                        assertEquals(38, shown.table().getSelectionModel().getSelectedIndex());
                        assertEquals(before, bar.getValue(), 0.01);
                        shown.table().getSelectionModel().select(42);
                        shown.table().layout();
                        assertEquals(42, shown.table().getSelectionModel().getSelectedIndex());
                        assertEquals(before, bar.getValue(), 0.01);
                    });
        } finally {
            close(shown);
        }
    }

    @Test
    void programmaticRestoreScrollsFarRowIntoView() throws Exception {
        OrderListViewModel viewModel = populatedListViewModel(100);
        ShownTable shown = showTable(viewModel, "order-list-scroll-restore");
        try {
            JavaFxTestSupport.runOnFxThread(
                    () -> {
                        shown.table().scrollTo(0);
                        shown.table().applyCss();
                        shown.root().layout();
                    });
            JavaFxTestSupport.runOnFxThread(
                    () -> {
                        javafx.scene.control.ScrollBar bar = verticalBar(shown.table());
                        assertNotNull(bar);
                        double before = bar.getValue();
                        OrderOperationalSummary far = viewModel.orders().get(85);
                        viewModel.selectedOrderProperty().set(far);
                        shown.table().layout();
                        assertEquals(far.orderId(), shown.table().getSelectionModel().getSelectedItem().orderId());
                        assertTrue(
                                bar.getValue() > before + 0.05,
                                "programmatic restore must scroll far row into view");
                    });
        } finally {
            close(shown);
        }
    }

    @Test
    void mementoRestoreReselectsOrderAfterBind() throws Exception {
        OrderListViewModel viewModel = populatedListViewModel(100);
        viewModel.refresh();
        viewModel.selectedOrderProperty().set(viewModel.orders().get(12));
        var memento = viewModel.captureMemento();
        viewModel.selectedOrderProperty().set(viewModel.orders().get(0));
        viewModel.restoreMemento(memento);
        Parent root = load(viewModel, "order-list-memento-bind");
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    @SuppressWarnings("unchecked")
                    TableView<OrderOperationalSummary> table =
                            (TableView<OrderOperationalSummary>) root.lookup("#ordersTable");
                    assertEquals(
                            memento.selectedOrderId(),
                            table.getSelectionModel().getSelectedItem().orderId());
                });
    }

    private static OrderListViewModel populatedListViewModel(int rows) {
        OrderListTestSupport.InMemoryWorklistQuery worklist = new OrderListTestSupport.InMemoryWorklistQuery();
        Instant created = Instant.parse("2026-09-01T10:00:00Z");
        for (int i = 0; i < rows; i++) {
            worklist.rows.add(
                    OrderListTestSupport.row(
                            "ORD-" + String.format("%03d", i),
                            OrderStatus.DRAFT,
                            "c-1",
                            "Alpha",
                            created));
        }
        OrderListViewModel viewModel =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(
                                PermissionId.of(UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION),
                                PermissionId.of(UiShellScreens.ORDER_CREATE_PERMISSION)),
                        new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId()),
                        new OrderListTestSupport.InMemoryPreferences());
        viewModel.pageSizeProperty().set(100);
        viewModel.refresh();
        return viewModel;
    }

    private static ShownTable showTable(OrderListViewModel viewModel, String screenId) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<ShownTable> shown = new AtomicReference<>();
        Platform.runLater(
                () -> {
                    try {
                        var navigation = NavigationServices.createDefault();
                        navigation.register(
                                new ScreenRegistration(
                                        screenId, UiShellScreens.ORDER_LIST_FXML, () -> viewModel));
                        Parent loaded = navigation.load(screenId);
                        Stage stage = new Stage();
                        stage.setScene(new Scene(loaded, 1024, 420));
                        stage.show();
                        loaded.applyCss();
                        loaded.layout();
                        @SuppressWarnings("unchecked")
                        TableView<OrderOperationalSummary> table =
                                (TableView<OrderOperationalSummary>) loaded.lookup("#ordersTable");
                        table.setPrefHeight(220);
                        table.setMinHeight(220);
                        table.setMaxHeight(220);
                        loaded.layout();
                        shown.set(new ShownTable(loaded, stage, table));
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });
        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Order list FX show failed", error.get());
        }
        return shown.get();
    }

    private static void close(ShownTable shown) throws Exception {
        JavaFxTestSupport.runOnFxThread(() -> shown.stage().close());
    }

    private static javafx.scene.control.ScrollBar verticalBar(TableView<?> table) {
        for (javafx.scene.Node node : table.lookupAll(".scroll-bar")) {
            if (node instanceof javafx.scene.control.ScrollBar bar
                    && bar.getOrientation() == javafx.geometry.Orientation.VERTICAL
                    && bar.isVisible()) {
                return bar;
            }
        }
        return null;
    }

    private record ShownTable(Parent root, Stage stage, TableView<OrderOperationalSummary> table) {}

    private static void layoutAtSize(int width, int height) throws Exception {
        OrderListViewModel viewModel = OrderListTestSupport.viewModel();
        viewModel.refresh();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                var navigation = NavigationServices.createDefault();
                navigation.register(
                        new ScreenRegistration(
                                "order-list-resize-" + width,
                                UiShellScreens.ORDER_LIST_FXML,
                                () -> viewModel));
                Parent loaded = navigation.load("order-list-resize-" + width);
                Stage stage = new Stage();
                stage.setScene(new Scene(loaded, width, height));
                stage.show();
                loaded.applyCss();
                loaded.autosize();
                assertNotNull(loaded.lookup("#ordersTable"));
                assertNotNull(loaded.lookup("#quickSearchField"));
                assertNotNull(loaded.lookup("#customerFilterButton"));
                assertNotNull(loaded.lookup("#periodCombo"));
                assertNotNull(loaded.lookup("#statusEditingCheck"));
                assertNotNull(loaded.lookup("#previousPageButton"));
                assertNotNull(loaded.lookup("#nextPageButton"));
                assertTrue(loaded.lookup("#ordersTable").getLayoutBounds().getWidth() > 0);
                stage.close();
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Orders resize layout failed at " + width + "x" + height, error.get());
        }
    }

    private static Parent load(OrderListViewModel viewModel) throws Exception {
        return load(viewModel, UiShellScreens.ORDER_LIST_SCREEN_ID);
    }

    private static Parent load(OrderListViewModel viewModel, String screenId) throws Exception {
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(screenId, UiShellScreens.ORDER_LIST_FXML, () -> viewModel));
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Parent> root = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                Parent loaded = navigation.load(screenId);
                Stage stage = new Stage();
                stage.setScene(new Scene(loaded));
                root.set(loaded);
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
        return root.get();
    }
}
