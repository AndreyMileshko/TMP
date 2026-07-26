package com.tmp.order.application.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.event.AbstractDomainEvent;
import com.tmp.core.api.event.DomainEvent;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.domain.PayloadRevision;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdempotencyGuardTest {

    private static final Instant NOW = Instant.parse("2026-07-25T16:00:00Z");

    private InMemoryProcessingRecordPort port;
    private List<DomainEvent> published;
    private IdempotencyGuard guard;

    @BeforeEach
    void setUp() {
        port = new InMemoryProcessingRecordPort();
        published = new ArrayList<>();
        TransactionalEventPublisher publisher = published::add;
        guard = new IdempotencyGuard(port, publisher);
    }

    @Test
    void firstPostRunsBusinessActionWritesRecordAndPublishesEvent() {
        DocumentId documentId = DocumentId.generate();
        AtomicInteger businessCalls = new AtomicInteger();

        guard.runPostOnce(
                documentId,
                businessCalls::incrementAndGet,
                () -> record(documentId),
                () -> new SampleEvent());

        assertEquals(1, businessCalls.get());
        assertEquals(1, published.size());
        assertTrue(port.exists(documentId, ProcessingOperation.POST));
        assertEquals(
                ResultReference.of("order:" + documentId),
                port.findByDocumentIdAndOperation(documentId, ProcessingOperation.POST)
                        .orElseThrow()
                        .resultReference()
                        .orElseThrow());
    }

    @Test
    void secondPostDoesNotRepeatBusinessActionOrEventOrRecord() {
        DocumentId documentId = DocumentId.generate();
        AtomicInteger businessCalls = new AtomicInteger();

        Runnable action = businessCalls::incrementAndGet;
        guard.runPostOnce(documentId, action, () -> record(documentId), SampleEvent::new);
        guard.runPostOnce(documentId, action, () -> record(documentId), SampleEvent::new);

        assertEquals(1, businessCalls.get());
        assertEquals(1, published.size());
        assertEquals(
                1,
                port.findByDocumentIdAndOperation(documentId, ProcessingOperation.POST).stream()
                        .count());
    }

    @Test
    void concurrentDuplicateInsertIsTreatedAsAlreadyProcessedWithoutSecondEvent() {
        DocumentId documentId = DocumentId.generate();
        ProcessingRecordPort racingPort =
                new ProcessingRecordPort() {
                    @Override
                    public java.util.Optional<ProcessingRecord> findByDocumentIdAndOperation(
                            DocumentId id, ProcessingOperation operation) {
                        return java.util.Optional.empty();
                    }

                    @Override
                    public void insert(ProcessingRecord record) {
                        throw new DuplicateProcessingRecordException(
                                record.documentId(), record.operation());
                    }

                    @Override
                    public boolean exists(DocumentId id, ProcessingOperation operation) {
                        return false;
                    }
                };
        List<DomainEvent> events = new ArrayList<>();
        IdempotencyGuard racingGuard = new IdempotencyGuard(racingPort, events::add);
        AtomicInteger businessCalls = new AtomicInteger();

        racingGuard.runPostOnce(
                documentId,
                businessCalls::incrementAndGet,
                () -> record(documentId),
                SampleEvent::new);

        assertEquals(1, businessCalls.get(), "Business may run before losing the insert race");
        assertTrue(events.isEmpty(), "Losing the insert race must not publish an event");
    }

    @Test
    void onPostContractRemainsVoidAndResultReferenceIsInternal() throws Exception {
        Method runPostOnce =
                IdempotencyGuard.class.getMethod(
                        "runPostOnce",
                        DocumentId.class,
                        Runnable.class,
                        java.util.function.Supplier.class,
                        java.util.function.Supplier.class);
        assertEquals(void.class, runPostOnce.getReturnType());

        Method onPost =
                com.tmp.document.api.DocumentProcessor.class.getMethod(
                        "onPost", com.tmp.document.api.DocumentOperationContext.class);
        assertEquals(void.class, onPost.getReturnType());

        ProcessingRecord stored = record(DocumentId.generate());
        assertTrue(stored.resultReference().isPresent());
        assertFalse(
                ProcessingRecord.class.getPackageName().startsWith("com.tmp.order.api"),
                "ProcessingRecord must not be public API");
    }

    private static ProcessingRecord record(DocumentId documentId) {
        return ProcessingRecord.completedPost(
                documentId,
                DocumentTypeCode.ORDER_CREATE,
                PayloadRevision.initial(),
                NOW,
                ResultReference.of("order:" + documentId));
    }

    private static final class SampleEvent extends AbstractDomainEvent {
        private SampleEvent() {
            super("order.management");
        }
    }
}
