package com.tmp.order.application.document;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.event.OrderItemCreated;
import com.tmp.order.api.event.OrderItemRevisionCreated;
import com.tmp.order.application.item.CreateOrderItemCommand;
import com.tmp.order.application.item.CreateOrderItemUseCase;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderItemCreatePayload;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.processing.ResultReference;
import com.tmp.order.domain.OrderItem;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Document processor for {@code ORDER_ITEM_CREATE} (Specification §13 / §14 / §17).
 *
 * <p>Publishes {@link OrderItemCreated} and {@link OrderItemRevisionCreated} after commit.
 */
public final class OrderItemCreateDocumentProcessor extends AbstractOrderDocumentProcessor {

    private static final String RESULT_PREFIX = "orderItem:";

    private final CreateOrderItemUseCase createOrderItemUseCase;

    public OrderItemCreateDocumentProcessor(
            OrderDocumentPayloadPort payloadPort,
            ProcessingRecordPort processingRecordPort,
            TransactionalEventPublisher eventPublisher,
            CreateOrderItemUseCase createOrderItemUseCase,
            Clock clock) {
        super(
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_ITEM_CREATE),
                payloadPort,
                processingRecordPort,
                eventPublisher,
                clock);
        this.createOrderItemUseCase =
                Objects.requireNonNull(createOrderItemUseCase, "createOrderItemUseCase");
    }

    public DocumentProcessorRegistration register(DocumentEngine documentEngine) {
        Objects.requireNonNull(documentEngine, "documentEngine");
        return documentEngine.registerProcessor(this);
    }

    @Override
    protected ResultReference executeBusinessAction(
            DocumentOperationContext context, OrderDocumentPayload payload) {
        OrderItemCreatePayload createPayload = (OrderItemCreatePayload) payload;
        OrderItem item =
                createOrderItemUseCase.execute(
                        new CreateOrderItemCommand(
                                createPayload.orderId(),
                                createPayload.orderItemId(),
                                createPayload.commercialData(),
                                createPayload.orderedQuantity()));
        return ResultReference.of(RESULT_PREFIX + item.id().value());
    }

    @Override
    protected List<DomainEvent> createDomainEvents(
            DocumentOperationContext context,
            OrderDocumentPayload payload,
            ResultReference resultReference) {
        OrderItemCreatePayload createPayload = (OrderItemCreatePayload) payload;
        return List.of(
                new OrderItemCreated(createPayload.orderId(), createPayload.orderItemId()),
                new OrderItemRevisionCreated(
                        createPayload.orderItemId(), RevisionNumber.first()));
    }
}
