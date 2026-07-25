package com.tmp.order.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only view of an order item (Specification §15.1.3).
 *
 * <p>Exposes only the {@code activeRevisionNumber} (approved). Draft revision numbers and draft
 * content are never published on the Public Query API.
 */
public final class OrderItemDto {

    private final OrderItemId orderItemId;
    private final OrderId orderId;
    private final String productCode;
    private final String name;
    private final String comments;
    private final OrderItemStatus status;
    private final RevisionNumber activeRevisionNumber;
    private final Instant createdAt;
    private final Instant updatedAt;

    private OrderItemDto(
            OrderItemId orderItemId,
            OrderId orderId,
            String productCode,
            String name,
            String comments,
            OrderItemStatus status,
            RevisionNumber activeRevisionNumber,
            Instant createdAt,
            Instant updatedAt) {
        this.orderItemId = orderItemId;
        this.orderId = orderId;
        this.productCode = productCode;
        this.name = name;
        this.comments = comments;
        this.status = status;
        this.activeRevisionNumber = activeRevisionNumber;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static OrderItemDto of(
            OrderItemId orderItemId,
            OrderId orderId,
            String productCode,
            String name,
            String comments,
            OrderItemStatus status,
            RevisionNumber activeRevisionNumber,
            Instant createdAt,
            Instant updatedAt) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(productCode, "productCode");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        return new OrderItemDto(
                orderItemId,
                orderId,
                productCode,
                name,
                comments,
                status,
                activeRevisionNumber,
                createdAt,
                updatedAt);
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public OrderId orderId() {
        return orderId;
    }

    public String productCode() {
        return productCode;
    }

    public String name() {
        return name;
    }

    public String comments() {
        return comments;
    }

    public OrderItemStatus status() {
        return status;
    }

    /** Approved active revision, if any. Never a draft. */
    public Optional<RevisionNumber> activeRevisionNumber() {
        return Optional.ofNullable(activeRevisionNumber);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
