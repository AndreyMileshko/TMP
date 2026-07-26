package com.tmp.order.application.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.event.AbstractDomainEvent;
import com.tmp.core.api.event.DomainEvent;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentStatus;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.InMemoryOrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderCreatePayload;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.processing.InMemoryProcessingRecordPort;
import com.tmp.order.application.processing.ProcessingOperation;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.processing.ResultReference;
import com.tmp.order.domain.CurrencyCode;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderDirection;
import com.tmp.order.domain.OrderNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractOrderDocumentProcessorTest {

    private static final Instant NOW = Instant.parse("2026-07-25T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OrderDocumentPayloadPort payloads;
    private ProcessingRecordPort processing;
    private List<DomainEvent> published;
    private AtomicInteger businessCalls;
    private TestOrderCreateProcessor processor;

    @BeforeEach
    void setUp() {
        payloads = new InMemoryOrderDocumentPayloadPort();
        processing = new InMemoryProcessingRecordPort();
        published = new ArrayList<>();
        businessCalls = new AtomicInteger();
        TransactionalEventPublisher publisher = published::add;
        processor =
                new TestOrderCreateProcessor(
                        payloads, processing, publisher, CLOCK, businessCalls);
    }

    @Test
    void onPostRunsBusinessWritesRecordAndPublishesOnce() {
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderCreatePayload.create(
                        documentId, OrderNumber.of("PR-1"), commercial(), NOW));

        processor.onPost(context(documentId, DocumentStatus.DRAFT));

        assertEquals(1, businessCalls.get());
        assertEquals(1, published.size());
        assertTrue(processing.exists(documentId, ProcessingOperation.POST));
    }

    @Test
    void onPostIdempotencySkipsBusinessAndEvent() {
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderCreatePayload.create(
                        documentId, OrderNumber.of("PR-2"), commercial(), NOW));
        DocumentOperationContext ctx = context(documentId, DocumentStatus.DRAFT);

        processor.onPost(ctx);
        processor.onPost(ctx);

        assertEquals(1, businessCalls.get());
        assertEquals(1, published.size());
    }

    @Test
    void onUnpostIsAlwaysRejected() {
        DocumentId documentId = DocumentId.generate();
        UnsupportedOperationException ex =
                assertThrows(
                        UnsupportedOperationException.class,
                        () -> processor.onUnpost(context(documentId, DocumentStatus.POSTED)));
        assertTrue(ex.getMessage().contains("UNPOST IS NOT SUPPORTED"));
        assertEquals(0, businessCalls.get());
        assertTrue(published.isEmpty());
    }

    @Test
    void onCloseDoesNotMutatePayloadOrPublish() {
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderCreatePayload.create(
                        documentId, OrderNumber.of("PR-3"), commercial(), NOW));
        processor.onClose(context(documentId, DocumentStatus.POSTED));
        assertTrue(payloads.existsByDocumentId(documentId));
        assertFalse(processing.exists(documentId, ProcessingOperation.POST));
        assertTrue(published.isEmpty());
        assertEquals(0, businessCalls.get());
    }

    @Test
    void onDeleteRemovesDraftPayloadOnly() {
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderCreatePayload.create(
                        documentId, OrderNumber.of("PR-4"), commercial(), NOW));
        processor.onDelete(context(documentId, DocumentStatus.DRAFT));
        assertFalse(payloads.existsByDocumentId(documentId));
        assertFalse(processing.exists(documentId, ProcessingOperation.POST));
        assertTrue(published.isEmpty());
        assertEquals(0, businessCalls.get());
    }

    @Test
    void onDeleteRejectsNonDraft() {
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderCreatePayload.create(
                        documentId, OrderNumber.of("PR-5"), commercial(), NOW));
        assertThrows(
                IllegalStateException.class,
                () -> processor.onDelete(context(documentId, DocumentStatus.POSTED)));
        assertTrue(payloads.existsByDocumentId(documentId));
    }

    @Test
    void onPostRemainsVoid() throws Exception {
        assertEquals(
                void.class,
                AbstractOrderDocumentProcessor.class
                        .getMethod("onPost", DocumentOperationContext.class)
                        .getReturnType());
    }

    private static DocumentOperationContext context(DocumentId documentId, DocumentStatus status) {
        DocumentMetadata metadata =
                new DocumentMetadata(
                        documentId.value(),
                        DocumentTypeCode.ORDER_CREATE.name(),
                        "DOC-" + documentId,
                        "title",
                        status,
                        0L,
                        NOW,
                        NOW,
                        status == DocumentStatus.DRAFT ? null : NOW,
                        null);
        return new DocumentOperationContext() {
            @Override
            public DocumentMetadata document() {
                return metadata;
            }
        };
    }

    private static OrderCommercialData commercial() {
        return OrderCommercialData.of(
                "C-1",
                "Customer",
                null,
                null,
                null,
                OrderDirection.PRIVATE,
                CurrencyCode.of("USD"));
    }

    private static final class TestOrderCreateProcessor extends AbstractOrderDocumentProcessor {
        private final AtomicInteger businessCalls;

        private TestOrderCreateProcessor(
                OrderDocumentPayloadPort payloads,
                ProcessingRecordPort processing,
                TransactionalEventPublisher publisher,
                Clock clock,
                AtomicInteger businessCalls) {
            super(
                    OrderBusinessDocumentCatalog.requireByCode(DocumentTypeCode.ORDER_CREATE),
                    payloads,
                    processing,
                    publisher,
                    clock);
            this.businessCalls = businessCalls;
        }

        @Override
        protected ResultReference executeBusinessAction(
                DocumentOperationContext context, OrderDocumentPayload payload) {
            businessCalls.incrementAndGet();
            return ResultReference.of("order:" + UUID.randomUUID());
        }

        @Override
        protected DomainEvent createDomainEvent(
                DocumentOperationContext context,
                OrderDocumentPayload payload,
                ResultReference resultReference) {
            return new SampleEvent();
        }
    }

    private static final class SampleEvent extends AbstractDomainEvent {
        private SampleEvent() {
            super("order.management");
        }
    }
}
