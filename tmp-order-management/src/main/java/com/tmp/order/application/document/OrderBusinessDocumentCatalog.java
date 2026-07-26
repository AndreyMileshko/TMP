package com.tmp.order.application.document;

import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderApprovePayload;
import com.tmp.order.application.payload.OrderCancelPayload;
import com.tmp.order.application.payload.OrderCreatePayload;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderItemCancelPayload;
import com.tmp.order.application.payload.OrderItemCreatePayload;
import com.tmp.order.application.payload.OrderItemRevisionApprovePayload;
import com.tmp.order.application.payload.OrderItemRevisionCreatePayload;
import com.tmp.order.application.payload.OrderItemRevisionUpdatePayload;
import com.tmp.order.application.payload.OrderItemUpdatePayload;
import com.tmp.order.application.payload.OrderUpdatePayload;
import com.tmp.order.domain.PayloadSchemaVersion;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable catalog of Order Management business document types (Specification §13.1 / §13.2).
 *
 * <p>Processors are not registered here — STAGE5-023+.
 */
public final class OrderBusinessDocumentCatalog {

    private static final List<OrderDocumentTypeDescriptor> DESCRIPTORS =
            List.of(
                    descriptor(
                            DocumentTypeCode.ORDER_CREATE,
                            "Order create",
                            "Creates a Draft customer order",
                            OrderCreatePayload.class,
                            "order.order.create"),
                    descriptor(
                            DocumentTypeCode.ORDER_UPDATE,
                            "Order update",
                            "Updates commercial fields of a Draft customer order",
                            OrderUpdatePayload.class,
                            "order.order.edit"),
                    descriptor(
                            DocumentTypeCode.ORDER_APPROVE,
                            "Order approve",
                            "Approves a Draft customer order",
                            OrderApprovePayload.class,
                            "order.order.approve"),
                    descriptor(
                            DocumentTypeCode.ORDER_CANCEL,
                            "Order cancel",
                            "Cancels a Draft customer order",
                            OrderCancelPayload.class,
                            "order.order.cancel"),
                    descriptor(
                            DocumentTypeCode.ORDER_ITEM_CREATE,
                            "Order item create",
                            "Creates a Draft order item with Revision 1",
                            OrderItemCreatePayload.class,
                            "order.item.create"),
                    descriptor(
                            DocumentTypeCode.ORDER_ITEM_UPDATE,
                            "Order item update",
                            "Updates commercial fields of a Draft order item",
                            OrderItemUpdatePayload.class,
                            "order.item.edit"),
                    descriptor(
                            DocumentTypeCode.ORDER_ITEM_CANCEL,
                            "Order item cancel",
                            "Cancels a Draft order item",
                            OrderItemCancelPayload.class,
                            "order.item.cancel"),
                    descriptor(
                            DocumentTypeCode.ORDER_ITEM_REVISION_CREATE,
                            "Order item revision create",
                            "Creates a new Draft Revision for an active order item",
                            OrderItemRevisionCreatePayload.class,
                            "order.revision.create"),
                    descriptor(
                            DocumentTypeCode.ORDER_ITEM_REVISION_UPDATE,
                            "Order item revision update",
                            "Updates a Draft Revision specification and quantity",
                            OrderItemRevisionUpdatePayload.class,
                            "order.revision.edit"),
                    descriptor(
                            DocumentTypeCode.ORDER_ITEM_REVISION_APPROVE,
                            "Order item revision approve",
                            "Approves a Draft Revision and makes it active",
                            OrderItemRevisionApprovePayload.class,
                            "order.item.approve"));

    private static final Map<DocumentTypeCode, OrderDocumentTypeDescriptor> BY_CODE =
            DESCRIPTORS.stream()
                    .collect(
                            Collectors.toUnmodifiableMap(
                                    OrderDocumentTypeDescriptor::documentTypeCode,
                                    Function.identity()));

    private OrderBusinessDocumentCatalog() {}

    public static List<OrderDocumentTypeDescriptor> all() {
        return DESCRIPTORS;
    }

    public static Optional<OrderDocumentTypeDescriptor> findByCode(DocumentTypeCode code) {
        Objects.requireNonNull(code, "code");
        return Optional.ofNullable(BY_CODE.get(code));
    }

    public static OrderDocumentTypeDescriptor requireByCode(DocumentTypeCode code) {
        return findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown document type: " + code));
    }

    public static OrderDocumentTypeDescriptor requireByTypeId(String documentTypeId) {
        Objects.requireNonNull(documentTypeId, "documentTypeId");
        return findByCode(DocumentTypeCode.valueOf(documentTypeId))
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Unknown document type id: " + documentTypeId));
    }

    private static OrderDocumentTypeDescriptor descriptor(
            DocumentTypeCode code,
            String displayName,
            String description,
            Class<? extends OrderDocumentPayload> payloadClass,
            String requiredCapability) {
        return new OrderDocumentTypeDescriptor(
                code,
                displayName,
                description,
                payloadClass,
                PayloadSchemaVersion.initial(),
                requiredCapability);
    }
}
