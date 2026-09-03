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
import com.tmp.order.api.event.OrderItemUpdated;
import com.tmp.order.application.item.UpdateOrderItemUseCase;
import com.tmp.order.application.order.InMemoryCustomerOrderRepository;
import com.tmp.order.application.order.InMemoryOrderItemRepository;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.InMemoryOrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderItemUpdatePayload;
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
import com.tmp.order.testsupport.ParentOrderFixtures;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderItemUpdateDocumentProcessorTest {

    private static final Instant NOW = Instant.parse("2026-07-26T17:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OrderDocumentPayloadPort payloads;
    private ProcessingRecordPort processing;
    private InMemoryOrderItemRepository items;
    private InMemoryCustomerOrderRepository orders;
    private List<DomainEvent> published;
    private OrderItemUpdateDocumentProcessor processor;

    @BeforeEach
    void setUp() {
        payloads = new InMemoryOrderDocumentPayloadPort();
        processing = new InMemoryProcessingRecordPort();
        items = new InMemoryOrderItemRepository();
        orders = new InMemoryCustomerOrderRepository();
        published = new ArrayList<>();
        processor =
                new OrderItemUpdateDocumentProcessor(
                        payloads,
                        processing,
                        (TransactionalEventPublisher) published::add,
                        new UpdateOrderItemUseCase(orders, items, CLOCK),
                        CLOCK);
    }

    @Test
    void draftItemCommercialFieldsAreUpdated() {
        OrderItem draft = seedDraftItem();
        DocumentId documentId = DocumentId.generate();
        ItemCommercialData updated =
                ItemCommercialData.of(ProductCode.of("P-2"), "Door B", "note");
        payloads.create(
                OrderItemUpdatePayload.create(documentId, draft.id(), updated, NOW));

        processor.onPost(context(documentId));

        OrderItem saved = items.findById(draft.id()).orElseThrow();
        assertEquals("Door B", saved.commercialData().name());
        assertEquals(OrderItemStatus.DRAFT, saved.status());
        assertEquals(draft.draftRevisionNumber(), saved.draftRevisionNumber());
        assertEquals(1, published.size());
        assertEquals(draft.id(), assertInstanceOf(OrderItemUpdated.class, published.get(0)).orderItemId());
    }

    @Test
    void activeItemCannotBeUpdated() {
        OrderItem active = seedActiveItem();
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemUpdatePayload.create(documentId, active.id(), commercial("X"), NOW));

        assertThrows(InvalidOrderStateException.class, () -> processor.onPost(context(documentId)));
        assertTrue(published.isEmpty());
        assertTrue(!processing.exists(documentId, ProcessingOperation.POST));
    }

    @Test
    void cancelledItemCannotBeUpdated() {
        OrderItem cancelled = seedDraftItem().cancel(CLOCK);
        items.save(cancelled);
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemUpdatePayload.create(
                        documentId, cancelled.id(), commercial("Y"), NOW));

        assertThrows(InvalidOrderStateException.class, () -> processor.onPost(context(documentId)));
        assertTrue(published.isEmpty());
    }

    @Test
    void revisionAndSpecificationRemainUnchanged() {
        OrderItem draft = seedDraftItem();
        ItemSpecification spec =
                ItemSpecification.of(
                        draft.id(),
                        RevisionNumber.first(),
                        List.of(
                                IntakeContractFixtures.specLine("MAT-1", "Glass", BigDecimal.ONE, "m2")));
        draft = items.save(draft.updateDraftSpecification(spec, CLOCK));
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemUpdatePayload.create(
                        documentId, draft.id(), commercial("Updated"), NOW));

        processor.onPost(context(documentId));

        OrderItem saved = items.findById(draft.id()).orElseThrow();
        assertEquals(1, saved.draftRevision().orElseThrow().specification().orElseThrow().lines().size());
        assertEquals(RevisionNumber.first(), saved.draftRevisionNumber().orElseThrow());
    }

    @Test
    void repeatedOnPostIsIdempotent() {
        OrderItem draft = seedDraftItem();
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemUpdatePayload.create(
                        documentId, draft.id(), commercial("Idem"), NOW));
        DocumentOperationContext ctx = context(documentId);

        processor.onPost(ctx);
        long version = items.findById(draft.id()).orElseThrow().version();
        processor.onPost(ctx);

        assertEquals(version, items.findById(draft.id()).orElseThrow().version());
        assertEquals(1, published.size());
    }

    private OrderItem seedDraftItem() {
        OrderId orderId = OrderId.generate();
        ParentOrderFixtures.saveDraft(orders, orderId, CLOCK);
        return items.save(
                OrderItem.create(
                        OrderItemId.generate(),
                        orderId,
                        commercial("Door A"),
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

    private static ItemCommercialData commercial(String name) {
        return ItemCommercialData.of(ProductCode.of("P-1"), name, null);
    }

    private static DocumentOperationContext context(DocumentId documentId) {
        DocumentMetadata metadata =
                new DocumentMetadata(
                        documentId.value(),
                        DocumentTypeCode.ORDER_ITEM_UPDATE.name(),
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
