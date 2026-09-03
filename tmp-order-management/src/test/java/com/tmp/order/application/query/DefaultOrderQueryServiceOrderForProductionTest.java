package com.tmp.order.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.ItemSpecificationDto;
import com.tmp.order.api.OrderCustomerOptionDto;
import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderForProductionDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemRevisionDto;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderSearchCriteria;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.OrderSummaryDto;
import com.tmp.order.api.OrderWorklistCriteria;
import com.tmp.order.api.OrderWorklistRowDto;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.order.api.ProductionSpecificationDto;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.SpecificationId;
import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultOrderQueryServiceOrderForProductionTest {

    @Test
    void getOrderForProductionReturnsActiveItemsWithCurrentSpecification() {
        OrderId orderId = OrderId.generate();
        OrderItemId activeItem = OrderItemId.generate();
        OrderItemId draftItem = OrderItemId.generate();
        Instant now = Instant.parse("2026-08-20T06:00:00Z");
        SpecificationId specId = SpecificationId.of(UUID.randomUUID());
        ProductionSpecificationDto spec =
                ProductionSpecificationDto.of(specId, activeItem, BigDecimal.valueOf(10), List.of());
        StubReadPort readPort =
                new StubReadPort(
                        OrderDto.of(
                                orderId,
                                "O-1",
                                OrderStatus.ACTIVE,
                                "ref",
                                "Customer",
                                null,
                                null,
                                null,
                                "PRIVATE",
                                "RUB",
                                now,
                                now),
                        List.of(
                                OrderItemDto.of(
                                        activeItem,
                                        orderId,
                                        "P-1",
                                        "Panel",
                                        null,
                                        null,
                                        OrderItemStatus.ACTIVE,
                                        RevisionNumber.first(),
                                        now,
                                        now),
                                OrderItemDto.of(
                                        draftItem,
                                        orderId,
                                        "P-2",
                                        "Draft",
                                        null,
                                        null,
                                        OrderItemStatus.DRAFT,
                                        null,
                                        now,
                                        now)),
                        Map.of(activeItem, spec));
        DefaultOrderQueryService service =
                new DefaultOrderQueryService(readPort, new AllowAllAuthorization());

        OrderForProductionDto dto = service.getOrderForProduction(orderId).orElseThrow();
        assertEquals(OrderStatus.ACTIVE, dto.status());
        assertEquals(1, dto.activeItemCount());
        assertEquals(1, dto.items().size());
        assertTrue(dto.missingSpecificationItemIds().isEmpty());
        assertEquals(activeItem, dto.items().getFirst().orderItemId());
        assertEquals(specId, dto.items().getFirst().specification().specificationId());
        assertEquals(0, BigDecimal.TEN.compareTo(dto.items().getFirst().specification().orderedQuantity()));
    }

    @Test
    void getOrderForProductionReportsMissingSpecificationItemIds() {
        OrderId orderId = OrderId.generate();
        OrderItemId withSpec = OrderItemId.generate();
        OrderItemId withoutSpec = OrderItemId.generate();
        Instant now = Instant.parse("2026-08-20T06:00:00Z");
        SpecificationId specId = SpecificationId.of(UUID.randomUUID());
        ProductionSpecificationDto spec =
                ProductionSpecificationDto.of(specId, withSpec, BigDecimal.valueOf(3), List.of());
        StubReadPort readPort =
                new StubReadPort(
                        OrderDto.of(
                                orderId,
                                "O-2",
                                OrderStatus.ACTIVE,
                                "ref",
                                "Customer",
                                null,
                                null,
                                null,
                                "PRIVATE",
                                "RUB",
                                now,
                                now),
                        List.of(
                                OrderItemDto.of(
                                        withSpec,
                                        orderId,
                                        "P-1",
                                        "Panel",
                                        null,
                                        null,
                                        OrderItemStatus.ACTIVE,
                                        RevisionNumber.first(),
                                        now,
                                        now),
                                OrderItemDto.of(
                                        withoutSpec,
                                        orderId,
                                        "P-2",
                                        "Missing",
                                        null,
                                        null,
                                        OrderItemStatus.ACTIVE,
                                        RevisionNumber.first(),
                                        now,
                                        now)),
                        Map.of(withSpec, spec));
        DefaultOrderQueryService service =
                new DefaultOrderQueryService(readPort, new AllowAllAuthorization());

        OrderForProductionDto dto = service.getOrderForProduction(orderId).orElseThrow();
        assertEquals(2, dto.activeItemCount());
        assertEquals(1, dto.items().size());
        assertEquals(List.of(withoutSpec), dto.missingSpecificationItemIds());
    }

    @Test
    void getOrderForProductionReturnsEmptyWhenOrderMissing() {
        DefaultOrderQueryService service =
                new DefaultOrderQueryService(
                        new StubReadPort(null, List.of(), Map.of()), new AllowAllAuthorization());
        assertTrue(service.getOrderForProduction(OrderId.generate()).isEmpty());
    }

    private static final class AllowAllAuthorization implements AuthorizationService {
        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return true;
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
            // granted
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of(
                    OrderManagementPermissions.ORDER_VIEW,
                    OrderManagementPermissions.ITEM_VIEW,
                    OrderManagementPermissions.SPECIFICATION_VIEW);
        }
    }

    private static final class StubReadPort implements OrderQueryReadPort {

        private final OrderDto order;
        private final List<OrderItemDto> items;
        private final Map<OrderItemId, ProductionSpecificationDto> specs;

        private StubReadPort(
                OrderDto order,
                List<OrderItemDto> items,
                Map<OrderItemId, ProductionSpecificationDto> specs) {
            this.order = order;
            this.items = items;
            this.specs = specs;
        }

        @Override
        public PageResult<OrderSummaryDto> searchOrders(
                OrderSearchCriteria criteria, PageRequest pageRequest) {
            return PageResult.of(List.of(), pageRequest.pageIndex(), pageRequest.pageSize(), 0);
        }

        @Override
        public Optional<OrderDto> findOrder(OrderId orderId) {
            return Optional.ofNullable(order);
        }

        @Override
        public PageResult<OrderItemDto> findOrderItems(OrderId orderId, PageRequest pageRequest) {
            return PageResult.of(items, pageRequest.pageIndex(), pageRequest.pageSize(), items.size());
        }

        @Override
        public Optional<OrderItemDto> findOrderItem(OrderItemId orderItemId) {
            return Optional.empty();
        }

        @Override
        public PageResult<OrderItemRevisionDto> findApprovedRevisions(
                OrderItemId orderItemId, PageRequest pageRequest) {
            return PageResult.of(List.of(), pageRequest.pageIndex(), pageRequest.pageSize(), 0);
        }

        @Override
        public Optional<OrderItemRevisionDto> findApprovedRevision(
                OrderItemId orderItemId, RevisionNumber revisionNumber) {
            return Optional.empty();
        }

        @Override
        public Optional<OrderItemRevisionDto> findActiveRevision(OrderItemId orderItemId) {
            return Optional.empty();
        }

        @Override
        public Optional<ItemSpecificationDto> findApprovedSpecification(
                OrderItemId orderItemId, RevisionNumber revisionNumber) {
            return Optional.empty();
        }

        @Override
        public Optional<ProductionSpecificationDto> findCurrentSpecification(OrderItemId orderItemId) {
            return Optional.ofNullable(specs.get(orderItemId));
        }

        @Override
        public Optional<ProductionSpecificationDto> findSpecificationById(SpecificationId specificationId) {
            return Optional.empty();
        }

        @Override
        public List<OrderWorklistRowDto> listWorklistRows(OrderWorklistCriteria criteria) {
            return List.of();
        }

        @Override
        public List<OrderCustomerOptionDto> listKnownCustomers() {
            return List.of();
        }
    }
}
