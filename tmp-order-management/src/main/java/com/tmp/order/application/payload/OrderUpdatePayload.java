package com.tmp.order.application.payload;

import com.tmp.order.api.OrderId;
import com.tmp.order.domain.OrderCommercialData;
import java.time.Instant;
import java.util.Objects;

/**
 * Typed payload of {@code ORDER_UPDATE} (Specification §13.2).
 *
 * <p>Updates commercial header fields of an existing Draft customer order. Immutable; identity is
 * bound to {@link DocumentTypeCode#ORDER_UPDATE}.
 */
public final class OrderUpdatePayload implements OrderDocumentPayload {

    private final PayloadIdentity identity;
    private final OrderId orderId;
    private final OrderCommercialData commercialData;

    private OrderUpdatePayload(
            PayloadIdentity identity, OrderId orderId, OrderCommercialData commercialData) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.commercialData = Objects.requireNonNull(commercialData, "commercialData");
        if (identity.documentTypeCode() != DocumentTypeCode.ORDER_UPDATE) {
            throw new IllegalArgumentException(
                    "OrderUpdatePayload requires document type ORDER_UPDATE, got "
                            + identity.documentTypeCode());
        }
    }

    public static OrderUpdatePayload create(
            DocumentId documentId,
            OrderId orderId,
            OrderCommercialData commercialData,
            Instant now) {
        Objects.requireNonNull(now, "now");
        return new OrderUpdatePayload(
                PayloadIdentity.initialDraft(documentId, DocumentTypeCode.ORDER_UPDATE, now),
                orderId,
                commercialData);
    }

    public static OrderUpdatePayload rehydrate(
            PayloadIdentity identity, OrderId orderId, OrderCommercialData commercialData) {
        return new OrderUpdatePayload(identity, orderId, commercialData);
    }

    public OrderUpdatePayload withCommercialData(OrderCommercialData newData, Instant updatedAt) {
        Objects.requireNonNull(newData, "newData");
        return new OrderUpdatePayload(identity.withNextRevision(updatedAt), orderId, newData);
    }

    @Override
    public PayloadIdentity identity() {
        return identity;
    }

    public OrderId orderId() {
        return orderId;
    }

    public OrderCommercialData commercialData() {
        return commercialData;
    }
}
