package com.tmp.order.api;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only view of an <strong>ACTIVE</strong> order item revision (Specification §15.1.3 /
 * ADR-031).
 *
 * <p>The Public Query API never returns draft revisions. Implementations must omit draft revisions
 * from list results and return empty for draft revision lookups.
 */
public final class OrderItemRevisionDto {

    private final OrderItemId orderItemId;
    private final RevisionNumber revisionNumber;
    private final RevisionStatus status;
    private final BigDecimal orderedQuantity;
    private final RevisionNumber previousRevisionNumber;

    private OrderItemRevisionDto(
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            RevisionStatus status,
            BigDecimal orderedQuantity,
            RevisionNumber previousRevisionNumber) {
        this.orderItemId = orderItemId;
        this.revisionNumber = revisionNumber;
        this.status = status;
        this.orderedQuantity = orderedQuantity;
        this.previousRevisionNumber = previousRevisionNumber;
    }

    /**
     * Creates a public revision DTO. {@code status} must be {@link RevisionStatus#ACTIVE}.
     *
     * @throws IllegalArgumentException if {@code status} is not {@code ACTIVE}
     */
    public static OrderItemRevisionDto of(
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            RevisionStatus status,
            BigDecimal orderedQuantity,
            RevisionNumber previousRevisionNumber) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(revisionNumber, "revisionNumber");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(orderedQuantity, "orderedQuantity");
        if (status != RevisionStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Public Query API may only expose ACTIVE revisions, got: " + status);
        }
        return new OrderItemRevisionDto(
                orderItemId, revisionNumber, status, orderedQuantity, previousRevisionNumber);
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public RevisionNumber revisionNumber() {
        return revisionNumber;
    }

    public RevisionStatus status() {
        return status;
    }

    public BigDecimal orderedQuantity() {
        return orderedQuantity;
    }

    public Optional<RevisionNumber> previousRevisionNumber() {
        return Optional.ofNullable(previousRevisionNumber);
    }
}
