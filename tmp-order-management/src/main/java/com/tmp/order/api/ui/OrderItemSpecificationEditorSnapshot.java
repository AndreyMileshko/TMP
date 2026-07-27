package com.tmp.order.api.ui;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Desktop UI read model for the Item Specification editor.
 *
 * <p>May expose Draft or Approved Specification for desktop UI only. Not part of the Public Query
 * API. Snapshot and line list are immutable.
 */
public final class OrderItemSpecificationEditorSnapshot {

    private final OrderItemId orderItemId;
    private final RevisionNumber revisionNumber;
    private final RevisionStatus revisionStatus;
    private final BigDecimal orderedQuantity;
    private final boolean immutable;
    private final List<OrderItemSpecificationLineView> lines;

    private OrderItemSpecificationEditorSnapshot(
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            RevisionStatus revisionStatus,
            BigDecimal orderedQuantity,
            boolean immutable,
            List<OrderItemSpecificationLineView> lines) {
        this.orderItemId = orderItemId;
        this.revisionNumber = revisionNumber;
        this.revisionStatus = revisionStatus;
        this.orderedQuantity = orderedQuantity;
        this.immutable = immutable;
        this.lines = List.copyOf(lines);
    }

    public static OrderItemSpecificationEditorSnapshot of(
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            RevisionStatus revisionStatus,
            BigDecimal orderedQuantity,
            boolean immutable,
            List<OrderItemSpecificationLineView> lines) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(revisionNumber, "revisionNumber");
        Objects.requireNonNull(revisionStatus, "revisionStatus");
        Objects.requireNonNull(orderedQuantity, "orderedQuantity");
        Objects.requireNonNull(lines, "lines");
        return new OrderItemSpecificationEditorSnapshot(
                orderItemId, revisionNumber, revisionStatus, orderedQuantity, immutable, lines);
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public RevisionNumber revisionNumber() {
        return revisionNumber;
    }

    public RevisionStatus revisionStatus() {
        return revisionStatus;
    }

    public BigDecimal orderedQuantity() {
        return orderedQuantity;
    }

    /** {@code true} when the owning Revision is Approved (read-only). */
    public boolean immutable() {
        return immutable;
    }

    /** Stable-order immutable specification lines. */
    public List<OrderItemSpecificationLineView> lines() {
        return lines;
    }
}
