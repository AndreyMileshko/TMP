package com.tmp.ui.shell.screen.orderitemlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class OrderItemListViewModelTest {

    @Test
    void loadsItemsOnlyThroughGetOrderItems() {
        FakeQuery query = new FakeQuery();
        OrderId orderId = OrderId.generate();
        OrderItemId itemId = OrderItemId.generate();
        query.items = List.of(item(orderId, itemId));
        OrderItemListViewModel viewModel =
                new OrderItemListViewModel(
                        query,
                        new FakeAuthorization(
                                Set.of(
                                        PermissionId.of("order.item.view"),
                                        PermissionId.of("order.item.create"))));
        viewModel.openForOrder(orderId, OrderStatus.DRAFT);
        assertEquals(1, query.getOrderItemsCalls);
        assertEquals(1, viewModel.items().size());
        assertTrue(viewModel.canCreateProperty().get());
    }

    @Test
    void createDisabledWhenOrderNotDraft() {
        FakeQuery query = new FakeQuery();
        OrderItemListViewModel viewModel =
                new OrderItemListViewModel(
                        query,
                        new FakeAuthorization(Set.of(PermissionId.of("order.item.create"))));
        viewModel.openForOrder(OrderId.generate(), OrderStatus.APPROVED);
        assertFalse(viewModel.canCreateProperty().get());
    }

    @Test
    void openSelectedInvokesCallback() {
        FakeQuery query = new FakeQuery();
        OrderId orderId = OrderId.generate();
        OrderItemId itemId = OrderItemId.generate();
        query.items = List.of(item(orderId, itemId));
        OrderItemListViewModel viewModel =
                new OrderItemListViewModel(query, new FakeAuthorization());
        AtomicBoolean opened = new AtomicBoolean(false);
        viewModel.setOnOpenItem(id -> opened.set(id.equals(itemId)));
        viewModel.openForOrder(orderId, OrderStatus.DRAFT);
        viewModel.selectedItemProperty().set(viewModel.items().get(0));
        viewModel.openSelected();
        assertTrue(opened.get());
    }

    private static OrderItemDto item(OrderId orderId, OrderItemId itemId) {
        Instant now = Instant.parse("2026-07-27T10:00:00Z");
        return OrderItemDto.of(
                itemId, orderId, "P-1", "Panel", null, null, OrderItemStatus.DRAFT, null, now, now);
    }

    private static final class FakeQuery implements OrderQueryService {
        private List<OrderItemDto> items = List.of();
        private int getOrderItemsCalls;

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
            getOrderItemsCalls++;
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
