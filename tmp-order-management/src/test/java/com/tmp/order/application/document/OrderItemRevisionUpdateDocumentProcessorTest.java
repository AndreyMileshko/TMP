package com.tmp.order.application.document;

import com.tmp.order.testsupport.IntakeContractFixtures;

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
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.application.item.UpdateOrderItemRevisionUseCase;
import com.tmp.order.application.order.InMemoryOrderItemRepository;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.InMemoryOrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderItemRevisionPayloadLine;
import com.tmp.order.application.payload.OrderItemRevisionUpdatePayload;
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

class OrderItemRevisionUpdateDocumentProcessorTest {

    private static final Instant NOW = Instant.parse("2026-07-26T19:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OrderDocumentPayloadPort payloads;
    private ProcessingRecordPort processing;
    private InMemoryOrderItemRepository items;
    private List<DomainEvent> published;
    private OrderItemRevisionUpdateDocumentProcessor processor;

    @BeforeEach
    void setUp() {
        payloads = new InMemoryOrderDocumentPayloadPort();
        processing = new InMemoryProcessingRecordPort();
        items = new InMemoryOrderItemRepository();
        published = new ArrayList<>();
        processor =
                new OrderItemRevisionUpdateDocumentProcessor(
                        payloads,
                        processing,
                        (TransactionalEventPublisher) published::add,
                        new UpdateOrderItemRevisionUseCase(items, CLOCK),
                        CLOCK);
    }

    @Test
    void draftRevisionIsUpdated() {
        OrderItem withDraft = seedActiveWithDraft();
        DocumentId documentId = DocumentId.generate();
        List<OrderItemRevisionPayloadLine> lines =
                List.of(
                        IntakeContractFixtures.payloadLine(1, "MAT-2", "Alu", BigDecimal.TEN, "kg"));
        payloads.create(
                OrderItemRevisionUpdatePayload.create(
                        documentId,
                        withDraft.id(),
                        RevisionNumber.of(2),
                        OrderedQuantity.of(9),
                        lines,
                        NOW));

        processor.onPost(context(documentId));

        OrderItem saved = items.findById(withDraft.id()).orElseThrow();
        assertEquals(9, saved.draftRevision().orElseThrow().orderedQuantity().value().intValue());
        assertEquals(
                "MAT-2",
                saved.draftRevision()
                        .orElseThrow()
                        .specification()
                        .orElseThrow()
                        .lines()
                        .get(0)
                        .materialCode());
        assertEquals("Door", saved.commercialData().name());
        assertEquals(1, published.size());
    }

    @Test
    void missingDraftRevisionIsRejected() {
        OrderItem active = seedActiveOnly();
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemRevisionUpdatePayload.create(
                        documentId,
                        active.id(),
                        RevisionNumber.of(2),
                        OrderedQuantity.of(1),
                        List.of(),
                        NOW));

        assertThrows(InvalidOrderStateException.class, () -> processor.onPost(context(documentId)));
        assertTrue(published.isEmpty());
    }

    @Test
    void mismatchedRevisionNumberIsRejected() {
        OrderItem withDraft = seedActiveWithDraft();
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemRevisionUpdatePayload.create(
                        documentId,
                        withDraft.id(),
                        RevisionNumber.first(),
                        OrderedQuantity.of(1),
                        List.of(),
                        NOW));

        assertThrows(InvalidOrderStateException.class, () -> processor.onPost(context(documentId)));
        assertTrue(published.isEmpty());
    }

    @Test
    void approvedRevisionIsNotChanged() {
        OrderItem withDraft = seedActiveWithDraft();
        RevisionNumber active = withDraft.activeRevisionNumber().orElseThrow();
        int activeLines =
                withDraft.revision(active).orElseThrow().specification().orElseThrow().lines().size();
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemRevisionUpdatePayload.create(
                        documentId,
                        withDraft.id(),
                        RevisionNumber.of(2),
                        OrderedQuantity.of(4),
                        List.of(
                                IntakeContractFixtures.payloadLine(1, "MAT-9", "X", BigDecimal.ONE, "m")),
                        NOW));

        processor.onPost(context(documentId));

        OrderItem saved = items.findById(withDraft.id()).orElseThrow();
        assertEquals(
                activeLines,
                saved.revision(active).orElseThrow().specification().orElseThrow().lines().size());
        assertTrue(saved.revision(active).orElseThrow().isActive());
    }

    @Test
    void specificationLinesRemainImmutableOnPayload() {
        OrderItem withDraft = seedActiveWithDraft();
        DocumentId documentId = DocumentId.generate();
        List<OrderItemRevisionPayloadLine> lines =
                List.of(
                        IntakeContractFixtures.payloadLine(1, "MAT-3", "Wood", BigDecimal.ONE, "m3"));
        OrderItemRevisionUpdatePayload payload =
                OrderItemRevisionUpdatePayload.create(
                        documentId,
                        withDraft.id(),
                        RevisionNumber.of(2),
                        OrderedQuantity.of(2),
                        lines,
                        NOW);
        payloads.create(payload);

        processor.onPost(context(documentId));

        assertThrows(UnsupportedOperationException.class, () -> payload.lines().add(null));
    }

    @Test
    void repeatedOnPostIsIdempotent() {
        OrderItem withDraft = seedActiveWithDraft();
        DocumentId documentId = DocumentId.generate();
        payloads.create(
                OrderItemRevisionUpdatePayload.create(
                        documentId,
                        withDraft.id(),
                        RevisionNumber.of(2),
                        OrderedQuantity.of(3),
                        List.of(),
                        NOW));
        DocumentOperationContext ctx = context(documentId);

        processor.onPost(ctx);
        long version = items.findById(withDraft.id()).orElseThrow().version();
        processor.onPost(ctx);

        assertEquals(version, items.findById(withDraft.id()).orElseThrow().version());
        assertEquals(1, published.size());
    }

    private OrderItem seedActiveOnly() {
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
                                IntakeContractFixtures.specLine("MAT-1", "Glass", BigDecimal.ONE, "m2")));
        return items.save(draft.updateDraftSpecification(spec, CLOCK).approveDraftRevision(CLOCK));
    }

    private OrderItem seedActiveWithDraft() {
        return items.save(
                seedActiveOnly().createNextDraftRevision(OrderedQuantity.of(2), CLOCK));
    }

    private static DocumentOperationContext context(DocumentId documentId) {
        DocumentMetadata metadata =
                new DocumentMetadata(
                        documentId.value(),
                        DocumentTypeCode.ORDER_ITEM_REVISION_UPDATE.name(),
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
