package com.tmp.order.application.document;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.api.event.OrderItemRevisionCreated;
import com.tmp.order.application.item.CreateOrderItemRevisionCommand;
import com.tmp.order.application.item.CreateOrderItemRevisionUseCase;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderItemRevisionCreatePayload;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.processing.ResultReference;
import com.tmp.order.domain.OrderItem;
import java.time.Clock;
import java.util.Objects;

/**
 * Document processor for {@code ORDER_ITEM_REVISION_CREATE} (Specification §6.2 / §13).
 */
public final class OrderItemRevisionCreateDocumentProcessor extends AbstractOrderDocumentProcessor {

    private static final String RESULT_PREFIX = "orderItem:";

    private final CreateOrderItemRevisionUseCase createOrderItemRevisionUseCase;

    public OrderItemRevisionCreateDocumentProcessor(
            OrderDocumentPayloadPort payloadPort,
            ProcessingRecordPort processingRecordPort,
            TransactionalEventPublisher eventPublisher,
            CreateOrderItemRevisionUseCase createOrderItemRevisionUseCase,
            Clock clock) {
        super(
                OrderBusinessDocumentCatalog.requireByCode(
                        DocumentTypeCode.ORDER_ITEM_REVISION_CREATE),
                payloadPort,
                processingRecordPort,
                eventPublisher,
                clock);
        this.createOrderItemRevisionUseCase =
                Objects.requireNonNull(
                        createOrderItemRevisionUseCase, "createOrderItemRevisionUseCase");
    }

    public DocumentProcessorRegistration register(DocumentEngine documentEngine) {
        Objects.requireNonNull(documentEngine, "documentEngine");
        return documentEngine.registerProcessor(this);
    }

    @Override
    protected ResultReference executeBusinessAction(
            DocumentOperationContext context, OrderDocumentPayload payload) {
        OrderItemRevisionCreatePayload createPayload = (OrderItemRevisionCreatePayload) payload;
        OrderItem item =
                createOrderItemRevisionUseCase.execute(
                        new CreateOrderItemRevisionCommand(
                                createPayload.orderItemId(),
                                createPayload.revisionNumber(),
                                createPayload.copyFromRevisionNumber()));
        return ResultReference.of(
                RESULT_PREFIX
                        + item.id().value()
                        + ":rev:"
                        + createPayload.revisionNumber().value());
    }

    @Override
    protected DomainEvent createDomainEvent(
            DocumentOperationContext context,
            OrderDocumentPayload payload,
            ResultReference resultReference) {
        OrderItemRevisionCreatePayload createPayload = (OrderItemRevisionCreatePayload) payload;
        return new OrderItemRevisionCreated(
                createPayload.orderItemId(), createPayload.revisionNumber());
    }
}
