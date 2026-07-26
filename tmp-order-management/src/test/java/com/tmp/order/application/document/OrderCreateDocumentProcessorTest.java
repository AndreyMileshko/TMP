package com.tmp.order.application.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.DocumentStatus;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.event.OrderCreated;
import com.tmp.order.application.order.CreateOrderUseCase;
import com.tmp.order.application.order.DuplicateOrderNumberException;
import com.tmp.order.application.order.InMemoryCustomerOrderRepository;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.InMemoryOrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderCreatePayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderUpdatePayload;
import com.tmp.order.application.processing.InMemoryProcessingRecordPort;
import com.tmp.order.application.processing.ProcessingOperation;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.domain.CurrencyCode;
import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderDirection;
import com.tmp.order.domain.OrderNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderCreateDocumentProcessorTest {

    private static final Instant NOW = Instant.parse("2026-07-26T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OrderDocumentPayloadPort payloads;
    private ProcessingRecordPort processing;
    private InMemoryCustomerOrderRepository orders;
    private List<DomainEvent> published;
    private OrderCreateDocumentProcessor processor;

    @BeforeEach
    void setUp() {
        payloads = new InMemoryOrderDocumentPayloadPort();
        processing = new InMemoryProcessingRecordPort();
        orders = new InMemoryCustomerOrderRepository();
        published = new ArrayList<>();
        TransactionalEventPublisher publisher = published::add;
        CreateOrderUseCase useCase = new CreateOrderUseCase(orders, CLOCK);
        processor =
                new OrderCreateDocumentProcessor(
                        payloads, processing, publisher, useCase, CLOCK);
    }

    @Test
    void onPostCreatesDraftOrderAndPublishesOrderCreated() {
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderCreatePayload.create(
                        documentId, OrderNumber.of("ORD-CREATE-1"), commercial("C-1"), NOW));

        processor.onPost(context(documentId, DocumentStatus.DRAFT));

        assertEquals(1, orders.size());
        assertEquals(1, published.size());
        OrderCreated event = assertInstanceOf(OrderCreated.class, published.get(0));
        CustomerOrder order = orders.findById(event.orderId()).orElseThrow();
        assertEquals(OrderStatus.DRAFT, order.status());
        assertEquals("ORD-CREATE-1", order.orderNumber().value());
        assertEquals(documentId.value().toString(), event.correlationId());
        assertTrue(processing.exists(documentId, ProcessingOperation.POST));
        assertTrue(
                processing
                        .findByDocumentIdAndOperation(documentId, ProcessingOperation.POST)
                        .orElseThrow()
                        .resultReference()
                        .orElseThrow()
                        .value()
                        .contains(order.id().value().toString()));
    }

    @Test
    void duplicateOrderNumberIsRejected() {
        DocumentId firstDoc = DocumentId.generate();
        DocumentId secondDoc = DocumentId.generate();
        OrderNumber number = OrderNumber.of("ORD-DUP");
        payloads.create(OrderCreatePayload.create(firstDoc, number, commercial("A"), NOW));
        payloads.create(OrderCreatePayload.create(secondDoc, number, commercial("B"), NOW));

        processor.onPost(context(firstDoc, DocumentStatus.DRAFT));
        assertThrows(
                DuplicateOrderNumberException.class,
                () -> processor.onPost(context(secondDoc, DocumentStatus.DRAFT)));
        assertEquals(1, orders.size());
        assertEquals(1, published.size());
        assertTrue(processing.exists(firstDoc, ProcessingOperation.POST));
        assertTrue(!processing.exists(secondDoc, ProcessingOperation.POST));
    }

    @Test
    void wrongPayloadTypeIsRejectedByBaseProcessor() {
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderUpdatePayload.create(
                        documentId, OrderId.generate(), commercial("X"), NOW));

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> processor.onPost(context(documentId, DocumentStatus.DRAFT)));
        assertTrue(ex.getMessage().contains("does not match processor"));
        assertEquals(0, orders.size());
        assertTrue(published.isEmpty());
        assertTrue(!processing.exists(documentId, ProcessingOperation.POST));
    }

    @Test
    void repeatedOnPostDoesNotCreateSecondOrderOrEvent() {
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderCreatePayload.create(
                        documentId, OrderNumber.of("ORD-IDEMP"), commercial("C"), NOW));
        DocumentOperationContext ctx = context(documentId, DocumentStatus.DRAFT);

        processor.onPost(ctx);
        processor.onPost(ctx);

        assertEquals(1, orders.size());
        assertEquals(1, published.size());
    }

    @Test
    void registerUsesPublicDocumentEngineApi() {
        AtomicReference<DocumentProcessor> registered = new AtomicReference<>();
        DocumentEngine engine =
                new DocumentEngine() {
                    @Override
                    public DocumentProcessorRegistration registerProcessor(
                            DocumentProcessor documentProcessor) {
                        registered.set(documentProcessor);
                        return new DocumentProcessorRegistration() {
                            @Override
                            public String documentTypeId() {
                                return documentProcessor.documentTypeId();
                            }

                            @Override
                            public void unregister() {}

                            @Override
                            public void deactivate() {}
                        };
                    }

                    @Override
                    public DocumentMetadata createDocument(
                            com.tmp.document.api.CreateDocumentCommand command) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public DocumentMetadata updateDocument(
                            com.tmp.document.api.UpdateDocumentCommand command) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public DocumentMetadata postDocument(java.util.UUID documentId) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public DocumentMetadata unpostDocument(java.util.UUID documentId) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public DocumentMetadata closeDocument(java.util.UUID documentId) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void deleteDocument(java.util.UUID documentId) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public java.util.Optional<DocumentMetadata> findById(
                            java.util.UUID documentId) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public java.util.List<DocumentMetadata> search(
                            com.tmp.document.api.DocumentQuery query) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public java.util.List<com.tmp.document.api.DocumentTypeDescriptor>
                            registeredTypes() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public com.tmp.document.api.DocumentEngineStatus status() {
                        throw new UnsupportedOperationException();
                    }
                };

        DocumentProcessorRegistration registration = processor.register(engine);
        assertEquals(DocumentTypeCode.ORDER_CREATE.name(), registration.documentTypeId());
        assertEquals(processor, registered.get());
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

    private static OrderCommercialData commercial(String customerRef) {
        return OrderCommercialData.of(
                customerRef,
                "Customer",
                null,
                null,
                null,
                OrderDirection.PRIVATE,
                CurrencyCode.of("USD"));
    }
}
