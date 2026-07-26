package com.tmp.order.application.document;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.api.event.OrderItemCancelled;
import com.tmp.order.application.item.CancelOrderItemCommand;
import com.tmp.order.application.item.CancelOrderItemUseCase;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderItemCancelPayload;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.processing.ResultReference;
import com.tmp.order.domain.OrderItem;
import java.time.Clock;
import java.util.Objects;

/**
 * Document processor for {@code ORDER_ITEM_CANCEL} (Specification §9 / §13). Draft items only.
 */
public final class OrderItemCancelDocumentProcessor extends AbstractOrderDocumentProcessor {

    private static final String RESULT_PREFIX = "orderItem:";

    private final CancelOrderItemUseCase cancelOrderItemUseCase;

    public OrderItemCancelDocumentProcessor(
            OrderDocumentPayloadPort payloadPort,
            ProcessingRecordPort processingRecordPort,
            TransactionalEventPublisher eventPublisher,
            CancelOrderItemUseCase cancelOrderItemUseCase,
            Clock clock) {
        super(
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_ITEM_CANCEL),
                payloadPort,
                processingRecordPort,
                eventPublisher,
                clock);
        this.cancelOrderItemUseCase =
                Objects.requireNonNull(cancelOrderItemUseCase, "cancelOrderItemUseCase");
    }

    public DocumentProcessorRegistration register(DocumentEngine documentEngine) {
        Objects.requireNonNull(documentEngine, "documentEngine");
        return documentEngine.registerProcessor(this);
    }

    @Override
    protected ResultReference executeBusinessAction(
            DocumentOperationContext context, OrderDocumentPayload payload) {
        OrderItemCancelPayload cancelPayload = (OrderItemCancelPayload) payload;
        OrderItem item =
                cancelOrderItemUseCase.execute(
                        new CancelOrderItemCommand(cancelPayload.orderItemId()));
        return ResultReference.of(
                RESULT_PREFIX + item.id().value() + ":order:" + item.orderId().value());
    }

    @Override
    protected DomainEvent createDomainEvent(
            DocumentOperationContext context,
            OrderDocumentPayload payload,
            ResultReference resultReference) {
        OrderItemCancelPayload cancelPayload = (OrderItemCancelPayload) payload;
        return new OrderItemCancelled(orderIdFrom(resultReference), cancelPayload.orderItemId());
    }

    static com.tmp.order.api.OrderId orderIdFrom(ResultReference resultReference) {
        Objects.requireNonNull(resultReference, "resultReference");
        String value = resultReference.value();
        int orderMarker = value.indexOf(":order:");
        if (orderMarker < 0) {
            throw new IllegalStateException(
                    "Unexpected ORDER_ITEM_CANCEL result reference: " + value);
        }
        return com.tmp.order.api.OrderId.of(
                java.util.UUID.fromString(value.substring(orderMarker + ":order:".length())));
    }
}
