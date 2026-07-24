package com.tmp.order.domain;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import java.util.Objects;
import java.util.Optional;

/**
 * Order Item Revision entity within the Order Item aggregate boundary (Specification §5.3 / §6).
 *
 * <p>After {@link RevisionStatus#APPROVED} the revision is immutable (ADR-018). Item Specification
 * is attached in STAGE5-007; until then the revision may exist without a specification payload.
 */
public final class OrderItemRevision {

    private final OrderItemId orderItemId;
    private final RevisionNumber revisionNumber;
    private final RevisionStatus status;
    private final OrderedQuantity orderedQuantity;
    private final RevisionNumber previousRevisionNumber;
    private final ItemSpecification specification;

    private OrderItemRevision(
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            RevisionStatus status,
            OrderedQuantity orderedQuantity,
            RevisionNumber previousRevisionNumber,
            ItemSpecification specification) {
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        this.revisionNumber = Objects.requireNonNull(revisionNumber, "revisionNumber");
        this.status = Objects.requireNonNull(status, "status");
        this.orderedQuantity = Objects.requireNonNull(orderedQuantity, "orderedQuantity");
        this.previousRevisionNumber = previousRevisionNumber;
        this.specification = specification;
    }

    static OrderItemRevision createDraft(
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            OrderedQuantity orderedQuantity,
            RevisionNumber previousRevisionNumber) {
        return new OrderItemRevision(
                orderItemId,
                revisionNumber,
                RevisionStatus.DRAFT,
                orderedQuantity,
                previousRevisionNumber,
                null);
    }

    static OrderItemRevision rehydrate(
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            RevisionStatus status,
            OrderedQuantity orderedQuantity,
            RevisionNumber previousRevisionNumber,
            ItemSpecification specification) {
        return new OrderItemRevision(
                orderItemId,
                revisionNumber,
                status,
                orderedQuantity,
                previousRevisionNumber,
                specification);
    }

    OrderItemRevision withOrderedQuantity(OrderedQuantity quantity) {
        requireDraft("change ordered quantity");
        return new OrderItemRevision(
                orderItemId,
                revisionNumber,
                status,
                quantity,
                previousRevisionNumber,
                specification);
    }

    OrderItemRevision withSpecification(ItemSpecification specification) {
        requireDraft("change specification");
        Objects.requireNonNull(specification, "specification");
        if (!specification.orderItemId().equals(orderItemId)
                || !specification.revisionNumber().equals(revisionNumber)) {
            throw new IllegalArgumentException(
                    "Specification must belong to this revision "
                            + orderItemId + "/" + revisionNumber);
        }
        return new OrderItemRevision(
                orderItemId,
                revisionNumber,
                status,
                orderedQuantity,
                previousRevisionNumber,
                specification);
    }

    OrderItemRevision approved() {
        requireDraft("approve");
        ItemSpecification frozen = specification == null ? null : specification.frozen();
        return new OrderItemRevision(
                orderItemId,
                revisionNumber,
                RevisionStatus.APPROVED,
                orderedQuantity,
                previousRevisionNumber,
                frozen);
    }

    private void requireDraft(String action) {
        if (status != RevisionStatus.DRAFT) {
            throw new InvalidOrderStateException(
                    "Cannot " + action + " on approved revision "
                            + orderItemId + "/" + revisionNumber);
        }
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

    public OrderedQuantity orderedQuantity() {
        return orderedQuantity;
    }

    public Optional<RevisionNumber> previousRevisionNumber() {
        return Optional.ofNullable(previousRevisionNumber);
    }

    public Optional<ItemSpecification> specification() {
        return Optional.ofNullable(specification);
    }

    public boolean isDraft() {
        return status == RevisionStatus.DRAFT;
    }

    public boolean isApproved() {
        return status == RevisionStatus.APPROVED;
    }
}
