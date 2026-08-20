package com.tmp.order.api;

import java.util.Objects;

/**
 * Production-facing read-only view of an order item prepared for whole-order Launch.
 *
 * <p>Contains the stable {@link ProductionSpecificationDto} without {@link RevisionNumber}.
 */
public final class OrderItemForProductionDto {

    private final OrderItemId orderItemId;
    private final ProductionSpecificationDto specification;

    private OrderItemForProductionDto(OrderItemId orderItemId, ProductionSpecificationDto specification) {
        this.orderItemId = orderItemId;
        this.specification = specification;
    }

    public static OrderItemForProductionDto of(
            OrderItemId orderItemId, ProductionSpecificationDto specification) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(specification, "specification");
        return new OrderItemForProductionDto(orderItemId, specification);
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public ProductionSpecificationDto specification() {
        return specification;
    }
}
