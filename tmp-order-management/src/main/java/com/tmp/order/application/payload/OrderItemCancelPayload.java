package com.tmp.order.application.payload;

import com.tmp.order.api.OrderItemId;
import java.time.Instant;
import java.util.Objects;

/**
 * Typed payload of {@code ORDER_ITEM_CANCEL} (Specification §13.2).
 *
 * <p>Contains only data required to cancel a Draft order item. Immutable; identity is bound to
 * {@link DocumentTypeCode#ORDER_ITEM_CANCEL}.
 */
public final class OrderItemCancelPayload implements OrderDocumentPayload {

    private final PayloadIdentity identity;
    private final OrderItemId orderItemId;

    private OrderItemCancelPayload(PayloadIdentity identity, OrderItemId orderItemId) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        if (identity.documentTypeCode() != DocumentTypeCode.ORDER_ITEM_CANCEL) {
            throw new IllegalArgumentException(
                    "OrderItemCancelPayload requires document type ORDER_ITEM_CANCEL, got "
                            + identity.documentTypeCode());
        }
    }

    public static OrderItemCancelPayload create(
            DocumentId documentId, OrderItemId orderItemId, Instant now) {
        Objects.requireNonNull(now, "now");
        return new OrderItemCancelPayload(
                PayloadIdentity.initialDraft(documentId, DocumentTypeCode.ORDER_ITEM_CANCEL, now),
                orderItemId);
    }

    public static OrderItemCancelPayload rehydrate(PayloadIdentity identity, OrderItemId orderItemId) {
        return new OrderItemCancelPayload(identity, orderItemId);
    }

    @Override
    public PayloadIdentity identity() {
        return identity;
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }
}
