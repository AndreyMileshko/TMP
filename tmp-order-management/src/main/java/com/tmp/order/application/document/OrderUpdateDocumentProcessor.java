package com.tmp.order.application.document;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.api.event.OrderUpdated;
import com.tmp.order.application.order.UpdateOrderCommand;
import com.tmp.order.application.order.UpdateOrderUseCase;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderUpdatePayload;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.processing.ResultReference;
import com.tmp.order.domain.CustomerOrder;
import java.time.Clock;
import java.util.Objects;

/**
 * Document processor for {@code ORDER_UPDATE} (Specification §13 / §14).
 */
public final class OrderUpdateDocumentProcessor extends AbstractOrderDocumentProcessor {

    private static final String RESULT_PREFIX = "order:";

    private final UpdateOrderUseCase updateOrderUseCase;

    public OrderUpdateDocumentProcessor(
            OrderDocumentPayloadPort payloadPort,
            ProcessingRecordPort processingRecordPort,
            TransactionalEventPublisher eventPublisher,
            UpdateOrderUseCase updateOrderUseCase,
            Clock clock) {
        super(
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_UPDATE),
                payloadPort,
                processingRecordPort,
                eventPublisher,
                clock);
        this.updateOrderUseCase = Objects.requireNonNull(updateOrderUseCase, "updateOrderUseCase");
    }

    public DocumentProcessorRegistration register(DocumentEngine documentEngine) {
        Objects.requireNonNull(documentEngine, "documentEngine");
        return documentEngine.registerProcessor(this);
    }

    @Override
    protected ResultReference executeBusinessAction(
            DocumentOperationContext context, OrderDocumentPayload payload) {
        OrderUpdatePayload updatePayload = (OrderUpdatePayload) payload;
        CustomerOrder order =
                updateOrderUseCase.execute(
                        new UpdateOrderCommand(
                                updatePayload.orderId(), updatePayload.commercialData()));
        return ResultReference.of(RESULT_PREFIX + order.id().value());
    }

    @Override
    protected DomainEvent createDomainEvent(
            DocumentOperationContext context,
            OrderDocumentPayload payload,
            ResultReference resultReference) {
        OrderUpdatePayload updatePayload = (OrderUpdatePayload) payload;
        return new OrderUpdated(updatePayload.orderId());
    }
}
