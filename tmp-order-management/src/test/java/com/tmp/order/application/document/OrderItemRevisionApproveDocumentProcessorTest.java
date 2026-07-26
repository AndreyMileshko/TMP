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
import com.tmp.order.api.event.OrderItemRevisionApproved;
import com.tmp.order.application.item.ApproveOrderItemRevisionUseCase;
import com.tmp.order.application.order.InMemoryOrderItemRepository;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.InMemoryOrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderItemRevisionApprovePayload;
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

class OrderItemRevisionApproveDocumentProcessorTest {

    private static final Instant NOW = Instant.parse("2026-07-26T20:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OrderDocumentPayloadPort payloads;
    private ProcessingRecordPort processing;
    private InMemoryOrderItemRepository items;
    private List<DomainEvent> published;
    private OrderItemRevisionApproveDocumentProcessor processor;

    @BeforeEach
    void setUp() {
        payloads = new InMemoryOrderDocumentPayloadPort();
        processing = new InMemoryProcessingRecordPort();
        items = new InMemoryOrderItemRepository();
        published = new ArrayList<>();
        processor =
                new OrderItemRevisionApproveDocumentProcessor(
                        payloads,
                        processing,
                        (TransactionalEventPublisher) published::add,
                        new ApproveOrderItemRevisionUseCase(items, CLOCK),
                        CLOCK);
    }

    @Test
    void firstApprovalActivatesItem() {
        OrderItem draft = seedDraftWithSpec();
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemRevisionApprovePayload.create(
                        documentId, draft.id(), RevisionNumber.first(), NOW));

        processor.onPost(context(documentId));

        OrderItem saved = items.findById(draft.id()).orElseThrow();
        assertEquals(OrderItemStatus.ACTIVE, saved.status());
        assertEquals(RevisionNumber.first(), saved.activeRevisionNumber().orElseThrow());
        assertTrue(saved.draftRevisionNumber().isEmpty());
        OrderItemRevisionApproved event =
                assertInstanceOf(OrderItemRevisionApproved.class, published.get(0));
        assertEquals(draft.orderId(), event.orderId());
        assertEquals(draft.id(), event.orderItemId());
    }

    @Test
    void newRevisionReplacesActivePointer() {
        OrderItem active = seedActiveItem();
        OrderItem withDraft =
                items.save(
                        active
                                .createNextDraftRevision(OrderedQuantity.of(2), CLOCK)
                                .updateDraftSpecification(
                                        ItemSpecification.of(
                                                active.id(),
                                                RevisionNumber.of(2),
                                                List.of(sampleLine())),
                                        CLOCK));
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemRevisionApprovePayload.create(
                        documentId, withDraft.id(), RevisionNumber.of(2), NOW));

        processor.onPost(context(documentId));

        OrderItem saved = items.findById(withDraft.id()).orElseThrow();
        assertEquals(RevisionNumber.of(2), saved.activeRevisionNumber().orElseThrow());
        assertTrue(saved.revision(RevisionNumber.first()).orElseThrow().isApproved());
        assertTrue(saved.revision(RevisionNumber.first()).orElseThrow().specification().orElseThrow().isImmutable());
    }

    @Test
    void emptySpecificationIsRejectedWithoutProcessingOrEvent() {
        OrderItem draft =
                items.save(
                        OrderItem.create(
                                OrderItemId.generate(),
                                OrderId.generate(),
                                ItemCommercialData.of(ProductCode.of("P-1"), "Door", null),
                                OrderedQuantity.of(1),
                                CLOCK));
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemRevisionApprovePayload.create(
                        documentId, draft.id(), RevisionNumber.first(), NOW));

        assertThrows(InvalidOrderStateException.class, () -> processor.onPost(context(documentId)));
        assertTrue(published.isEmpty());
        assertTrue(!processing.exists(documentId, ProcessingOperation.POST));
        assertEquals(OrderItemStatus.DRAFT, items.findById(draft.id()).orElseThrow().status());
    }

    @Test
    void reApprovalWithoutDraftIsRejectedWithoutProcessingOrEvent() {
        OrderItem active = seedActiveItem();
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemRevisionApprovePayload.create(
                        documentId, active.id(), RevisionNumber.first(), NOW));

        assertThrows(InvalidOrderStateException.class, () -> processor.onPost(context(documentId)));
        assertTrue(published.isEmpty());
        assertTrue(!processing.exists(documentId, ProcessingOperation.POST));
    }

    private OrderItem seedDraftWithSpec() {
        OrderItem draft =
                OrderItem.create(
                        OrderItemId.generate(),
                        OrderId.generate(),
                        ItemCommercialData.of(ProductCode.of("P-1"), "Door", null),
                        OrderedQuantity.of(1),
                        CLOCK);
        return items.save(
                draft.updateDraftSpecification(
                        ItemSpecification.of(
                                draft.id(), RevisionNumber.first(), List.of(sampleLine())),
                        CLOCK));
    }

    private OrderItem seedActiveItem() {
        return items.save(seedDraftWithSpec().approveDraftRevision(CLOCK));
    }

    private static SpecificationLine sampleLine() {
        return SpecificationLine.of(
                "MAT-1", "Glass", BigDecimal.ONE, "m2", BigDecimal.ONE);
    }

    private static DocumentOperationContext context(DocumentId documentId) {
        DocumentMetadata metadata =
                new DocumentMetadata(
                        documentId.value(),
                        DocumentTypeCode.ORDER_ITEM_REVISION_APPROVE.name(),
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
