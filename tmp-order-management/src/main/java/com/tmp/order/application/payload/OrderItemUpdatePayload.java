package com.tmp.order.application.payload;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.domain.ItemCommercialData;
import java.time.Instant;
import java.util.Objects;

/**
 * Typed payload of {@code ORDER_ITEM_UPDATE} (Specification §6.3 / §13.2).
 *
 * <p>Contains only commercial item fields. Revision and Specification content are forbidden here —
 * those are owned exclusively by {@code ORDER_ITEM_REVISION_*} payloads. Immutable; identity is
 * bound to {@link DocumentTypeCode#ORDER_ITEM_UPDATE}.
 */
public final class OrderItemUpdatePayload implements OrderDocumentPayload {

    private final PayloadIdentity identity;
    private final OrderItemId orderItemId;
    private final ItemCommercialData commercialData;

    private OrderItemUpdatePayload(
            PayloadIdentity identity, OrderItemId orderItemId, ItemCommercialData commercialData) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        this.commercialData = Objects.requireNonNull(commercialData, "commercialData");
        if (identity.documentTypeCode() != DocumentTypeCode.ORDER_ITEM_UPDATE) {
            throw new IllegalArgumentException(
                    "OrderItemUpdatePayload requires document type ORDER_ITEM_UPDATE, got "
                            + identity.documentTypeCode());
        }
    }

    public static OrderItemUpdatePayload create(
            DocumentId documentId,
            OrderItemId orderItemId,
            ItemCommercialData commercialData,
            Instant now) {
        Objects.requireNonNull(now, "now");
        return new OrderItemUpdatePayload(
                PayloadIdentity.initialDraft(documentId, DocumentTypeCode.ORDER_ITEM_UPDATE, now),
                orderItemId,
                commercialData);
    }

    public static OrderItemUpdatePayload rehydrate(
            PayloadIdentity identity, OrderItemId orderItemId, ItemCommercialData commercialData) {
        return new OrderItemUpdatePayload(identity, orderItemId, commercialData);
    }

    public OrderItemUpdatePayload withCommercialData(
            ItemCommercialData newData, Instant updatedAt) {
        Objects.requireNonNull(newData, "newData");
        return new OrderItemUpdatePayload(
                identity.withNextRevision(updatedAt), orderItemId, newData);
    }

    @Override
    public PayloadIdentity identity() {
        return identity;
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public ItemCommercialData commercialData() {
        return commercialData;
    }
}
