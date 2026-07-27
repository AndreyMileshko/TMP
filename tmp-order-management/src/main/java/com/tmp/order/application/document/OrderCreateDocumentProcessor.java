package com.tmp.order.application.document;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.event.OrderCreated;
import com.tmp.order.application.order.CreateOrderCommand;
import com.tmp.order.application.order.CreateOrderUseCase;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderCreatePayload;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.processing.ResultReference;
import com.tmp.order.domain.CustomerOrder;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Document processor for {@code ORDER_CREATE} (Specification §13 / §14).
 *
 * <p>Publishes {@link OrderCreated} only through the base processor / public
 * {@link TransactionalEventPublisher}. {@code onPost} remains {@code void}.
 */
public final class OrderCreateDocumentProcessor extends AbstractOrderDocumentProcessor {

    private static final String RESULT_PREFIX = "order:";

    private final CreateOrderUseCase createOrderUseCase;

    public OrderCreateDocumentProcessor(
            OrderDocumentPayloadPort payloadPort,
            ProcessingRecordPort processingRecordPort,
            TransactionalEventPublisher eventPublisher,
            CreateOrderUseCase createOrderUseCase,
            Clock clock) {
        super(
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_CREATE),
                payloadPort,
                processingRecordPort,
                eventPublisher,
                clock);
        this.createOrderUseCase = Objects.requireNonNull(createOrderUseCase, "createOrderUseCase");
    }

    /**
     * Registers this processor through the public Document Engine registration API.
     */
    public DocumentProcessorRegistration register(DocumentEngine documentEngine) {
        Objects.requireNonNull(documentEngine, "documentEngine");
        return documentEngine.registerProcessor(this);
    }

    @Override
    protected ResultReference executeBusinessAction(
            DocumentOperationContext context, OrderDocumentPayload payload) {
        OrderCreatePayload createPayload = (OrderCreatePayload) payload;
        CustomerOrder order =
                createOrderUseCase.execute(
                        new CreateOrderCommand(
                                createPayload.orderNumber(), createPayload.commercialData()));
        return ResultReference.of(RESULT_PREFIX + order.id().value());
    }

    @Override
    protected DomainEvent createDomainEvent(
            DocumentOperationContext context,
            OrderDocumentPayload payload,
            ResultReference resultReference) {
        return new OrderCreated(
                orderIdFrom(resultReference), context.document().id().toString());
    }

    public static OrderId orderIdFrom(ResultReference resultReference) {
        Objects.requireNonNull(resultReference, "resultReference");
        String value = resultReference.value();
        if (!value.startsWith(RESULT_PREFIX)) {
            throw new IllegalStateException("Unexpected ORDER_CREATE result reference: " + value);
        }
        return OrderId.of(UUID.fromString(value.substring(RESULT_PREFIX.length())));
    }
}
