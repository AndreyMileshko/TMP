package com.tmp.order.application.document;

import com.tmp.order.testsupport.IntakeContractFixtures;

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
import com.tmp.order.api.event.OrderItemCancelled;
import com.tmp.order.application.item.CancelOrderItemUseCase;
import com.tmp.order.application.order.InMemoryOrderItemRepository;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.InMemoryOrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderItemCancelPayload;
import com.tmp.order.application.processing.InMemoryProcessingRecordPort;
import com.tmp.order.application.processing.ProcessingOperation;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.domain.InvalidOrderStateException;
import com.tmp.order.domain.ItemCommercialData;
import com.tmp.order.domain.ItemSpecification;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.OrderedQuantity;
import com.tmp.order.domain.ProductCode;
import com.tmp.order.domain.SpecificationLine;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderItemCancelDocumentProcessorTest {

    private static final Instant NOW = Instant.parse("2026-07-26T21:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OrderDocumentPayloadPort payloads;
    private ProcessingRecordPort processing;
    private InMemoryOrderItemRepository items;
    private List<DomainEvent> published;
    private OrderItemCancelDocumentProcessor processor;

    @BeforeEach
    void setUp() {
        payloads = new InMemoryOrderDocumentPayloadPort();
        processing = new InMemoryProcessingRecordPort();
        items = new InMemoryOrderItemRepository();
        published = new ArrayList<>();
        processor =
                new OrderItemCancelDocumentProcessor(
                        payloads,
                        processing,
                        (TransactionalEventPublisher) published::add,
                        new CancelOrderItemUseCase(items, CLOCK),
                        CLOCK);
    }

    @Test
    void draftItemBecomesCancelled() {
        OrderItem draft = seedDraftItem();
        DocumentId documentId = DocumentId.generate();
        payloads.create(OrderItemCancelPayload.create(documentId, draft.id(), NOW));

        processor.onPost(context(documentId));

        OrderItem saved = items.findById(draft.id()).orElseThrow();
        assertEquals(OrderItemStatus.CANCELLED, saved.status());
        assertTrue(saved.draftRevisionNumber().isPresent());
        assertEquals(1, published.size());
        OrderItemCancelled event = assertInstanceOf(OrderItemCancelled.class, published.get(0));
        assertEquals(draft.id(), event.orderItemId());
        assertEquals(draft.orderId(), event.orderId());
    }

    @Test
    void activeItemCannotBeCancelled() {
        OrderItem active = seedActiveItem();
        DocumentId documentId = DocumentId.generate();
        payloads.create(OrderItemCancelPayload.create(documentId, active.id(), NOW));

        assertThrows(InvalidOrderStateException.class, () -> processor.onPost(context(documentId)));
        assertEquals(OrderItemStatus.ACTIVE, items.findById(active.id()).orElseThrow().status());
        assertTrue(published.isEmpty());
        assertTrue(!processing.exists(documentId, ProcessingOperation.POST));
    }

    @Test
    void repeatedCancelIsRejected() {
        OrderItem cancelled = seedDraftItem().cancel(CLOCK);
        items.save(cancelled);
        DocumentId documentId = DocumentId.generate();
        payloads.create(OrderItemCancelPayload.create(documentId, cancelled.id(), NOW));

        assertThrows(InvalidOrderStateException.class, () -> processor.onPost(context(documentId)));
        assertTrue(published.isEmpty());
    }

    @Test
    void repeatedOnPostIsIdempotent() {
        OrderItem draft = seedDraftItem();
        DocumentId documentId = DocumentId.generate();
        payloads.create(OrderItemCancelPayload.create(documentId, draft.id(), NOW));
        DocumentOperationContext ctx = context(documentId);

        processor.onPost(ctx);
        processor.onPost(ctx);

        assertEquals(OrderItemStatus.CANCELLED, items.findById(draft.id()).orElseThrow().status());
        assertEquals(1, published.size());
    }

    private OrderItem seedDraftItem() {
        return items.save(
                OrderItem.create(
                        OrderItemId.generate(),
                        OrderId.generate(),
                        ItemCommercialData.of(ProductCode.of("P-1"), "Door", null),
                        OrderedQuantity.of(1),
                        CLOCK));
    }

    private OrderItem seedActiveItem() {
        OrderItem draft = seedDraftItem();
        ItemSpecification spec =
                ItemSpecification.of(
                        draft.id(),
                        RevisionNumber.first(),
                        List.of(
                                IntakeContractFixtures.specLine("MAT-1", "Glass", BigDecimal.ONE, "m2")));
        return items.save(draft.updateDraftSpecification(spec, CLOCK).approveDraftRevision(CLOCK));
    }

    private static DocumentOperationContext context(DocumentId documentId) {
        DocumentMetadata metadata =
                new DocumentMetadata(
                        documentId.value(),
                        DocumentTypeCode.ORDER_ITEM_CANCEL.name(),
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
