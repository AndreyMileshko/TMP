package com.tmp.order.application.document;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.document.api.DocumentStatus;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.payload.PayloadNotFoundException;
import com.tmp.order.application.processing.IdempotencyGuard;
import com.tmp.order.application.processing.ProcessingOperation;
import com.tmp.order.application.processing.ProcessingRecord;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.processing.ResultReference;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared Order Management document lifecycle policy (Specification §14) without concrete
 * {@code ORDER_*} business logic.
 *
 * <p>Uses only the public {@link TransactionalEventPublisher}. {@link #onPost} remains {@code void}.
 * Processors may publish one or more domain events via {@link #createDomainEvents}.
 */
public abstract class AbstractOrderDocumentProcessor implements DocumentProcessor {

    private final OrderDocumentTypeDescriptor descriptor;
    private final OrderDocumentPayloadPort payloadPort;
    private final IdempotencyGuard idempotencyGuard;
    private final Clock clock;

    protected AbstractOrderDocumentProcessor(
            OrderDocumentTypeDescriptor descriptor,
            OrderDocumentPayloadPort payloadPort,
            ProcessingRecordPort processingRecordPort,
            TransactionalEventPublisher eventPublisher,
            Clock clock) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.payloadPort = Objects.requireNonNull(payloadPort, "payloadPort");
        this.idempotencyGuard =
                new IdempotencyGuard(
                        Objects.requireNonNull(processingRecordPort, "processingRecordPort"),
                        Objects.requireNonNull(eventPublisher, "eventPublisher"));
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public final String documentTypeId() {
        return descriptor.documentTypeId();
    }

    protected final OrderDocumentTypeDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public void validateCreate(DocumentOperationContext context) {
        // Default: no additional validation. Concrete processors may override.
    }

    @Override
    public void validateUpdate(DocumentOperationContext context) {
        // Default: no additional validation. Concrete processors may override.
    }

    @Override
    public final void onPost(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
        DocumentId documentId = DocumentId.of(context.document().id());
        OrderDocumentPayload payload = loadAndValidatePayload(documentId);

        AtomicReference<ResultReference> resultHolder = new AtomicReference<>();
        idempotencyGuard.runPostOncePublishing(
                documentId,
                () -> resultHolder.set(executeBusinessAction(context, payload)),
                () ->
                        ProcessingRecord.completedPost(
                                documentId,
                                descriptor.documentTypeCode(),
                                payload.identity().payloadRevision(),
                                clock.instant(),
                                resultHolder.get()),
                () -> createDomainEvents(context, payload, resultHolder.get()));
    }

    @Override
    public final void onUnpost(DocumentOperationContext context) {
        throw new UnsupportedOperationException("UNPOST IS NOT SUPPORTED");
    }

    @Override
    public final void onClose(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
        // No aggregate change, no payload delete, no processing record, no event.
    }

    @Override
    public final void onDelete(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
        if (context.document().status() != DocumentStatus.DRAFT) {
            throw new IllegalStateException(
                    "Delete is allowed only for DRAFT documents: " + context.document().id());
        }
        DocumentId documentId = DocumentId.of(context.document().id());
        if (payloadPort.existsByDocumentId(documentId)) {
            payloadPort.deleteDraft(documentId);
        }
        // No aggregate change, no processing record, no event.
    }

    /**
     * Performs the single business change for this document type. Invoked only when the document
     * has not already been processed.
     *
     * @return internal result reference stored on the processing record (never returned from
     *     {@code onPost})
     */
    protected abstract ResultReference executeBusinessAction(
            DocumentOperationContext context, OrderDocumentPayload payload);

    /**
     * Builds the domain events published after commit on first successful processing.
     *
     * <p>Default wraps {@link #createDomainEvent} for single-event processors. Multi-event
     * processors override this method and return an immutable list.
     */
    protected List<DomainEvent> createDomainEvents(
            DocumentOperationContext context,
            OrderDocumentPayload payload,
            ResultReference resultReference) {
        return List.of(createDomainEvent(context, payload, resultReference));
    }

    /**
     * Builds the single domain event for processors that publish exactly one event.
     *
     * <p>Multi-event processors override {@link #createDomainEvents} instead and need not call
     * this method.
     */
    protected DomainEvent createDomainEvent(
            DocumentOperationContext context,
            OrderDocumentPayload payload,
            ResultReference resultReference) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName()
                        + " must override createDomainEvent or createDomainEvents");
    }

    private OrderDocumentPayload loadAndValidatePayload(DocumentId documentId) {
        OrderDocumentPayload payload =
                payloadPort
                        .findByDocumentId(documentId)
                        .orElseThrow(() -> new PayloadNotFoundException(documentId));
        if (payload.documentTypeCode() != descriptor.documentTypeCode()) {
            throw new IllegalStateException(
                    "Payload type "
                            + payload.documentTypeCode()
                            + " does not match processor "
                            + descriptor.documentTypeCode());
        }
        if (!payload.identity().schemaVersion().equals(descriptor.payloadSchemaVersion())) {
            throw new IllegalStateException(
                    "Unsupported payload schema version "
                            + payload.identity().schemaVersion()
                            + " for "
                            + descriptor.documentTypeCode()
                            + "; expected "
                            + descriptor.payloadSchemaVersion());
        }
        if (!descriptor.payloadClass().isInstance(payload)) {
            throw new IllegalStateException(
                    "Payload class "
                            + payload.getClass().getName()
                            + " is not "
                            + descriptor.payloadClass().getName());
        }
        return payload;
    }

    /** Visible for tests: whether POST was already recorded. */
    protected final boolean alreadyProcessed(DocumentId documentId) {
        return idempotencyGuard.alreadyProcessed(documentId, ProcessingOperation.POST);
    }
}
