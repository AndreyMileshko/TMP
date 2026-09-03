package com.tmp.ui.shell.screen.ordereditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.ui.OrderDocumentUiService;
import com.tmp.order.api.ui.OrderHeaderDraft;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.navigation.ShellHistoryEntry;
import com.tmp.ui.shell.navigation.ShellNavigationHistory;
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Create-route / history stabilization proofs for Stage 3.4.4 Part A.
 */
class OrderEditorCreateNavigationTest {

    @Test
    void openCreateAlwaysClearsPreviousOrderId() {
        FakeOrderQuery query = new FakeOrderQuery();
        OrderId existing = OrderId.generate();
        query.order = order(existing, "A-1", OrderStatus.DRAFT);
        OrderEditorViewModel viewModel =
                new OrderEditorViewModel(
                        query,
                        new FakeDocuments(),
                        createAuth(),
                        new EmptyProductionQuery());
        viewModel.openExisting(existing);
        assertEquals(existing, viewModel.currentOrderId());

        viewModel.openCreate();

        assertNull(viewModel.currentOrderId());
        assertEquals(OrderEditorViewModel.Mode.CREATE, viewModel.modeProperty().get());
        assertEquals("Новый заказ", viewModel.titleProperty().get());
        assertEquals("", viewModel.orderNumberProperty().get());
    }

    @Test
    void saveFromCreateInvokesOnOrderCreatedWithNewId() {
        FakeDocuments documents = new FakeDocuments();
        OrderId created = OrderId.generate();
        documents.createdId = created;
        FakeOrderQuery query = new FakeOrderQuery();
        query.order = order(created, "B-1", OrderStatus.DRAFT);
        OrderEditorViewModel viewModel =
                new OrderEditorViewModel(query, documents, createAuth(), new EmptyProductionQuery());
        AtomicReference<OrderId> opened = new AtomicReference<>();
        viewModel.setOnOrderCreated(opened::set);
        viewModel.openCreate();
        viewModel.orderNumberProperty().set("B-1");
        viewModel.customerNameProperty().set("Customer");

        viewModel.save();

        assertEquals(created, opened.get());
        assertEquals(OrderEditorViewModel.Mode.VIEW_EXISTING, viewModel.modeProperty().get());
    }

    @Test
    void historyReplaceAfterCreateMakesBackForwardStableExistingRoute() {
        FakeDocuments documents = new FakeDocuments();
        OrderId created = OrderId.generate();
        documents.createdId = created;
        FakeOrderQuery query = new FakeOrderQuery();
        query.order = order(created, "B-2", OrderStatus.DRAFT);
        OrderEditorViewModel viewModel =
                new OrderEditorViewModel(query, documents, createAuth(), new EmptyProductionQuery());

        ShellNavigationHistory history = new ShellNavigationHistory();
        ShellHistoryEntry listEntry =
                ShellHistoryEntry.of(
                        UiShellScreens.ORDER_LIST_SCREEN_ID,
                        UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION,
                        () -> {});
        ShellHistoryEntry createEntry =
                ShellHistoryEntry.of(
                        UiShellScreens.ORDER_EDITOR_SCREEN_ID,
                        UiShellScreens.ORDER_CREATE_PERMISSION,
                        viewModel::openCreate);
        history.navigate(listEntry);
        history.navigate(createEntry);
        viewModel.openCreate();

        viewModel.setOnOrderCreated(
                orderId ->
                        history.replaceCurrent(
                                ShellHistoryEntry.of(
                                        UiShellScreens.ORDER_EDITOR_SCREEN_ID,
                                        UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION,
                                        () -> viewModel.openExisting(orderId))));
        viewModel.orderNumberProperty().set("B-2");
        viewModel.customerNameProperty().set("Customer");
        viewModel.save();

        history.navigate(
                ShellHistoryEntry.of(
                        UiShellScreens.ORDER_LIST_SCREEN_ID,
                        UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION,
                        () -> {}));
        assertTrue(history.canGoBack());
        history.goBack(e -> true).orElseThrow().restore().run();

        assertEquals(created, viewModel.currentOrderId());
        assertEquals(OrderEditorViewModel.Mode.VIEW_EXISTING, viewModel.modeProperty().get());
    }

