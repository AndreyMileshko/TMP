package com.tmp.order.application.payload;

import com.tmp.order.api.OrderId;
import java.time.Instant;
import java.util.Objects;

/**
 * Typed payload of {@code ORDER_APPROVE} (Specification §13.2).
 *
 * <p>Addresses the customer order to approve. Shares the physical {@code order_status_payload}
 * shape with cancel; Java type remains distinct. Immutable; identity is bound to
 * {@link DocumentTypeCode#ORDER_APPROVE}.
 */
public final class OrderApprovePayload implements OrderDocumentPayload {

    private final PayloadIdentity identity;
    private final OrderId orderId;

    private OrderApprovePayload(PayloadIdentity identity, OrderId orderId) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        if (identity.documentTypeCode() != DocumentTypeCode.ORDER_APPROVE) {
            throw new IllegalArgumentException(
                    "OrderApprovePayload requires document type ORDER_APPROVE, got "
                            + identity.documentTypeCode());
        }
    }

    public static OrderApprovePayload create(DocumentId documentId, OrderId orderId, Instant now) {
        Objects.requireNonNull(now, "now");
        return new OrderApprovePayload(
                PayloadIdentity.initialDraft(documentId, DocumentTypeCode.ORDER_APPROVE, now),
                orderId);
    }

    public static OrderApprovePayload rehydrate(PayloadIdentity identity, OrderId orderId) {
        return new OrderApprovePayload(identity, orderId);
    }

    @Override
    public PayloadIdentity identity() {
        return identity;
    }

    public OrderId orderId() {
        return orderId;
    }
}
