package com.tmp.order.application.document;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.api.event.OrderItemUpdated;
import com.tmp.order.application.item.UpdateOrderItemCommand;
import com.tmp.order.application.item.UpdateOrderItemUseCase;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderItemUpdatePayload;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.processing.ResultReference;
import com.tmp.order.domain.OrderItem;
import java.time.Clock;
import java.util.Objects;

/**
 * Document processor for {@code ORDER_ITEM_UPDATE} (Specification §13 / §14).
 */
public final class OrderItemUpdateDocumentProcessor extends AbstractOrderDocumentProcessor {

    private static final String RESULT_PREFIX = "orderItem:";

    private final UpdateOrderItemUseCase updateOrderItemUseCase;

    public OrderItemUpdateDocumentProcessor(
            OrderDocumentPayloadPort payloadPort,
            ProcessingRecordPort processingRecordPort,
            TransactionalEventPublisher eventPublisher,
            UpdateOrderItemUseCase updateOrderItemUseCase,
            Clock clock) {
        super(
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_ITEM_UPDATE),
                payloadPort,
                processingRecordPort,
                eventPublisher,
                clock);
        this.updateOrderItemUseCase =
                Objects.requireNonNull(updateOrderItemUseCase, "updateOrderItemUseCase");
    }

    public DocumentProcessorRegistration register(DocumentEngine documentEngine) {
        Objects.requireNonNull(documentEngine, "documentEngine");
        return documentEngine.registerProcessor(this);
    }

    @Override
    protected ResultReference executeBusinessAction(
            DocumentOperationContext context, OrderDocumentPayload payload) {
        OrderItemUpdatePayload updatePayload = (OrderItemUpdatePayload) payload;
        OrderItem item =
                updateOrderItemUseCase.execute(
                        new UpdateOrderItemCommand(
                                updatePayload.orderItemId(), updatePayload.commercialData()));
        return ResultReference.of(RESULT_PREFIX + item.id().value());
    }

    @Override
    protected DomainEvent createDomainEvent(
            DocumentOperationContext context,
            OrderDocumentPayload payload,
            ResultReference resultReference) {
        OrderItemUpdatePayload updatePayload = (OrderItemUpdatePayload) payload;
        return new OrderItemUpdated(updatePayload.orderItemId());
    }
}
