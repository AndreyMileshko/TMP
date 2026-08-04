package com.tmp.ui.shell.screen.ordereditor;

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
import com.tmp.order.api.OrderSummaryDto;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.ui.OrderDocumentUiService;
import com.tmp.order.api.ui.OrderHeaderDraft;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrderEditorControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void loadsOrderEditorFxml() throws Exception {
        OrderEditorViewModel viewModel = new OrderEditorViewModel(
                new EmptyQuery(), new EmptyDocs(), new FakeAuthorization());
        viewModel.openCreate();
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                UiShellScreens.ORDER_EDITOR_SCREEN_ID,
                UiShellScreens.ORDER_EDITOR_FXML,
                () -> viewModel));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<TextField> field = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Parent root = navigation.load(UiShellScreens.ORDER_EDITOR_SCREEN_ID);
                Stage stage = new Stage();
                stage.setScene(new Scene(root));
                field.set((TextField) root.lookup("#orderNumberField"));
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Order editor FX load failed", error.get());
        }
        assertNotNull(field.get());
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
    }

    private static final class EmptyDocs implements OrderDocumentUiService {
        @Override
        public UUID beginOrderCreate(String title) {
            return UUID.randomUUID();
        }

        @Override
        public UUID beginOrderUpdate(String title, OrderId orderId) {
            return UUID.randomUUID();
        }

        @Override
        public UUID beginOrderApprove(String title, OrderId orderId) {
            return UUID.randomUUID();
        }

        @Override
        public UUID beginOrderActivate(String title, OrderId orderId) {
            return UUID.randomUUID();
        }

        @Override
        public UUID beginOrderCancel(String title, OrderId orderId) {
            return UUID.randomUUID();
        }

        @Override
        public long saveCreateDraft(
                UUID documentId, OrderHeaderDraft draft, long expectedPayloadRevision) {
            return 0L;
        }

        @Override
        public long saveUpdateDraft(
                UUID documentId,
                OrderId orderId,
                OrderHeaderDraft draft,
                long expectedPayloadRevision) {
            return 0L;
        }

        @Override
        public OrderId postDocument(UUID documentId) {
            return OrderId.generate();
        }

        @Override
        public Optional<OrderHeaderDraft> loadCreateDraft(UUID documentId) {
            return Optional.empty();
        }

        @Override
        public Optional<OrderHeaderDraft> loadUpdateDraft(UUID documentId) {
            return Optional.empty();
        }
    }
}
