package com.tmp.production.application.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentStatus;
import com.tmp.production.domain.CancellationItemAction;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionCancellation;
import com.tmp.production.domain.ProductionCancellationValidationException;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.ProductionCancellationRepository;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductionCancellationProcessorTest {

    private static final Instant T0 = Instant.parse("2026-08-21T12:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-21T13:00:00Z");

    private InMemoryItemRepository items;
    private InMemoryCancellationRepository cancellations;
    private ProductionCancellationProcessor processor;

    @BeforeEach
    void setUp() {
        items = new InMemoryItemRepository();
        cancellations = new InMemoryCancellationRepository();
        processor = new ProductionCancellationProcessor(cancellations, items);
    }

    @Test
    void documentTypeId() {
        assertEquals("production.cancellation", processor.documentTypeId());
    }

    @Test
    void wholeOrderCancellationCancelsUnfinishedPreservesReleased() {
        SourceOrderId orderId = SourceOrderId.generate();
        Fixture a = launchSameOrder(orderId, 10);
        Fixture b = launchSameOrder(orderId, 8);
        items.save(
                items.require(b.orderId, b.itemId, b.specId)
                        .release(ProductionQuantity.positive(2), T0));
        Fixture c = launchSameOrder(orderId, 5);
        items.save(
                items.require(c.orderId, c.itemId, c.specId)
                        .release(ProductionQuantity.positive(5), T0));

        UUID docId =
                prepareWholeOrderCancellation(
                        orderId,
                        T1,
                        List.of(
                                cancellationLine(a, ProductionStatus.IN_PRODUCTION),
                                cancellationLine(
                                        b,
                                        ProductionStatus.PARTIALLY_RELEASED,
                                        CancellationItemAction.CANCELLED_UNFINISHED,
                                        ProductionQuantity.nonNegative(6),
                                        ProductionQuantity.nonNegative(2)),
                                cancellationLine(
                                        c,
                                        ProductionStatus.RELEASED,
                                        CancellationItemAction.PRESERVED_RELEASED,
                                        ProductionQuantity.zero(),
                                        ProductionQuantity.positive(5))));

        processor.onPost(context(docId));

        assertEquals(
                ProductionStatus.CANCELLED,
                items.require(a.orderId, a.itemId, a.specId).status());
        assertEquals(
                ProductionQuantity.zero(),
                items.require(a.orderId, a.itemId, a.specId).activeProductionQuantity());
        ProductionItemState afterB = items.require(b.orderId, b.itemId, b.specId);
        assertEquals(ProductionStatus.CANCELLED, afterB.status());
        assertEquals(ProductionQuantity.nonNegative(2), afterB.releasedQuantity());
        assertEquals(ProductionQuantity.zero(), afterB.activeProductionQuantity());
        ProductionItemState afterC = items.require(c.orderId, c.itemId, c.specId);
        assertEquals(ProductionStatus.RELEASED, afterC.status());
        assertTrue(cancellations.findByDocumentId(docId).orElseThrow().posted());
    }

    @Test
    void statusMismatchRejectedWithoutMutation() {
        Fixture fx = launch(10);
        UUID docId =
                prepareWholeOrderCancellation(
                        fx.orderId,
                        T1,
                        List.of(
                                new ProductionCancellation.ItemLine(
                                        fx.itemId,
                                        fx.specId,
                                        ProductionStatus.PARTIALLY_RELEASED,
                                        CancellationItemAction.CANCELLED_UNFINISHED,
                                        ProductionQuantity.positive(10),
                                        ProductionQuantity.zero())));
        assertThrows(
                ProductionCancellationValidationException.class,
                () -> processor.onPost(context(docId)));
        assertEquals(
                ProductionStatus.IN_PRODUCTION,
                items.require(fx.orderId, fx.itemId, fx.specId).status());
        assertFalse(cancellations.findByDocumentId(docId).orElseThrow().posted());
    }

    @Test
    void multiItemAtomicPreValidation() {
        SourceOrderId orderId = SourceOrderId.generate();
        Fixture a = launchSameOrder(orderId, 10);
        Fixture b = launchSameOrder(orderId, 3);
        UUID docId = UUID.randomUUID();
        cancellations.saveDraft(
                ProductionCancellation.draft(
                        docId,
                        orderId,
                        T1,
                        Optional.empty(),
                        List.of(
                                cancellationLine(a, ProductionStatus.IN_PRODUCTION),
                                new ProductionCancellation.ItemLine(
                                        b.itemId,
                                        b.specId,
                                        ProductionStatus.IN_PRODUCTION,
                                        CancellationItemAction.CANCELLED_UNFINISHED,
                                        ProductionQuantity.positive(99),
                                        ProductionQuantity.zero()))));

        assertThrows(
                ProductionCancellationValidationException.class,
                () -> processor.onPost(context(docId)));
        assertEquals(
                ProductionStatus.IN_PRODUCTION,
                items.require(a.orderId, a.itemId, a.specId).status());
        assertEquals(
                ProductionStatus.IN_PRODUCTION,
                items.require(b.orderId, b.itemId, b.specId).status());
        assertFalse(cancellations.findByDocumentId(docId).orElseThrow().posted());
    }

    private static ProductionCancellation.ItemLine cancellationLine(
            Fixture fx, ProductionStatus previousStatus) {
        return cancellationLine(
                fx,
                previousStatus,
                CancellationItemAction.CANCELLED_UNFINISHED,
                ProductionQuantity.positive(10),
                ProductionQuantity.zero());
    }

    private static ProductionCancellation.ItemLine cancellationLine(
            Fixture fx,
            ProductionStatus previousStatus,
            CancellationItemAction action,
            ProductionQuantity activeCancelled,
            ProductionQuantity releasedPreserved) {
        return new ProductionCancellation.ItemLine(
                fx.itemId, fx.specId, previousStatus, action, activeCancelled, releasedPreserved);
    }

    private UUID prepareWholeOrderCancellation(
            SourceOrderId orderId,
            Instant cancelledAt,
            List<ProductionCancellation.ItemLine> lines) {
        UUID docId = UUID.randomUUID();
        cancellations.saveDraft(
                ProductionCancellation.draft(docId, orderId, cancelledAt, Optional.empty(), lines));
        return docId;
    }

    private static DocumentOperationContext context(UUID documentId) {
        DocumentMetadata meta =
                new DocumentMetadata(
                        documentId,
                        ProductionCancellationProcessor.DOCUMENT_TYPE_ID,
                        "PC-001",
                        "test",
                        DocumentStatus.DRAFT,
                        0L,
                        Instant.now(),
                        Instant.now(),
                        null,
                        null);
        return () -> meta;
    }

    private Fixture launch(long quantity) {
        SourceOrderId orderId = SourceOrderId.generate();
        return launchSameOrder(orderId, quantity);
    }

    private Fixture launchSameOrder(SourceOrderId orderId, long quantity) {
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        items.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemId, specId, T0),
                        ProductionQuantity.positive(quantity),
                        T0));
        return new Fixture(orderId, itemId, specId);
    }

    private record Fixture(SourceOrderId orderId, SourceOrderItemId itemId, SpecificationId specId) {}

    private static final class InMemoryItemRepository implements ProductionItemStateRepository {

        private final Map<String, ProductionItemState> store = new ConcurrentHashMap<>();

        @Override
        public ProductionItemState save(ProductionItemState state) {
            store.put(key(state), state);
            return state;
        }

        @Override
        public Optional<ProductionItemState> findByIdentity(
                SourceOrderId sourceOrderId,
                SourceOrderItemId sourceOrderItemId,
                SpecificationId specificationId) {
            return Optional.ofNullable(
                    store.get(sourceOrderId + ":" + sourceOrderItemId + ":" + specificationId));
        }

        @Override
        public List<ProductionItemState> findBySourceOrderId(SourceOrderId sourceOrderId) {
            return store.values().stream()
                    .filter(state -> state.sourceOrderId().equals(sourceOrderId))
                    .toList();
        }

        ProductionItemState require(
                SourceOrderId orderId, SourceOrderItemId itemId, SpecificationId specId) {
            return findByIdentity(orderId, itemId, specId).orElseThrow();
        }

        private static String key(ProductionItemState state) {
            return state.sourceOrderId()
                    + ":"
                    + state.sourceOrderItemId()
                    + ":"
                    + state.specificationId();
        }
    }

    private static final class InMemoryCancellationRepository
            implements ProductionCancellationRepository {

        private final Map<UUID, ProductionCancellation> store = new ConcurrentHashMap<>();

        @Override
        public ProductionCancellation saveDraft(ProductionCancellation cancellation) {
            store.put(cancellation.documentId(), cancellation);
            return cancellation;
        }

        @Override
        public ProductionCancellation markPosted(ProductionCancellation cancellation) {
            store.put(cancellation.documentId(), cancellation);
            return cancellation;
        }

        @Override
        public Optional<ProductionCancellation> findByDocumentId(UUID documentId) {
            return Optional.ofNullable(store.get(documentId));
        }
    }
}
