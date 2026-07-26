package com.tmp.order.application.document;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.api.event.OrderCancelled;
import com.tmp.order.application.order.CancelOrderCommand;
import com.tmp.order.application.order.CancelOrderUseCase;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderCancelPayload;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.processing.ResultReference;
import com.tmp.order.domain.CustomerOrder;
import java.time.Clock;
import java.util.Objects;

/**
 * Document processor for {@code ORDER_CANCEL} (Specification §13 / §14). Draft orders only.
 */
public final class OrderCancelDocumentProcessor extends AbstractOrderDocumentProcessor {

    private static final String RESULT_PREFIX = "order:";

    private final CancelOrderUseCase cancelOrderUseCase;

    public OrderCancelDocumentProcessor(
            OrderDocumentPayloadPort payloadPort,
            ProcessingRecordPort processingRecordPort,
            TransactionalEventPublisher eventPublisher,
            CancelOrderUseCase cancelOrderUseCase,
            Clock clock) {
        super(
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_CANCEL),
                payloadPort,
                processingRecordPort,
                eventPublisher,
                clock);
        this.cancelOrderUseCase = Objects.requireNonNull(cancelOrderUseCase, "cancelOrderUseCase");
    }

    public DocumentProcessorRegistration register(DocumentEngine documentEngine) {
        Objects.requireNonNull(documentEngine, "documentEngine");
        return documentEngine.registerProcessor(this);
    }

    @Override
    protected ResultReference executeBusinessAction(
            DocumentOperationContext context, OrderDocumentPayload payload) {
        OrderCancelPayload cancelPayload = (OrderCancelPayload) payload;
        CustomerOrder order =
                cancelOrderUseCase.execute(new CancelOrderCommand(cancelPayload.orderId()));
        return ResultReference.of(RESULT_PREFIX + order.id().value());
    }

    @Override
    protected DomainEvent createDomainEvent(
            DocumentOperationContext context,
            OrderDocumentPayload payload,
            ResultReference resultReference) {
        OrderCancelPayload cancelPayload = (OrderCancelPayload) payload;
        return new OrderCancelled(cancelPayload.orderId());
    }
}
