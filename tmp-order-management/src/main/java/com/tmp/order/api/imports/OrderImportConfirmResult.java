package com.tmp.order.api.imports;

import com.tmp.order.api.OrderId;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Successful confirm result of a source-neutral order import (ADR-031: uniform ACTIVE landing).
 * Supports one or more orders from a single STXT file.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "orders is an unmodifiable defensive copy.")
public final class OrderImportConfirmResult {

    private final List<ImportedOrder> orders;
    private final int createdPositionCount;
    private final int createdSpecificationLineCount;

    private OrderImportConfirmResult(
            List<ImportedOrder> orders,
            int createdPositionCount,
            int createdSpecificationLineCount) {
        this.orders = orders;
        this.createdPositionCount = createdPositionCount;
        this.createdSpecificationLineCount = createdSpecificationLineCount;
    }

    public static OrderImportConfirmResult of(
            OrderId orderId,
            String orderNumber,
            int createdPositionCount,
            int createdSpecificationLineCount) {
        return of(
                List.of(ImportedOrder.of(orderId, orderNumber)),
                createdPositionCount,
                createdSpecificationLineCount);
    }

    public static OrderImportConfirmResult of(
            List<ImportedOrder> orders,
            int createdPositionCount,
            int createdSpecificationLineCount) {
        Objects.requireNonNull(orders, "orders");
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("orders must not be empty");
        }
        if (createdPositionCount < 0) {
            throw new IllegalArgumentException("createdPositionCount must be >= 0");
        }
        if (createdSpecificationLineCount < 0) {
            throw new IllegalArgumentException("createdSpecificationLineCount must be >= 0");
        }
        List<ImportedOrder> copy = new ArrayList<>(orders.size());
        for (ImportedOrder order : orders) {
            copy.add(Objects.requireNonNull(order, "order"));
        }
        return new OrderImportConfirmResult(
                List.copyOf(copy), createdPositionCount, createdSpecificationLineCount);
    }

    public List<ImportedOrder> orders() {
        return orders;
    }

    public int createdOrderCount() {
        return orders.size();
    }

    /** First created order id (single-order convenience). */
    public OrderId orderId() {
        return orders.get(0).orderId();
    }

    /** Comma-separated order numbers, or the single number. */
    public String orderNumber() {
        return orders.stream().map(ImportedOrder::orderNumber).collect(Collectors.joining(", "));
    }

    public int createdPositionCount() {
        return createdPositionCount;
    }

    public int createdSpecificationLineCount() {
        return createdSpecificationLineCount;
    }

    /** One successfully imported ACTIVE order. */
    public static final class ImportedOrder {
        private final OrderId orderId;
        private final String orderNumber;

        private ImportedOrder(OrderId orderId, String orderNumber) {
            this.orderId = orderId;
            this.orderNumber = orderNumber;
        }

        public static ImportedOrder of(OrderId orderId, String orderNumber) {
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(orderNumber, "orderNumber");
            return new ImportedOrder(orderId, orderNumber);
        }

        public OrderId orderId() {
            return orderId;
        }

        public String orderNumber() {
            return orderNumber;
        }
    }
}
