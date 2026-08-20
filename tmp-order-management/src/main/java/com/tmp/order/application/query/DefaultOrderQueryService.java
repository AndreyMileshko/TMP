package com.tmp.order.application.query;

import com.tmp.order.api.ItemSpecificationDto;
import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderForProductionDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemForProductionDto;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemRevisionDto;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderSearchCriteria;
import com.tmp.order.api.OrderSummaryDto;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.order.api.ProductionSpecificationDto;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.SpecificationId;
import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.security.api.AuthorizationService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Default Public Query API implementation (Specification §15.1 / §18).
 *
 * <p>Enforces view permissions via the public {@link AuthorizationService} before delegating to the
 * read-only persistence port. Never returns domain aggregates.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Holds injected collaborators managed by the Spring container.")
public final class DefaultOrderQueryService implements OrderQueryService {

    private final OrderQueryReadPort readPort;
    private final AuthorizationService authorization;

    public DefaultOrderQueryService(
            OrderQueryReadPort readPort, AuthorizationService authorization) {
        this.readPort = Objects.requireNonNull(readPort, "readPort");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    @Override
    public PageResult<OrderSummaryDto> searchOrders(
            OrderSearchCriteria criteria, PageRequest pageRequest) {
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(pageRequest, "pageRequest");
        authorization.requirePermission(OrderManagementPermissions.ORDER_VIEW);
        return readPort.searchOrders(criteria, pageRequest);
    }

    @Override
    public Optional<OrderDto> getOrder(OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId");
        authorization.requirePermission(OrderManagementPermissions.ORDER_VIEW);
        return readPort.findOrder(orderId);
    }

    @Override
    public PageResult<OrderItemDto> getOrderItems(OrderId orderId, PageRequest pageRequest) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(pageRequest, "pageRequest");
        authorization.requirePermission(OrderManagementPermissions.ITEM_VIEW);
        return readPort.findOrderItems(orderId, pageRequest);
    }

    @Override
    public Optional<OrderItemDto> getOrderItem(OrderItemId orderItemId) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        authorization.requirePermission(OrderManagementPermissions.ITEM_VIEW);
        return readPort.findOrderItem(orderItemId);
    }

    @Override
    public PageResult<OrderItemRevisionDto> getOrderItemRevisions(
            OrderItemId orderItemId, PageRequest pageRequest) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(pageRequest, "pageRequest");
        authorization.requirePermission(OrderManagementPermissions.ITEM_VIEW);
        return readPort.findApprovedRevisions(orderItemId, pageRequest);
    }

    @Override
    public Optional<OrderItemRevisionDto> getOrderItemRevision(
            OrderItemId orderItemId, RevisionNumber revisionNumber) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(revisionNumber, "revisionNumber");
        authorization.requirePermission(OrderManagementPermissions.ITEM_VIEW);
        return readPort.findApprovedRevision(orderItemId, revisionNumber);
    }

    @Override
    public Optional<OrderItemRevisionDto> getActiveOrderItemRevision(OrderItemId orderItemId) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        authorization.requirePermission(OrderManagementPermissions.ITEM_VIEW);
        return readPort.findActiveRevision(orderItemId);
    }

    @Override
    public Optional<ItemSpecificationDto> getItemSpecification(
            OrderItemId orderItemId, RevisionNumber revisionNumber) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(revisionNumber, "revisionNumber");
        authorization.requirePermission(OrderManagementPermissions.SPECIFICATION_VIEW);
        return readPort.findApprovedSpecification(orderItemId, revisionNumber);
    }

    @Override
    public Optional<ProductionSpecificationDto> getCurrentItemSpecification(
            OrderItemId orderItemId) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        authorization.requirePermission(OrderManagementPermissions.SPECIFICATION_VIEW);
        return readPort.findCurrentSpecification(orderItemId);
    }

    @Override
    public Optional<ProductionSpecificationDto> getSpecificationById(
            SpecificationId specificationId) {
        Objects.requireNonNull(specificationId, "specificationId");
        authorization.requirePermission(OrderManagementPermissions.SPECIFICATION_VIEW);
        return readPort.findSpecificationById(specificationId);
    }

    @Override
    public Optional<OrderForProductionDto> getOrderForProduction(OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId");
        authorization.requirePermission(OrderManagementPermissions.ORDER_VIEW);
        authorization.requirePermission(OrderManagementPermissions.ITEM_VIEW);
        authorization.requirePermission(OrderManagementPermissions.SPECIFICATION_VIEW);

        Optional<OrderDto> order = readPort.findOrder(orderId);
        if (order.isEmpty()) {
            return Optional.empty();
        }

        List<OrderItemForProductionDto> producibleItems = new ArrayList<>();
        int activeItemCount = 0;
        long fetched = 0;
        long totalElements = Long.MAX_VALUE;
        int pageIndex = 0;
        while (fetched < totalElements) {
            PageResult<OrderItemDto> page =
                    readPort.findOrderItems(orderId, PageRequest.of(pageIndex++, PageRequest.MAX_PAGE_SIZE));
            totalElements = page.totalElements();
            fetched += page.content().size();
            for (OrderItemDto item : page.content()) {
                if (item.status() != OrderItemStatus.ACTIVE) {
                    continue;
                }
                activeItemCount++;
                readPort.findCurrentSpecification(item.orderItemId())
                        .ifPresent(
                                specification ->
                                        producibleItems.add(
                                                OrderItemForProductionDto.of(
                                                        item.orderItemId(), specification)));
            }
        }

        return Optional.of(
                OrderForProductionDto.of(
                        order.get().orderId(),
                        order.get().status(),
                        activeItemCount,
                        producibleItems));
    }
}
