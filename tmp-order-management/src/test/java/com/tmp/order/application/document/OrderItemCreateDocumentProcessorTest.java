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
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.api.event.OrderItemCreated;
import com.tmp.order.api.event.OrderItemRevisionCreated;
import com.tmp.order.application.item.CreateOrderItemUseCase;
import com.tmp.order.application.order.InMemoryCustomerOrderRepository;
import com.tmp.order.application.order.InMemoryOrderItemRepository;
import com.tmp.order.application.order.OrderNotFoundException;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.InMemoryOrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderItemCreatePayload;
import com.tmp.order.application.processing.InMemoryProcessingRecordPort;
import com.tmp.order.application.processing.ProcessingOperation;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.domain.CurrencyCode;
import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.InvalidOrderStateException;
import com.tmp.order.domain.ItemCommercialData;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderDirection;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.OrderedQuantity;
import com.tmp.order.domain.ProductCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderItemCreateDocumentProcessorTest {

    private static final Instant NOW = Instant.parse("2026-07-26T16:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OrderDocumentPayloadPort payloads;
    private ProcessingRecordPort processing;
    private InMemoryCustomerOrderRepository orders;
    private InMemoryOrderItemRepository items;
    private List<DomainEvent> published;
    private OrderItemCreateDocumentProcessor processor;

    @BeforeEach
    void setUp() {
        payloads = new InMemoryOrderDocumentPayloadPort();
        processing = new InMemoryProcessingRecordPort();
        orders = new InMemoryCustomerOrderRepository();
        items = new InMemoryOrderItemRepository();
        published = new ArrayList<>();
        TransactionalEventPublisher publisher = published::add;
        processor =
                new OrderItemCreateDocumentProcessor(
                        payloads,
                        processing,
                        publisher,
                        new CreateOrderItemUseCase(orders, items, CLOCK),
                        CLOCK);
    }

    @Test
    void createsDraftItemWithRevisionOneAndPublishesTwoEvents() {
        CustomerOrder parent = seedDraftOrder("ORD-ITEM-1");
        OrderItemId itemId = OrderItemId.generate();
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemCreatePayload.create(
                        documentId,
                        parent.id(),
                        itemId,
                        commercial(),
                        OrderedQuantity.of(2),
                        NOW));

        processor.onPost(context(documentId));

        OrderItem item = items.findById(itemId).orElseThrow();
        assertEquals(OrderItemStatus.DRAFT, item.status());
        assertEquals(RevisionNumber.first(), item.draftRevisionNumber().orElseThrow());
        assertTrue(item.activeRevisionNumber().isEmpty());
        assertEquals(RevisionStatus.DRAFT, item.draftRevision().orElseThrow().status());
        assertEquals(2, published.size());
        assertInstanceOf(OrderItemCreated.class, published.get(0));
        assertInstanceOf(OrderItemRevisionCreated.class, published.get(1));
        assertEquals(itemId, ((OrderItemCreated) published.get(0)).orderItemId());
        assertEquals(
                RevisionNumber.first(),
                ((OrderItemRevisionCreated) published.get(1)).revisionNumber());
        assertTrue(processing.exists(documentId, ProcessingOperation.POST));
    }

    @Test
    void missingParentOrderIsRejected() {
        OrderId missing = OrderId.generate();
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemCreatePayload.create(
                        documentId,
                        missing,
                        OrderItemId.generate(),
                        commercial(),
                        OrderedQuantity.of(1),
                        NOW));

        assertThrows(OrderNotFoundException.class, () -> processor.onPost(context(documentId)));
        assertTrue(published.isEmpty());
        assertTrue(!processing.exists(documentId, ProcessingOperation.POST));
    }

    @Test
    void approvedParentOrderRejectsItemCreate() {
        CustomerOrder approved = seedDraftOrder("ORD-ITEM-APPR").approve(CLOCK);
        orders.save(approved);
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemCreatePayload.create(
                        documentId,
                        approved.id(),
                        OrderItemId.generate(),
                        commercial(),
                        OrderedQuantity.of(1),
                        NOW));

        assertThrows(InvalidOrderStateException.class, () -> processor.onPost(context(documentId)));
        assertTrue(published.isEmpty());
        assertTrue(!processing.exists(documentId, ProcessingOperation.POST));
    }

    @Test
    void cancelledParentOrderRejectsItemCreate() {
        CustomerOrder cancelled = seedDraftOrder("ORD-ITEM-CAN").cancel(CLOCK);
        orders.save(cancelled);
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemCreatePayload.create(
                        documentId,
                        cancelled.id(),
                        OrderItemId.generate(),
                        commercial(),
                        OrderedQuantity.of(1),
                        NOW));

        assertThrows(InvalidOrderStateException.class, () -> processor.onPost(context(documentId)));
        assertTrue(published.isEmpty());
    }

    @Test
    void repeatedOnPostDoesNotDuplicateItemOrEvents() {
        CustomerOrder parent = seedDraftOrder("ORD-ITEM-IDEM");
        OrderItemId itemId = OrderItemId.generate();
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemCreatePayload.create(
                        documentId,
                        parent.id(),
                        itemId,
                        commercial(),
                        OrderedQuantity.of(1),
                        NOW));
        DocumentOperationContext ctx = context(documentId);

        processor.onPost(ctx);
        processor.onPost(ctx);

        assertEquals(1, items.findByOrderId(parent.id()).size());
        assertEquals(2, published.size());
    }

    private CustomerOrder seedDraftOrder(String number) {
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

    private static ItemCommercialData commercial() {
        return ItemCommercialData.of(ProductCode.of("P-1"), "Door", null);
    }

    private static DocumentOperationContext context(DocumentId documentId) {
        DocumentMetadata metadata =
                new DocumentMetadata(
                        documentId.value(),
                        DocumentTypeCode.ORDER_ITEM_CREATE.name(),
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
