package com.tmp.ui.shell.screen.orderlist;

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
import java.util.List;
import java.util.Optional;

/** Empty {@link OrderQueryService} double for UI FX / navigation tests. */
final class EmptyOrderQuery implements OrderQueryService {

    @Override
    public PageResult<OrderSummaryDto> searchOrders(
            OrderSearchCriteria criteria, PageRequest pageRequest) {
        return PageResult.of(List.of(), pageRequest.pageIndex(), pageRequest.pageSize(), 0);
    }

    @Override
    public Optional<OrderDto> getOrder(OrderId orderId) {
        return Optional.empty();
    }

    @Override
    public PageResult<OrderItemDto> getOrderItems(OrderId orderId, PageRequest pageRequest) {
        return PageResult.of(List.of(), pageRequest.pageIndex(), pageRequest.pageSize(), 0);
    }

    @Override
    public Optional<OrderItemDto> getOrderItem(OrderItemId orderItemId) {
        return Optional.empty();
    }

    @Override
    public PageResult<OrderItemRevisionDto> getOrderItemRevisions(
            OrderItemId orderItemId, PageRequest pageRequest) {
        return PageResult.of(List.of(), pageRequest.pageIndex(), pageRequest.pageSize(), 0);
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
