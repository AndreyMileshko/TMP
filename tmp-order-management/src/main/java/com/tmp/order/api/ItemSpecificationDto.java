package com.tmp.order.api;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Read-only item specification for an <strong>approved</strong> revision (Specification §15.1.3).
 *
 * <p>Draft specifications are never returned by the Public Query API. The lines collection is an
 * unmodifiable copy.
 */
public final class ItemSpecificationDto {

    private final OrderItemId orderItemId;
    private final RevisionNumber revisionNumber;
    private final List<SpecificationLineDto> lines;

    private ItemSpecificationDto(
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            List<SpecificationLineDto> lines) {
        this.orderItemId = orderItemId;
        this.revisionNumber = revisionNumber;
        this.lines = List.copyOf(lines);
    }

    public static ItemSpecificationDto of(
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            List<SpecificationLineDto> lines) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(revisionNumber, "revisionNumber");
        Objects.requireNonNull(lines, "lines");
        return new ItemSpecificationDto(orderItemId, revisionNumber, lines);
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public RevisionNumber revisionNumber() {
        return revisionNumber;
    }

    /** Unmodifiable view of specification lines. */
    public List<SpecificationLineDto> lines() {
        return Collections.unmodifiableList(lines);
    }
}
