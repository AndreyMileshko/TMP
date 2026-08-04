package com.tmp.order.application.payload;

/**
 * Sealed root of Order Management typed document payloads (Specification §11 / §13.2, ADR-028).
 *
 * <p>Every concrete payload is immutable, versioned and bound to a platform {@link DocumentId}.
 * Generic JSON / {@code Map} / raw {@code Object} envelopes are not permitted.
 */
public sealed interface OrderDocumentPayload
        permits OrderCreatePayload,
                OrderUpdatePayload,
                OrderApprovePayload,
                OrderActivatePayload,
                OrderCancelPayload,
                OrderItemCreatePayload,
                OrderItemUpdatePayload,
                OrderItemCancelPayload,
                OrderItemRevisionCreatePayload,
                OrderItemRevisionUpdatePayload,
                OrderItemRevisionApprovePayload {

    PayloadIdentity identity();

    default DocumentId documentId() {
        return identity().documentId();
    }

    default DocumentTypeCode documentTypeCode() {
        return identity().documentTypeCode();
    }
}
