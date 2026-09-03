package com.tmp.ui.shell.screen.orderitemlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.ItemSpecificationDto;
import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemRevisionDto;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderSearchCriteria;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.OrderSummaryDto;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.order.api.RevisionNumber;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrderItemListControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void loadsOrderItemListFxmlWithRequiredColumnsOnly() throws Exception {
        FakeQuery query = new FakeQuery();
        OrderId orderId = OrderId.generate();
        query.items = List.of(item(orderId, OrderItemId.generate()));
        OrderItemListViewModel viewModel =
                new OrderItemListViewModel(
                        query,
                        new FakeAuthorization(
                                Set.of(
                                        PermissionId.of("order.item.view"),
                                        PermissionId.of("order.item.create"))));
        viewModel.openForOrder(orderId, OrderStatus.DRAFT);
        Parent root = loadList(viewModel);

        @SuppressWarnings("unchecked")
        TableView<OrderItemListRow> table = (TableView<OrderItemListRow>) root.lookup("#itemsTable");
        assertNotNull(table);
        assertEquals(4, table.getColumns().size());
        assertEquals("Код позиции", table.getColumns().get(0).getText());
        assertEquals("Наименование", table.getColumns().get(1).getText());
        assertEquals("Количество", table.getColumns().get(2).getText());
        assertEquals("Статус", table.getColumns().get(3).getText());
        for (TableColumn<?, ?> column : table.getColumns()) {
            assertTrue(!"Активная редакция".equals(column.getText()));
        }
        assertNull(root.lookup("#openItemButton"));
        assertNull(root.lookup("#activeRevisionColumn"));
        Button create = (Button) root.lookup("#createItemButton");
        assertNotNull(create);
        assertTrue(create.isVisible());
    }

    @Test
    void enterOpensSelectedRow() throws Exception {
        FakeQuery query = new FakeQuery();
        OrderId orderId = OrderId.generate();
        OrderItemId itemId = OrderItemId.generate();
        query.items = List.of(item(orderId, itemId));
        OrderItemListViewModel viewModel =
                new OrderItemListViewModel(query, new FakeAuthorization());
        AtomicBoolean opened = new AtomicBoolean(false);
        viewModel.setOnOpenItem(id -> opened.set(id.equals(itemId)));
        viewModel.openForOrder(orderId, OrderStatus.DRAFT);

        Parent root = loadList(viewModel);
        @SuppressWarnings("unchecked")
        TableView<OrderItemListRow> table = (TableView<OrderItemListRow>) root.lookup("#itemsTable");

        runFx(
                () -> {
                    table.getSelectionModel().select(0);
                    table.getOnKeyPressed()
                            .handle(
                                    new javafx.scene.input.KeyEvent(
                                            javafx.scene.input.KeyEvent.KEY_PRESSED,
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

    private static Parent loadList(OrderItemListViewModel viewModel) throws Exception {
        var navigation = NavigationServices.createDefault();
        navigation.register(
                new ScreenRegistration(
                        UiShellScreens.ORDER_ITEM_LIST_SCREEN_ID,
                        UiShellScreens.ORDER_ITEM_LIST_FXML,
                        () -> viewModel));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Parent> rootRef = new AtomicReference<>();

        Platform.runLater(
                () -> {
                    try {
                        Parent root = navigation.load(UiShellScreens.ORDER_ITEM_LIST_SCREEN_ID);
                        Stage stage = new Stage();
                        stage.setScene(new Scene(root));
                        rootRef.set(root);
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Order item list FX load failed", error.get());
        }
        return rootRef.get();
    }

    private static void runFx(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(
                () -> {
                    try {
                        action.run();
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("FX action failed", error.get());
        }
    }

    private static OrderItemDto item(OrderId orderId, OrderItemId itemId) {
        Instant now = Instant.parse("2026-07-27T10:00:00Z");
        return OrderItemDto.of(
                itemId, orderId, "P-1", "Panel", null, null, OrderItemStatus.DRAFT, null, now, now);
    }

    private static final class FakeQuery implements OrderQueryService {
        private List<OrderItemDto> items = List.of();

        @Override
        public PageResult<OrderSummaryDto> searchOrders(
                OrderSearchCriteria criteria, PageRequest pageRequest) {
            return PageResult.of(List.of(), 0, pageRequest.pageSize(), 0);
        }

        @Override
        public Optional<OrderDto> getOrder(OrderId orderId) {
            return Optional.empty();
        }

        @Override
        public PageResult<OrderItemDto> getOrderItems(OrderId orderId, PageRequest pageRequest) {
            return PageResult.of(items, 0, pageRequest.pageSize(), items.size());
        }

        @Override
        public Optional<OrderItemDto> getOrderItem(OrderItemId orderItemId) {
            return Optional.empty();
        }

        @Override
        public PageResult<OrderItemRevisionDto> getOrderItemRevisions(
                OrderItemId orderItemId, PageRequest pageRequest) {
            return PageResult.of(List.of(), 0, pageRequest.pageSize(), 0);
        }

        @Override
        public Optional<OrderItemRevisionDto> getOrderItemRevision(
                OrderItemId orderItemId, RevisionNumber revisionNumber) {
            return Optional.empty();
        }

        @Override
        public Optional<OrderItemRevisionDto> getActiveOrderItemRevision(OrderItemId orderItemId) {
            return Optional.empty();
        }

        @Override
        public Optional<ItemSpecificationDto> getItemSpecification(
                OrderItemId orderItemId, RevisionNumber revisionNumber) {
            return Optional.empty();
        }

        @Override
        public Optional<com.tmp.order.api.ProductionSpecificationDto> getCurrentItemSpecification(
                OrderItemId orderItemId) {
            return Optional.empty();
        }

        @Override
        public Optional<com.tmp.order.api.ProductionSpecificationDto> getSpecificationById(
                com.tmp.order.api.SpecificationId specificationId) {
            return Optional.empty();
        }

        @Override
        public Optional<com.tmp.order.api.OrderForProductionDto> getOrderForProduction(OrderId orderId) {
            return Optional.empty();
        }
    }
}
