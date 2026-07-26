package com.tmp.order.application.document;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.api.event.OrderItemRevisionUpdated;
import com.tmp.order.application.item.UpdateOrderItemRevisionCommand;
import com.tmp.order.application.item.UpdateOrderItemRevisionUseCase;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderItemRevisionUpdatePayload;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.processing.ResultReference;
import com.tmp.order.domain.OrderItem;
import java.time.Clock;
import java.util.Objects;

/**
 * Document processor for {@code ORDER_ITEM_REVISION_UPDATE} (Specification §6.3 / §13).
 */
public final class OrderItemRevisionUpdateDocumentProcessor extends AbstractOrderDocumentProcessor {

    private static final String RESULT_PREFIX = "orderItem:";

    private final UpdateOrderItemRevisionUseCase updateOrderItemRevisionUseCase;

    public OrderItemRevisionUpdateDocumentProcessor(
            OrderDocumentPayloadPort payloadPort,
            ProcessingRecordPort processingRecordPort,
            TransactionalEventPublisher eventPublisher,
            UpdateOrderItemRevisionUseCase updateOrderItemRevisionUseCase,
            Clock clock) {
        super(
                OrderBusinessDocumentCatalog.requireByCode(
                        DocumentTypeCode.ORDER_ITEM_REVISION_UPDATE),
                payloadPort,
                processingRecordPort,
                eventPublisher,
                clock);
        this.updateOrderItemRevisionUseCase =
                Objects.requireNonNull(
                        updateOrderItemRevisionUseCase, "updateOrderItemRevisionUseCase");
    }

    public DocumentProcessorRegistration register(DocumentEngine documentEngine) {
        Objects.requireNonNull(documentEngine, "documentEngine");
        return documentEngine.registerProcessor(this);
    }

    @Override
    protected ResultReference executeBusinessAction(
            DocumentOperationContext context, OrderDocumentPayload payload) {
        OrderItemRevisionUpdatePayload updatePayload = (OrderItemRevisionUpdatePayload) payload;
        OrderItem item =
                updateOrderItemRevisionUseCase.execute(
                        new UpdateOrderItemRevisionCommand(
                                updatePayload.orderItemId(),
                                updatePayload.revisionNumber(),
                                updatePayload.orderedQuantity(),
                                updatePayload.lines()));
        return ResultReference.of(
                RESULT_PREFIX
                        + item.id().value()
                        + ":rev:"
                        + updatePayload.revisionNumber().value());
    }

    @Override
    protected DomainEvent createDomainEvent(
            DocumentOperationContext context,
            OrderDocumentPayload payload,
            ResultReference resultReference) {
        OrderItemRevisionUpdatePayload updatePayload = (OrderItemRevisionUpdatePayload) payload;
        return new OrderItemRevisionUpdated(
                updatePayload.orderItemId(), updatePayload.revisionNumber());
    }
}
