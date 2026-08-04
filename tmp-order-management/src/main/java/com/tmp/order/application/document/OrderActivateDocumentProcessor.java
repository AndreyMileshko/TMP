package com.tmp.order.application.document;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.api.event.OrderActivated;
import com.tmp.order.application.order.ActivateOrderCommand;
import com.tmp.order.application.order.ActivateOrderUseCase;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderActivatePayload;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.processing.ResultReference;
import com.tmp.order.domain.CustomerOrder;
import java.time.Clock;
import java.util.Objects;

/**
 * Document processor for {@code ORDER_ACTIVATE} (Specification §8.2 / ADR-031).
 */
public final class OrderActivateDocumentProcessor extends AbstractOrderDocumentProcessor {

    private static final String RESULT_PREFIX = "order:";

    private final ActivateOrderUseCase activateOrderUseCase;

    public OrderActivateDocumentProcessor(
            OrderDocumentPayloadPort payloadPort,
            ProcessingRecordPort processingRecordPort,
            TransactionalEventPublisher eventPublisher,
            ActivateOrderUseCase activateOrderUseCase,
            Clock clock) {
        super(
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_ACTIVATE),
                payloadPort,
                processingRecordPort,
                eventPublisher,
                clock);
        this.activateOrderUseCase =
                Objects.requireNonNull(activateOrderUseCase, "activateOrderUseCase");
    }

    public DocumentProcessorRegistration register(DocumentEngine documentEngine) {
        Objects.requireNonNull(documentEngine, "documentEngine");
        return documentEngine.registerProcessor(this);
    }

    @Override
    protected ResultReference executeBusinessAction(
            DocumentOperationContext context, OrderDocumentPayload payload) {
        OrderActivatePayload activatePayload = (OrderActivatePayload) payload;
        CustomerOrder order =
                activateOrderUseCase.execute(new ActivateOrderCommand(activatePayload.orderId()));
        return ResultReference.of(RESULT_PREFIX + order.id().value());
    }

    @Override
    protected DomainEvent createDomainEvent(
            DocumentOperationContext context,
            OrderDocumentPayload payload,
            ResultReference resultReference) {
        OrderActivatePayload activatePayload = (OrderActivatePayload) payload;
        return new OrderActivated(activatePayload.orderId());
    }
}
