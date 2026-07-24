package com.tmp.order.domain;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Item Specification belonging to a concrete Order Item Revision (Specification §5.4 / §7).
 *
 * <p>Editable while the owning revision is {@code DRAFT}; becomes immutable when the revision is
 * approved (ADR-018). Line-level editing and validation are completed in STAGE5-007.
 */
public final class ItemSpecification {

    private final OrderItemId orderItemId;
    private final RevisionNumber revisionNumber;
    private final List<SpecificationLine> lines;
    private final boolean immutable;

    private ItemSpecification(
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            List<SpecificationLine> lines,
            boolean immutable) {
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        this.revisionNumber = Objects.requireNonNull(revisionNumber, "revisionNumber");
        this.lines = List.copyOf(lines);
        this.immutable = immutable;
    }

    public static ItemSpecification empty(OrderItemId orderItemId, RevisionNumber revisionNumber) {
        return new ItemSpecification(orderItemId, revisionNumber, List.of(), false);
    }

    public static ItemSpecification of(
            OrderItemId orderItemId, RevisionNumber revisionNumber, List<SpecificationLine> lines) {
        Objects.requireNonNull(lines, "lines");
        return new ItemSpecification(orderItemId, revisionNumber, lines, false);
    }

    static ItemSpecification rehydrate(
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            List<SpecificationLine> lines,
            boolean immutable) {
        return new ItemSpecification(orderItemId, revisionNumber, lines, immutable);
    }

    ItemSpecification frozen() {
        if (immutable) {
            return this;
        }
        return new ItemSpecification(orderItemId, revisionNumber, lines, true);
    }

    public ItemSpecification withLines(List<SpecificationLine> newLines) {
        requireMutable();
        Objects.requireNonNull(newLines, "newLines");
        return new ItemSpecification(orderItemId, revisionNumber, newLines, false);
    }

    public ItemSpecification addLine(SpecificationLine line) {
        requireMutable();
        Objects.requireNonNull(line, "line");
        List<SpecificationLine> next = new ArrayList<>(lines);
        next.add(line);
        return new ItemSpecification(orderItemId, revisionNumber, next, false);
    }

    public ItemSpecification clearLines() {
        requireMutable();
        return new ItemSpecification(orderItemId, revisionNumber, List.of(), false);
    }

    private void requireMutable() {
        if (immutable) {
            throw new InvalidOrderStateException(
                    "Approved item specification is immutable: "
                            + orderItemId + "/" + revisionNumber);
        }
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public RevisionNumber revisionNumber() {
        return revisionNumber;
    }

    /**
     * Unmodifiable view of specification lines. External mutation of the returned list is
     * impossible; the list itself is an unmodifiable copy.
     */
    public List<SpecificationLine> lines() {
        return Collections.unmodifiableList(lines);
    }

    public boolean isImmutable() {
        return immutable;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
