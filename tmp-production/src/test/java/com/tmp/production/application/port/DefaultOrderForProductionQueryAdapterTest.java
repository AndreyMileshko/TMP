package com.tmp.production.application.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.ItemSpecificationDto;
import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderForProductionDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemForProductionDto;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemRevisionDto;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderSearchCriteria;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.OrderSummaryDto;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.order.api.ProductionSpecificationDto;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.SpecificationId;
import com.tmp.production.application.port.OrderForProductionQueryPort.ResolvedOrderForLaunch;
import com.tmp.production.domain.SourceOrderId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultOrderForProductionQueryAdapterTest {

    @Test
    void mapsPublicQueryResultToLaunchView() {
        OrderId orderId = OrderId.generate();
        OrderItemId itemId = OrderItemId.generate();
        SpecificationId specId = SpecificationId.of(UUID.randomUUID());
        ProductionSpecificationDto spec =
                ProductionSpecificationDto.of(specId, itemId, BigDecimal.TEN, List.of());
        FakeOrderQuery query =
                new FakeOrderQuery(
                        OrderForProductionDto.of(
                                orderId,
                                OrderStatus.ACTIVE,
                                1,
                                List.of(OrderItemForProductionDto.of(itemId, spec))));
        DefaultOrderForProductionQueryAdapter adapter =
                new DefaultOrderForProductionQueryAdapter(query);

        Optional<ResolvedOrderForLaunch> resolved =
                adapter.resolveForLaunch(SourceOrderId.of(orderId.value()));

        assertTrue(resolved.isPresent());
        assertEquals(OrderStatus.ACTIVE, resolved.get().orderStatus());
        assertEquals(1, resolved.get().lines().size());
        assertEquals(BigDecimal.TEN, resolved.get().lines().getFirst().orderedQuantity());
        assertEquals(specId.value(), resolved.get().lines().getFirst().specificationId().value());
    }

    private static final class FakeOrderQuery implements OrderQueryService {

        private final OrderForProductionDto dto;

        private FakeOrderQuery(OrderForProductionDto dto) {
            this.dto = dto;
        }

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
        public Optional<ProductionSpecificationDto> getCurrentItemSpecification(
                OrderItemId orderItemId) {
            return Optional.empty();
        }

        @Override
        public Optional<ProductionSpecificationDto> getSpecificationById(
                SpecificationId specificationId) {
            return Optional.empty();
        }

        @Override
        public Optional<OrderForProductionDto> getOrderForProduction(OrderId orderId) {
            return Optional.of(dto);
        }
    }
}
