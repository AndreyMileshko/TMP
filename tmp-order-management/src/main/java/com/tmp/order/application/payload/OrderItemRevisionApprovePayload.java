package com.tmp.order.application.payload;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import java.time.Instant;
import java.util.Objects;

/**
 * Typed payload of {@code ORDER_ITEM_REVISION_APPROVE} (Specification §6.4 / §13.2).
 *
 * <p>Explicitly carries {@link OrderItemId} and the Draft {@link RevisionNumber} to approve.
 * Immutable; identity is bound to {@link DocumentTypeCode#ORDER_ITEM_REVISION_APPROVE}.
 */
public final class OrderItemRevisionApprovePayload implements OrderDocumentPayload {

    private final PayloadIdentity identity;
    private final OrderItemId orderItemId;
    private final RevisionNumber revisionNumber;

    private OrderItemRevisionApprovePayload(
            PayloadIdentity identity, OrderItemId orderItemId, RevisionNumber revisionNumber) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        this.revisionNumber = Objects.requireNonNull(revisionNumber, "revisionNumber");
        if (identity.documentTypeCode() != DocumentTypeCode.ORDER_ITEM_REVISION_APPROVE) {
            throw new IllegalArgumentException(
                    "OrderItemRevisionApprovePayload requires document type "
                            + "ORDER_ITEM_REVISION_APPROVE, got "
                            + identity.documentTypeCode());
        }
    }

    public static OrderItemRevisionApprovePayload create(
            DocumentId documentId,
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            Instant now) {
        Objects.requireNonNull(now, "now");
        return new OrderItemRevisionApprovePayload(
                PayloadIdentity.initialDraft(
                        documentId, DocumentTypeCode.ORDER_ITEM_REVISION_APPROVE, now),
                orderItemId,
                revisionNumber);
    }

    public static OrderItemRevisionApprovePayload rehydrate(
            PayloadIdentity identity, OrderItemId orderItemId, RevisionNumber revisionNumber) {
        return new OrderItemRevisionApprovePayload(identity, orderItemId, revisionNumber);
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
}
