package com.tmp.order.application.processing;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.application.payload.DocumentId;
import java.util.List;
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
     * Executes the posting side-effects at most once for {@code documentId + POST}, publishing a
     * single domain event after a successful insert.
     */
    public void runPostOnce(
            DocumentId documentId,
            Runnable businessAction,
            Supplier<ProcessingRecord> recordFactory,
            Supplier<? extends DomainEvent> eventFactory) {
        Objects.requireNonNull(eventFactory, "eventFactory");
        runPostOncePublishing(
                documentId,
                businessAction,
                recordFactory,
                () -> List.of(Objects.requireNonNull(eventFactory.get(), "event")));
    }

    /**
     * Executes the posting side-effects at most once for {@code documentId + POST}, publishing an
     * immutable list of domain events after a successful insert. On duplicate processing or
     * concurrent insert conflict, no events are published.
     */
    public void runPostOncePublishing(
            DocumentId documentId,
            Runnable businessAction,
            Supplier<ProcessingRecord> recordFactory,
            Supplier<? extends List<? extends DomainEvent>> eventsFactory) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(businessAction, "businessAction");
        Objects.requireNonNull(recordFactory, "recordFactory");
        Objects.requireNonNull(eventsFactory, "eventsFactory");

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

        List<? extends DomainEvent> events =
                Objects.requireNonNull(eventsFactory.get(), "events");
        if (events.isEmpty()) {
            throw new IllegalArgumentException("at least one domain event is required");
        }
        for (DomainEvent event : events) {
            eventPublisher.publishAfterCommit(Objects.requireNonNull(event, "event"));
        }
    }
}
