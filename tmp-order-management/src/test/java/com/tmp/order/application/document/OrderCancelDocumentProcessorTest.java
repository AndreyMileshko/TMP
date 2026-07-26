package com.tmp.order.application.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentStatus;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.event.OrderCancelled;
import com.tmp.order.application.order.CancelOrderUseCase;
import com.tmp.order.application.order.InMemoryCustomerOrderRepository;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.InMemoryOrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderCancelPayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.processing.InMemoryProcessingRecordPort;
import com.tmp.order.application.processing.ProcessingOperation;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.domain.CurrencyCode;
import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.InvalidOrderStateException;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderDirection;
import com.tmp.order.domain.OrderNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderCancelDocumentProcessorTest {

    private static final Instant NOW = Instant.parse("2026-07-26T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OrderDocumentPayloadPort payloads;
    private ProcessingRecordPort processing;
    private InMemoryCustomerOrderRepository orders;
    private List<DomainEvent> published;
    private OrderCancelDocumentProcessor processor;

    @BeforeEach
    void setUp() {
        payloads = new InMemoryOrderDocumentPayloadPort();
        processing = new InMemoryProcessingRecordPort();
        orders = new InMemoryCustomerOrderRepository();
        published = new ArrayList<>();
        TransactionalEventPublisher publisher = published::add;
        processor =
                new OrderCancelDocumentProcessor(
                        payloads,
                        processing,
                        publisher,
                        new CancelOrderUseCase(orders, CLOCK),
                        CLOCK);
    }

    @Test
    void draftOrderBecomesCancelled() {
        CustomerOrder draft = seedDraft("ORD-CAN-1");
        DocumentId documentId = DocumentId.generate();
        payloads.create(OrderCancelPayload.create(documentId, draft.id(), NOW));

        processor.onPost(context(documentId));

        assertEquals(OrderStatus.CANCELLED, orders.findById(draft.id()).orElseThrow().status());
        assertEquals(1, published.size());
        assertEquals(
                draft.id(), assertInstanceOf(OrderCancelled.class, published.get(0)).orderId());
        assertTrue(processing.exists(documentId, ProcessingOperation.POST));
    }

    @Test
    void approvedOrderCannotBeCancelled() {
        CustomerOrder approved = seedDraft("ORD-CAN-APPR").approve(CLOCK);
        orders.save(approved);
        DocumentId documentId = DocumentId.generate();
        payloads.create(OrderCancelPayload.create(documentId, approved.id(), NOW));

        assertThrows(
                InvalidOrderStateException.class, () -> processor.onPost(context(documentId)));
        assertEquals(OrderStatus.APPROVED, orders.findById(approved.id()).orElseThrow().status());
        assertTrue(published.isEmpty());
        assertTrue(!processing.exists(documentId, ProcessingOperation.POST));
    }

    @Test
    void repeatedCancelIsRejectedByDomain() {
        CustomerOrder cancelled = seedDraft("ORD-CAN-AGAIN").cancel(CLOCK);
        orders.save(cancelled);
        DocumentId documentId = DocumentId.generate();
        payloads.create(OrderCancelPayload.create(documentId, cancelled.id(), NOW));

        assertThrows(
                InvalidOrderStateException.class, () -> processor.onPost(context(documentId)));
        assertTrue(published.isEmpty());
        assertTrue(!processing.exists(documentId, ProcessingOperation.POST));
    }

    @Test
    void repeatedOnPostIsIdempotent() {
        CustomerOrder draft = seedDraft("ORD-CAN-IDEM");
        DocumentId documentId = DocumentId.generate();
        payloads.create(OrderCancelPayload.create(documentId, draft.id(), NOW));
        DocumentOperationContext ctx = context(documentId);

        processor.onPost(ctx);
        processor.onPost(ctx);

        assertEquals(OrderStatus.CANCELLED, orders.findById(draft.id()).orElseThrow().status());
        assertEquals(1, published.size());
    }

    private CustomerOrder seedDraft(String number) {
        return orders.save(
                CustomerOrder.create(
                        OrderId.generate(),
                        OrderNumber.of(number),
                        OrderCommercialData.of(
                                "C-1",
                                "Customer",
                                null,
                                null,
                                null,
                                OrderDirection.PRIVATE,
                                CurrencyCode.of("USD")),
                        CLOCK));
    }

    private static DocumentOperationContext context(DocumentId documentId) {
        DocumentMetadata metadata =
                new DocumentMetadata(
                        documentId.value(),
                        DocumentTypeCode.ORDER_CANCEL.name(),
                        "DOC-" + documentId,
                        "title",
                        DocumentStatus.DRAFT,
                        0L,
                        NOW,
                        NOW,
                        null,
                        null);
        return () -> metadata;
    }
}
