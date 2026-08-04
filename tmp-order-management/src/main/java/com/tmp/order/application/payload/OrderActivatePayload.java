package com.tmp.order.application.payload;

import com.tmp.order.api.OrderId;
import java.time.Instant;
import java.util.Objects;

/**
 * Typed payload of {@code ORDER_ACTIVATE} (Specification §8.2 / ADR-031).
 *
 * <p>Shares the physical {@code order_status_payload} shape with approve/cancel; Java type remains
 * distinct. Immutable; identity is bound to {@link DocumentTypeCode#ORDER_ACTIVATE}.
 */
public final class OrderActivatePayload implements OrderDocumentPayload {

    private final PayloadIdentity identity;
    private final OrderId orderId;

    private OrderActivatePayload(PayloadIdentity identity, OrderId orderId) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        if (identity.documentTypeCode() != DocumentTypeCode.ORDER_ACTIVATE) {
            throw new IllegalArgumentException(
                    "OrderActivatePayload requires document type ORDER_ACTIVATE, got "
                            + identity.documentTypeCode());
        }
    }

    public static OrderActivatePayload create(DocumentId documentId, OrderId orderId, Instant now) {
        Objects.requireNonNull(now, "now");
        return new OrderActivatePayload(
                PayloadIdentity.initialDraft(documentId, DocumentTypeCode.ORDER_ACTIVATE, now),
                orderId);
    }

    public static OrderActivatePayload rehydrate(PayloadIdentity identity, OrderId orderId) {
        return new OrderActivatePayload(identity, orderId);
    }

    @Override
    public PayloadIdentity identity() {
        return identity;
    }

    public OrderId orderId() {
        return orderId;
    }
}
