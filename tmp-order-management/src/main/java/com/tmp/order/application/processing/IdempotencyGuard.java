package com.tmp.order.application.processing;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.application.payload.DocumentId;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Idempotency guard for {@code DocumentProcessor.onPost} (Specification §16).
 *
 * <p>When a processing record for {@link DocumentId} + {@link ProcessingOperation#POST} already
 * exists: business action is skipped, no event is published, no new record is written, and the
 * method returns normally ({@code void} semantics — nothing is returned to Document Engine).
 */
public final class IdempotencyGuard {

    private final ProcessingRecordPort processingRecordPort;
    private final TransactionalEventPublisher eventPublisher;

    public IdempotencyGuard(
            ProcessingRecordPort processingRecordPort, TransactionalEventPublisher eventPublisher) {
        this.processingRecordPort =
                Objects.requireNonNull(processingRecordPort, "processingRecordPort");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    public boolean alreadyProcessed(DocumentId documentId, ProcessingOperation operation) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(operation, "operation");
        return processingRecordPort.exists(documentId, operation);
    }

    /**
     * Executes the posting side-effects at most once for {@code documentId + POST}.
     *
     * <p>Return type is {@code void} to mirror {@code DocumentProcessor.onPost}. {@link
     * ResultReference} stays inside {@link ProcessingRecord} and is never exposed.
     *
     * @param documentId platform document id
     * @param businessAction aggregate change; invoked only on first processing
     * @param recordFactory builds the processing record after a successful business action
     * @param eventFactory builds the domain event after a successful business action; published
     *     after commit via {@link TransactionalEventPublisher}
     */
    public void runPostOnce(
            DocumentId documentId,
            Runnable businessAction,
            Supplier<ProcessingRecord> recordFactory,
            Supplier<? extends DomainEvent> eventFactory) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(businessAction, "businessAction");
        Objects.requireNonNull(recordFactory, "recordFactory");
        Objects.requireNonNull(eventFactory, "eventFactory");

        if (alreadyProcessed(documentId, ProcessingOperation.POST)) {
            return;
        }

        businessAction.run();

        ProcessingRecord record = Objects.requireNonNull(recordFactory.get(), "record");
        if (!record.documentId().equals(documentId)
                || record.operation() != ProcessingOperation.POST) {
            throw new IllegalArgumentException(
                    "Processing record must match documentId and POST operation");
        }

        try {
            processingRecordPort.insert(record);
        } catch (DuplicateProcessingRecordException duplicate) {
            // Concurrent first-time processors: treat as already processed — do not publish again.
            return;
        }

        DomainEvent event = Objects.requireNonNull(eventFactory.get(), "event");
        eventPublisher.publishAfterCommit(event);
    }
}
