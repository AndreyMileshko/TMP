package com.tmp.order.api.imports;

import com.tmp.order.api.OrderId;
import java.util.Objects;

/**
 * Successful confirm result of a source-neutral order import (ADR-031: uniform ACTIVE landing).
 */
public final class OrderImportConfirmResult {

    private final OrderId orderId;
    private final String orderNumber;
    private final int createdPositionCount;
    private final int createdSpecificationLineCount;

    private OrderImportConfirmResult(
            OrderId orderId,
            String orderNumber,
            int createdPositionCount,
            int createdSpecificationLineCount) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.createdPositionCount = createdPositionCount;
        this.createdSpecificationLineCount = createdSpecificationLineCount;
    }

    public static OrderImportConfirmResult of(
            OrderId orderId,
            String orderNumber,
            int createdPositionCount,
            int createdSpecificationLineCount) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(orderNumber, "orderNumber");
        if (createdPositionCount < 0) {
            throw new IllegalArgumentException("createdPositionCount must be >= 0");
        }
        if (createdSpecificationLineCount < 0) {
            throw new IllegalArgumentException("createdSpecificationLineCount must be >= 0");
        }
        return new OrderImportConfirmResult(
                orderId, orderNumber, createdPositionCount, createdSpecificationLineCount);
    }

    public OrderId orderId() {
        return orderId;
    }

    public String orderNumber() {
        return orderNumber;
    }

    public int createdPositionCount() {
        return createdPositionCount;
    }

    public int createdSpecificationLineCount() {
        return createdSpecificationLineCount;
    }
}
