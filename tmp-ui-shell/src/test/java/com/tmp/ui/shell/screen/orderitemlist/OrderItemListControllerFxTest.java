package com.tmp.ui.shell.screen.orderitemlist;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.ItemSpecificationDto;
import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemRevisionDto;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderSearchCriteria;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.OrderSummaryDto;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.order.api.RevisionNumber;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrderItemListControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void loadsOrderItemListFxml() throws Exception {
        OrderItemListViewModel viewModel =
                new OrderItemListViewModel(new EmptyQuery(), new FakeAuthorization());
        viewModel.openForOrder(OrderId.generate(), OrderStatus.DRAFT);
        var navigation = NavigationServices.createDefault();
        navigation.register(
                new ScreenRegistration(
                        UiShellScreens.ORDER_ITEM_LIST_SCREEN_ID,
                        UiShellScreens.ORDER_ITEM_LIST_FXML,
                        () -> viewModel));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<TableView<?>> table = new AtomicReference<>();

        Platform.runLater(
                () -> {
                    try {
                        Parent root = navigation.load(UiShellScreens.ORDER_ITEM_LIST_SCREEN_ID);
                        Stage stage = new Stage();
                        stage.setScene(new Scene(root));
                        table.set((TableView<?>) root.lookup("#itemsTable"));
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
        assertNotNull(table.get());
    }

    private static final class EmptyQuery implements OrderQueryService {
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
            return PageResult.of(List.of(), 0, pageRequest.pageSize(), 0);
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
