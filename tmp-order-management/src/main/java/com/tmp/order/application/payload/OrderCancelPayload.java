package com.tmp.order.application.payload;

import com.tmp.order.api.OrderId;
import java.time.Instant;
import java.util.Objects;

/**
 * Typed payload of {@code ORDER_CANCEL} (Specification §13.2).
 *
 * <p>Addresses the Draft customer order to cancel. Shares the physical {@code order_status_payload}
 * shape with approve; Java type remains distinct. Immutable; identity is bound to
 * {@link DocumentTypeCode#ORDER_CANCEL}.
 */
public final class OrderCancelPayload implements OrderDocumentPayload {

    private final PayloadIdentity identity;
    private final OrderId orderId;

    private OrderCancelPayload(PayloadIdentity identity, OrderId orderId) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        if (identity.documentTypeCode() != DocumentTypeCode.ORDER_CANCEL) {
            throw new IllegalArgumentException(
                    "OrderCancelPayload requires document type ORDER_CANCEL, got "
                            + identity.documentTypeCode());
        }
    }

    public static OrderCancelPayload create(DocumentId documentId, OrderId orderId, Instant now) {
        Objects.requireNonNull(now, "now");
        return new OrderCancelPayload(
                PayloadIdentity.initialDraft(documentId, DocumentTypeCode.ORDER_CANCEL, now),
                orderId);
    }

    public static OrderCancelPayload rehydrate(PayloadIdentity identity, OrderId orderId) {
        return new OrderCancelPayload(identity, orderId);
    }

    @Override
    public PayloadIdentity identity() {
        return identity;
    }

    public OrderId orderId() {
        return orderId;
    }
}
