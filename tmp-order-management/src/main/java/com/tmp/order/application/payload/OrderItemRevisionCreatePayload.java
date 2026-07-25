package com.tmp.order.application.payload;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import java.time.Instant;
import java.util.Objects;

/**
 * Typed payload of {@code ORDER_ITEM_REVISION_CREATE} (Specification §6.2 / §13.2).
 *
 * <p>Creates a new Draft Revision {@code N+1} for an active order item. Explicitly carries
 * {@link OrderItemId} and the required new {@link RevisionNumber}. Immutable; identity is bound to
 * {@link DocumentTypeCode#ORDER_ITEM_REVISION_CREATE}.
 */
public final class OrderItemRevisionCreatePayload implements OrderDocumentPayload {

    private final PayloadIdentity identity;
    private final OrderItemId orderItemId;
    private final RevisionNumber revisionNumber;
    private final RevisionNumber copyFromRevisionNumber;

    private OrderItemRevisionCreatePayload(
            PayloadIdentity identity,
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            RevisionNumber copyFromRevisionNumber) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        this.revisionNumber = Objects.requireNonNull(revisionNumber, "revisionNumber");
        this.copyFromRevisionNumber = copyFromRevisionNumber;
        if (identity.documentTypeCode() != DocumentTypeCode.ORDER_ITEM_REVISION_CREATE) {
            throw new IllegalArgumentException(
                    "OrderItemRevisionCreatePayload requires document type "
                            + "ORDER_ITEM_REVISION_CREATE, got "
                            + identity.documentTypeCode());
        }
        if (copyFromRevisionNumber != null && !revisionNumber.isAfter(copyFromRevisionNumber)) {
            throw new IllegalArgumentException(
                    "New revision number must be after copy-from revision: "
                            + revisionNumber
                            + " / "
                            + copyFromRevisionNumber);
        }
    }

    public static OrderItemRevisionCreatePayload create(
            DocumentId documentId,
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            RevisionNumber copyFromRevisionNumber,
            Instant now) {
        Objects.requireNonNull(now, "now");
        return new OrderItemRevisionCreatePayload(
                PayloadIdentity.initialDraft(
                        documentId, DocumentTypeCode.ORDER_ITEM_REVISION_CREATE, now),
                orderItemId,
                revisionNumber,
                copyFromRevisionNumber);
    }

    public static OrderItemRevisionCreatePayload rehydrate(
            PayloadIdentity identity,
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            RevisionNumber copyFromRevisionNumber) {
        return new OrderItemRevisionCreatePayload(
                identity, orderItemId, revisionNumber, copyFromRevisionNumber);
    }

    @Override
    public PayloadIdentity identity() {
        return identity;
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public RevisionNumber revisionNumber() {
        return revisionNumber;
    }

    /**
     * Optional previous revision to copy as the initial Draft content; {@code null} when starting
     * empty.
     */
    public RevisionNumber copyFromRevisionNumber() {
        return copyFromRevisionNumber;
    }
}
