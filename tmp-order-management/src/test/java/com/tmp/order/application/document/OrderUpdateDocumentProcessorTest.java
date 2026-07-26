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
import com.tmp.order.api.event.OrderUpdated;
import com.tmp.order.application.order.InMemoryCustomerOrderRepository;
import com.tmp.order.application.order.OrderNotFoundException;
import com.tmp.order.application.order.UpdateOrderUseCase;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.InMemoryOrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderUpdatePayload;
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

class OrderUpdateDocumentProcessorTest {

    private static final Instant NOW = Instant.parse("2026-07-26T13:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OrderDocumentPayloadPort payloads;
    private ProcessingRecordPort processing;
    private InMemoryCustomerOrderRepository orders;
    private List<DomainEvent> published;
    private OrderUpdateDocumentProcessor processor;

    @BeforeEach
    void setUp() {
        payloads = new InMemoryOrderDocumentPayloadPort();
        processing = new InMemoryProcessingRecordPort();
        orders = new InMemoryCustomerOrderRepository();
        published = new ArrayList<>();
        TransactionalEventPublisher publisher = published::add;
        processor =
                new OrderUpdateDocumentProcessor(
                        payloads,
                        processing,
                        publisher,
                        new UpdateOrderUseCase(orders, CLOCK),
                        CLOCK);
    }

    @Test
    void draftOrderCommercialFieldsAreUpdated() {
        CustomerOrder draft = seedDraft("ORD-UPD-1");
        DocumentId documentId = DocumentId.generate();
        OrderCommercialData updated =
                OrderCommercialData.of(
                        "C-2",
                        "Updated Customer",
                        "CTR-2",
                        "SITE-2",
                        "Mgr",
                        OrderDirection.DEALER,
                        CurrencyCode.of("EUR"));
        payloads.create(OrderUpdatePayload.create(documentId, draft.id(), updated, NOW));

        processor.onPost(context(documentId));

        CustomerOrder saved = orders.findById(draft.id()).orElseThrow();
        assertEquals(OrderStatus.DRAFT, saved.status());
        assertEquals(draft.orderNumber(), saved.orderNumber());
        assertEquals("Updated Customer", saved.commercialData().customerName());
        assertEquals(OrderDirection.DEALER, saved.commercialData().direction());
        assertEquals(1L, saved.version());
        assertEquals(1, published.size());
        assertEquals(draft.id(), assertInstanceOf(OrderUpdated.class, published.get(0)).orderId());
    }

    @Test
    void approvedOrderCannotBeUpdated() {
        CustomerOrder approved = seedDraft("ORD-UPD-APPR").approve(CLOCK);
        orders.save(approved);
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderUpdatePayload.create(
                        documentId, approved.id(), commercial("X"), NOW));

        assertThrows(
                InvalidOrderStateException.class, () -> processor.onPost(context(documentId)));
        assertTrue(published.isEmpty());
        assertTrue(!processing.exists(documentId, ProcessingOperation.POST));
        assertEquals(OrderStatus.APPROVED, orders.findById(approved.id()).orElseThrow().status());
    }

    @Test
    void cancelledOrderCannotBeUpdated() {
        CustomerOrder cancelled = seedDraft("ORD-UPD-CAN").cancel(CLOCK);
        orders.save(cancelled);
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderUpdatePayload.create(
                        documentId, cancelled.id(), commercial("Y"), NOW));

        assertThrows(
                InvalidOrderStateException.class, () -> processor.onPost(context(documentId)));
        assertTrue(published.isEmpty());
        assertTrue(!processing.exists(documentId, ProcessingOperation.POST));
    }

    @Test
    void missingOrderIsRejected() {
        OrderId missing = OrderId.generate();
        DocumentId documentId = DocumentId.generate();
        payloads.create(OrderUpdatePayload.create(documentId, missing, commercial("Z"), NOW));

        OrderNotFoundException ex =
                assertThrows(
                        OrderNotFoundException.class, () -> processor.onPost(context(documentId)));
        assertEquals(missing, ex.orderId());
        assertTrue(published.isEmpty());
        assertTrue(!processing.exists(documentId, ProcessingOperation.POST));
    }

    @Test
    void repeatedOnPostIsIdempotent() {
        CustomerOrder draft = seedDraft("ORD-UPD-IDEM");
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderUpdatePayload.create(
                        documentId, draft.id(), commercial("Idem"), NOW));
        DocumentOperationContext ctx = context(documentId);

        processor.onPost(ctx);
        long versionAfterFirst = orders.findById(draft.id()).orElseThrow().version();
        processor.onPost(ctx);

        assertEquals(versionAfterFirst, orders.findById(draft.id()).orElseThrow().version());
        assertEquals(1, published.size());
    }

    private CustomerOrder seedDraft(String number) {
        return orders.save(
                CustomerOrder.create(
                        OrderId.generate(), OrderNumber.of(number), commercial("C-1"), CLOCK));
    }

    private static DocumentOperationContext context(DocumentId documentId) {
        DocumentMetadata metadata =
                new DocumentMetadata(
                        documentId.value(),
                        DocumentTypeCode.ORDER_UPDATE.name(),
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

    private static OrderCommercialData commercial(String customerName) {
        return OrderCommercialData.of(
                "C-1",
                customerName,
                null,
                null,
                null,
                OrderDirection.PRIVATE,
                CurrencyCode.of("USD"));
    }
}
