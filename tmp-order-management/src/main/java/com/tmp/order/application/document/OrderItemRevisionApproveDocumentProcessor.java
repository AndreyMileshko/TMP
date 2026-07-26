package com.tmp.order.application.document;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.event.OrderItemRevisionApproved;
import com.tmp.order.application.item.ApproveOrderItemRevisionCommand;
import com.tmp.order.application.item.ApproveOrderItemRevisionUseCase;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderItemRevisionApprovePayload;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.processing.ResultReference;
import com.tmp.order.domain.OrderItem;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Document processor for {@code ORDER_ITEM_REVISION_APPROVE} (Specification §6.4 / §13 / §17).
 */
public final class OrderItemRevisionApproveDocumentProcessor extends AbstractOrderDocumentProcessor {

    private static final String RESULT_PREFIX = "orderItem:";

    private final ApproveOrderItemRevisionUseCase approveOrderItemRevisionUseCase;

    public OrderItemRevisionApproveDocumentProcessor(
            OrderDocumentPayloadPort payloadPort,
            ProcessingRecordPort processingRecordPort,
            TransactionalEventPublisher eventPublisher,
            ApproveOrderItemRevisionUseCase approveOrderItemRevisionUseCase,
            Clock clock) {
        super(
                OrderBusinessDocumentCatalog.requireByCode(
                        DocumentTypeCode.ORDER_ITEM_REVISION_APPROVE),
                payloadPort,
                processingRecordPort,
                eventPublisher,
                clock);
        this.approveOrderItemRevisionUseCase =
                Objects.requireNonNull(
                        approveOrderItemRevisionUseCase, "approveOrderItemRevisionUseCase");
    }

    public DocumentProcessorRegistration register(DocumentEngine documentEngine) {
        Objects.requireNonNull(documentEngine, "documentEngine");
        return documentEngine.registerProcessor(this);
    }

    @Override
    protected ResultReference executeBusinessAction(
            DocumentOperationContext context, OrderDocumentPayload payload) {
        OrderItemRevisionApprovePayload approvePayload = (OrderItemRevisionApprovePayload) payload;
        OrderItem item =
                approveOrderItemRevisionUseCase.execute(
                        new ApproveOrderItemRevisionCommand(
                                approvePayload.orderItemId(), approvePayload.revisionNumber()));
        return ResultReference.of(
                RESULT_PREFIX
                        + item.id().value()
                        + ":order:"
                        + item.orderId().value()
                        + ":rev:"
                        + approvePayload.revisionNumber().value());
    }

    @Override
    protected DomainEvent createDomainEvent(
            DocumentOperationContext context,
            OrderDocumentPayload payload,
            ResultReference resultReference) {
        OrderItemRevisionApprovePayload approvePayload = (OrderItemRevisionApprovePayload) payload;
        return new OrderItemRevisionApproved(
                orderIdFrom(resultReference),
                approvePayload.orderItemId(),
                approvePayload.revisionNumber(),
                context.document().id().toString());
    }

    static OrderId orderIdFrom(ResultReference resultReference) {
        Objects.requireNonNull(resultReference, "resultReference");
        String value = resultReference.value();
        int orderMarker = value.indexOf(":order:");
        int revMarker = value.indexOf(":rev:");
        if (orderMarker < 0 || revMarker < 0 || revMarker <= orderMarker) {
            throw new IllegalStateException(
                    "Unexpected ORDER_ITEM_REVISION_APPROVE result reference: " + value);
        }
        String orderId = value.substring(orderMarker + ":order:".length(), revMarker);
        return OrderId.of(UUID.fromString(orderId));
    }
}
