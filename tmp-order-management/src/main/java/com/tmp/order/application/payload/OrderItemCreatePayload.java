package com.tmp.order.application.payload;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.domain.ItemCommercialData;
import com.tmp.order.domain.OrderedQuantity;
import java.time.Instant;
import java.util.Objects;

/**
 * Typed payload of {@code ORDER_ITEM_CREATE} (Specification §13.2).
 *
 * <p>Contains only data required to create a Draft order item with initial Revision 1 Draft:
 * parent order, new item id, commercial fields and ordered quantity. Immutable; identity is bound
 * to {@link DocumentTypeCode#ORDER_ITEM_CREATE}.
 */
public final class OrderItemCreatePayload implements OrderDocumentPayload {

    private final PayloadIdentity identity;
    private final OrderId orderId;
    private final OrderItemId orderItemId;
    private final ItemCommercialData commercialData;
    private final OrderedQuantity orderedQuantity;

    private OrderItemCreatePayload(
            PayloadIdentity identity,
            OrderId orderId,
            OrderItemId orderItemId,
            ItemCommercialData commercialData,
            OrderedQuantity orderedQuantity) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        this.commercialData = Objects.requireNonNull(commercialData, "commercialData");
        this.orderedQuantity = Objects.requireNonNull(orderedQuantity, "orderedQuantity");
        if (identity.documentTypeCode() != DocumentTypeCode.ORDER_ITEM_CREATE) {
            throw new IllegalArgumentException(
                    "OrderItemCreatePayload requires document type ORDER_ITEM_CREATE, got "
                            + identity.documentTypeCode());
        }
    }

    public static OrderItemCreatePayload create(
            DocumentId documentId,
            OrderId orderId,
            OrderItemId orderItemId,
            ItemCommercialData commercialData,
            OrderedQuantity orderedQuantity,
            Instant now) {
        Objects.requireNonNull(now, "now");
        return new OrderItemCreatePayload(
                PayloadIdentity.initialDraft(documentId, DocumentTypeCode.ORDER_ITEM_CREATE, now),
                orderId,
                orderItemId,
                commercialData,
                orderedQuantity);
    }

    public static OrderItemCreatePayload rehydrate(
            PayloadIdentity identity,
            OrderId orderId,
            OrderItemId orderItemId,
            ItemCommercialData commercialData,
            OrderedQuantity orderedQuantity) {
        return new OrderItemCreatePayload(
                identity, orderId, orderItemId, commercialData, orderedQuantity);
    }

    public OrderItemCreatePayload withCommercialData(
            ItemCommercialData newData, Instant updatedAt) {
        Objects.requireNonNull(newData, "newData");
        return new OrderItemCreatePayload(
                identity.withNextRevision(updatedAt),
                orderId,
                orderItemId,
                newData,
                orderedQuantity);
    }

    public OrderItemCreatePayload withOrderedQuantity(
            OrderedQuantity newQuantity, Instant updatedAt) {
        Objects.requireNonNull(newQuantity, "newQuantity");
        return new OrderItemCreatePayload(
                identity.withNextRevision(updatedAt),
                orderId,
                orderItemId,
                commercialData,
                newQuantity);
    }

    @Override
    public PayloadIdentity identity() {
        return identity;
    }

    public OrderId orderId() {
        return orderId;
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public ItemCommercialData commercialData() {
        return commercialData;
    }

    public OrderedQuantity orderedQuantity() {
        return orderedQuantity;
    }
}