    @Test
    void createHistoryEntryRequiresCreatePermission() {
        ShellHistoryEntry createEntry =
                ShellHistoryEntry.of(
                        UiShellScreens.ORDER_EDITOR_SCREEN_ID,
                        UiShellScreens.ORDER_CREATE_PERMISSION,
                        () -> {});
        assertEquals(UiShellScreens.ORDER_CREATE_PERMISSION, createEntry.requiredPermission());
    }

    private static FakeAuthorization createAuth() {
        return new FakeAuthorization(
                Set.of(
                        PermissionId.of("order.order.view"),
                        PermissionId.of("order.order.create"),
                        PermissionId.of("order.order.edit"),
                        PermissionId.of("order.item.view")));
    }

    private static OrderDto order(OrderId id, String number, OrderStatus status) {
        Instant now = Instant.parse("2026-09-03T00:00:00Z");
        return OrderDto.of(
                id,
                number,
                status,
                null,
                "Customer",
                null,
                null,
                null,
                "PRIVATE",
                "RUB",
                now,
                now);
    }

    private static final class FakeOrderQuery implements OrderQueryService {
        private OrderDto order;

        @Override
        public Optional<OrderDto> getOrder(OrderId orderId) {
            return Optional.ofNullable(order);
        }

        @Override
        public com.tmp.order.api.PageResult<com.tmp.order.api.OrderSummaryDto> searchOrders(
                com.tmp.order.api.OrderSearchCriteria criteria,
                com.tmp.order.api.PageRequest pageRequest) {
            return com.tmp.order.api.PageResult.of(java.util.List.of(), 0, pageRequest.pageSize(), 0);
        }

        @Override
        public com.tmp.order.api.PageResult<com.tmp.order.api.OrderItemDto> getOrderItems(
                OrderId orderId, com.tmp.order.api.PageRequest pageRequest) {
            return com.tmp.order.api.PageResult.of(java.util.List.of(), 0, pageRequest.pageSize(), 0);
        }

        @Override
        public Optional<com.tmp.order.api.OrderItemDto> getOrderItem(
                com.tmp.order.api.OrderItemId orderItemId) {
            return Optional.empty();
        }

        @Override
        public com.tmp.order.api.PageResult<com.tmp.order.api.OrderItemRevisionDto>
                getOrderItemRevisions(
                        com.tmp.order.api.OrderItemId orderItemId,
                        com.tmp.order.api.PageRequest pageRequest) {
            return com.tmp.order.api.PageResult.of(java.util.List.of(), 0, pageRequest.pageSize(), 0);
        }

        @Override
        public Optional<com.tmp.order.api.OrderItemRevisionDto> getOrderItemRevision(
                com.tmp.order.api.OrderItemId orderItemId,
                com.tmp.order.api.RevisionNumber revisionNumber) {
            return Optional.empty();
        }

        @Override
        public Optional<com.tmp.order.api.OrderItemRevisionDto> getActiveOrderItemRevision(
                com.tmp.order.api.OrderItemId orderItemId) {
            return Optional.empty();
        }

        @Override
        public Optional<com.tmp.order.api.ItemSpecificationDto> getItemSpecification(
                com.tmp.order.api.OrderItemId orderItemId,
                com.tmp.order.api.RevisionNumber revisionNumber) {
            return Optional.empty();
        }

        @Override
        public Optional<com.tmp.order.api.ProductionSpecificationDto> getCurrentItemSpecification(
                com.tmp.order.api.OrderItemId orderItemId) {
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

    private static final class FakeDocuments implements OrderDocumentUiService {
        private OrderId createdId = OrderId.generate();

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
                UUID documentId, OrderId orderId, OrderHeaderDraft draft, long expectedPayloadRevision) {
            return expectedPayloadRevision + 1;
        }

        @Override
        public OrderId postDocument(UUID documentId) {
            return createdId;
        }

        @Override
        public OrderId saveNewOrder(OrderHeaderDraft draft) {
            return createdId;
        }

        @Override
        public OrderId saveExistingDraft(OrderId orderId, OrderHeaderDraft draft) {
            return orderId;
        }

        @Override
        public OrderId transferToWork(OrderId orderId) {
            return orderId;
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
