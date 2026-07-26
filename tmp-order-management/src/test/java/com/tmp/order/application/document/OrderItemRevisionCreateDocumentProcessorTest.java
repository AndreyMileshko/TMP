package com.tmp.order.application.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.tmp.order.api.event.OrderItemRevisionCreated;
import com.tmp.order.application.item.CreateOrderItemRevisionUseCase;
import com.tmp.order.application.order.InMemoryOrderItemRepository;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.InMemoryOrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderItemRevisionCreatePayload;
import com.tmp.order.application.processing.InMemoryProcessingRecordPort;
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

class OrderItemRevisionCreateDocumentProcessorTest {

    private static final Instant NOW = Instant.parse("2026-07-26T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OrderDocumentPayloadPort payloads;
    private ProcessingRecordPort processing;
    private InMemoryOrderItemRepository items;
    private List<DomainEvent> published;
    private OrderItemRevisionCreateDocumentProcessor processor;

    @BeforeEach
    void setUp() {
        payloads = new InMemoryOrderDocumentPayloadPort();
        processing = new InMemoryProcessingRecordPort();
        items = new InMemoryOrderItemRepository();
        published = new ArrayList<>();
        processor =
                new OrderItemRevisionCreateDocumentProcessor(
                        payloads,
                        processing,
                        (TransactionalEventPublisher) published::add,
                        new CreateOrderItemRevisionUseCase(items, CLOCK),
                        CLOCK);
    }

    @Test
    void createsRevisionNPlusOne() {
        OrderItem active = seedActiveItem();
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemRevisionCreatePayload.create(
                        documentId, active.id(), RevisionNumber.of(2), null, NOW));

        processor.onPost(context(documentId));

        OrderItem saved = items.findById(active.id()).orElseThrow();
        assertEquals(OrderItemStatus.ACTIVE, saved.status());
        assertEquals(RevisionNumber.first(), saved.activeRevisionNumber().orElseThrow());
        assertEquals(RevisionNumber.of(2), saved.draftRevisionNumber().orElseThrow());
        assertEquals(RevisionStatus.DRAFT, saved.draftRevision().orElseThrow().status());
        assertEquals(1, published.size());
        assertEquals(
                RevisionNumber.of(2),
                ((OrderItemRevisionCreated) published.get(0)).revisionNumber());
    }

    @Test
    void secondDraftRevisionIsRejected() {
        OrderItem withDraft =
                seedActiveItem().createNextDraftRevision(OrderedQuantity.of(3), CLOCK);
        items.save(withDraft);
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemRevisionCreatePayload.create(
                        documentId, withDraft.id(), RevisionNumber.of(3), null, NOW));

        assertThrows(InvalidOrderStateException.class, () -> processor.onPost(context(documentId)));
        assertTrue(published.isEmpty());
    }

    @Test
    void wrongRevisionNumberIsRejected() {
        OrderItem active = seedActiveItem();
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemRevisionCreatePayload.create(
                        documentId, active.id(), RevisionNumber.of(5), null, NOW));

        assertThrows(InvalidOrderStateException.class, () -> processor.onPost(context(documentId)));
        assertTrue(published.isEmpty());
    }

    @Test
    void activeRevisionUnchangedAfterCreate() {
        OrderItem active = seedActiveItem();
        RevisionNumber activeBefore = active.activeRevisionNumber().orElseThrow();
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemRevisionCreatePayload.create(
                        documentId, active.id(), RevisionNumber.of(2), null, NOW));

        processor.onPost(context(documentId));

        OrderItem saved = items.findById(active.id()).orElseThrow();
        assertEquals(activeBefore, saved.activeRevisionNumber().orElseThrow());
        assertTrue(saved.revision(activeBefore).orElseThrow().isApproved());
    }

    @Test
    void copyDoesNotMutateSourceRevision() {
        OrderItem active = seedActiveItem();
        int sourceLines =
                active.activeRevision().orElseThrow().specification().orElseThrow().lines().size();
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemRevisionCreatePayload.create(
                        documentId,
                        active.id(),
                        RevisionNumber.of(2),
                        RevisionNumber.first(),
                        NOW));

        processor.onPost(context(documentId));

        OrderItem saved = items.findById(active.id()).orElseThrow();
        assertEquals(
                sourceLines,
                saved.revision(RevisionNumber.first())
                        .orElseThrow()
                        .specification()
                        .orElseThrow()
                        .lines()
                        .size());
        assertEquals(
                sourceLines,
                saved.draftRevision().orElseThrow().specification().orElseThrow().lines().size());
        assertTrue(saved.revision(RevisionNumber.first()).orElseThrow().specification().orElseThrow().isImmutable());
        assertTrue(!saved.draftRevision().orElseThrow().specification().orElseThrow().isImmutable());
    }

    private OrderItem seedActiveItem() {
        OrderItem draft =
                OrderItem.create(
                        OrderItemId.generate(),
                        OrderId.generate(),
                        ItemCommercialData.of(ProductCode.of("P-1"), "Door", null),
                        OrderedQuantity.of(1),
                        CLOCK);
        ItemSpecification spec =
                ItemSpecification.of(
                        draft.id(),
                        RevisionNumber.first(),
                        List.of(
                                SpecificationLine.of(
                                        "MAT-1",
                                        "Glass",
                                        BigDecimal.ONE,
                                        "m2",
                                        BigDecimal.ONE)));
        return items.save(draft.updateDraftSpecification(spec, CLOCK).approveDraftRevision(CLOCK));
    }

    private static DocumentOperationContext context(DocumentId documentId) {
        DocumentMetadata metadata =
                new DocumentMetadata(
                        documentId.value(),
                        DocumentTypeCode.ORDER_ITEM_REVISION_CREATE.name(),
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
