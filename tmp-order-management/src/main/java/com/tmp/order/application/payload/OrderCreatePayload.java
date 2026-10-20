package com.tmp.order.application.payload;

import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderNumber;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Typed payload of {@code ORDER_CREATE} (Specification §13.2).
 *
 * <p>Carries the business number and commercial header fields required to create a Draft customer
 * order. Optional {@code readyDate} carries SuperOkna «Дата готовности» for import. Immutable;
 * identity is bound to {@link DocumentTypeCode#ORDER_CREATE}.
 */
public final class OrderCreatePayload implements OrderDocumentPayload {

    private final PayloadIdentity identity;
    private final OrderNumber orderNumber;
    private final OrderCommercialData commercialData;
    private final LocalDate readyDate;

    private OrderCreatePayload(
            PayloadIdentity identity,
            OrderNumber orderNumber,
            OrderCommercialData commercialData,
            LocalDate readyDate) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.orderNumber = Objects.requireNonNull(orderNumber, "orderNumber");
        this.commercialData = Objects.requireNonNull(commercialData, "commercialData");
        this.readyDate = readyDate;
        if (identity.documentTypeCode() != DocumentTypeCode.ORDER_CREATE) {
            throw new IllegalArgumentException(
                    "OrderCreatePayload requires document type ORDER_CREATE, got "
                            + identity.documentTypeCode());
        }
    }

    public static OrderCreatePayload create(
            DocumentId documentId,
            OrderNumber orderNumber,
            OrderCommercialData commercialData,
            Instant now) {
        return create(documentId, orderNumber, commercialData, null, now);
    }

    public static OrderCreatePayload create(
            DocumentId documentId,
            OrderNumber orderNumber,
            OrderCommercialData commercialData,
            LocalDate readyDate,
            Instant now) {
        Objects.requireNonNull(now, "now");
        return new OrderCreatePayload(
                PayloadIdentity.initialDraft(documentId, DocumentTypeCode.ORDER_CREATE, now),
                orderNumber,
                commercialData,
                readyDate);
    }

    public static OrderCreatePayload rehydrate(
            PayloadIdentity identity,
            OrderNumber orderNumber,
            OrderCommercialData commercialData) {
        return rehydrate(identity, orderNumber, commercialData, null);
    }

    public static OrderCreatePayload rehydrate(
            PayloadIdentity identity,
            OrderNumber orderNumber,
            OrderCommercialData commercialData,
            LocalDate readyDate) {
        return new OrderCreatePayload(identity, orderNumber, commercialData, readyDate);
    }

    public OrderCreatePayload withCommercialData(OrderCommercialData newData, Instant updatedAt) {
        Objects.requireNonNull(newData, "newData");
        return new OrderCreatePayload(
                identity.withNextRevision(updatedAt), orderNumber, newData, readyDate);
    }

    public OrderCreatePayload withOrderNumber(OrderNumber newNumber, Instant updatedAt) {
        Objects.requireNonNull(newNumber, "newNumber");
        return new OrderCreatePayload(
                identity.withNextRevision(updatedAt), newNumber, commercialData, readyDate);
    }

    @Override
    public PayloadIdentity identity() {
        return identity;
    }

    public OrderNumber orderNumber() {
        return orderNumber;
    }

    public OrderCommercialData commercialData() {
        return commercialData;
    }

    /** Optional planned ready date; {@code null} when absent. */
    public LocalDate readyDate() {
        return readyDate;
    }
}
