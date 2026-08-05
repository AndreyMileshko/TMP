package com.tmp.order.application.document;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.api.event.OrderApproved;
import com.tmp.order.application.imports.ImportApproveGate;
import com.tmp.order.application.order.ApproveOrderCommand;
import com.tmp.order.application.order.ApproveOrderUseCase;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderApprovePayload;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.processing.ResultReference;
import com.tmp.order.domain.CustomerOrder;
import java.time.Clock;
import java.util.Objects;

/**
 * Document processor for {@code ORDER_APPROVE} (Specification §13 / §14).
 *
 * <p>Manual posts use full ADR-030 commercial gates. Import Core posts under
 * {@link ImportApproveGate} and uses import landing rules (client + ACTIVE item).
 */
public final class OrderApproveDocumentProcessor extends AbstractOrderDocumentProcessor {

    private static final String RESULT_PREFIX = "order:";

    private final ApproveOrderUseCase approveOrderUseCase;

    public OrderApproveDocumentProcessor(
            OrderDocumentPayloadPort payloadPort,
            ProcessingRecordPort processingRecordPort,
            TransactionalEventPublisher eventPublisher,
            ApproveOrderUseCase approveOrderUseCase,
            Clock clock) {
        super(
                OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_APPROVE),
                payloadPort,
                processingRecordPort,
                eventPublisher,
                clock);
        this.approveOrderUseCase =
                Objects.requireNonNull(approveOrderUseCase, "approveOrderUseCase");
    }

    public DocumentProcessorRegistration register(DocumentEngine documentEngine) {
        Objects.requireNonNull(documentEngine, "documentEngine");
        return documentEngine.registerProcessor(this);
    }

    @Override
    protected ResultReference executeBusinessAction(
            DocumentOperationContext context, OrderDocumentPayload payload) {
        OrderApprovePayload approvePayload = (OrderApprovePayload) payload;
        ApproveOrderCommand command = new ApproveOrderCommand(approvePayload.orderId());
        CustomerOrder order =
                ImportApproveGate.isActive()
                        ? approveOrderUseCase.executeForImport(command)
                        : approveOrderUseCase.execute(command);
        return ResultReference.of(RESULT_PREFIX + order.id().value());
    }

    @Override
    protected DomainEvent createDomainEvent(
            DocumentOperationContext context,
            OrderDocumentPayload payload,
            ResultReference resultReference) {
        OrderApprovePayload approvePayload = (OrderApprovePayload) payload;
        return new OrderApproved(approvePayload.orderId());
    }
}
