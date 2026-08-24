package com.tmp.production.application.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentStatus;
import com.tmp.production.application.event.ProductionReleased;
import com.tmp.production.domain.MaterialPlanningSource;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionRelease;
import com.tmp.production.domain.ProductionReleaseImmutableException;
import com.tmp.production.domain.ProductionReleaseValidationException;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import com.tmp.production.domain.repository.ProductionReleaseRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductionReleaseProcessorTest {

    private static final Instant T0 = Instant.parse("2026-08-21T12:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-21T13:00:00Z");

    private InMemoryItemRepository items;
    private InMemoryReleaseRepository releases;
    private ProductionReleaseProcessor processor;
    private AtomicBoolean warehouseTouched;

    @BeforeEach
    void setUp() {
        items = new InMemoryItemRepository();
        releases = new InMemoryReleaseRepository();
        processor = new ProductionReleaseProcessor(releases, items, event -> {});
        warehouseTouched = new AtomicBoolean(false);
    }

    @Test
    void documentTypeId() {
        assertEquals("production.release", processor.documentTypeId());
    }

    @Test
    void fullReleaseMovesToReleased() {
        Fixture fx = launch(10);
        UUID docId = prepareRelease(fx, 10, List.of());
        processor.onPost(context(docId));

        ProductionItemState state = items.require(fx.orderId, fx.itemId, fx.specId);
        assertEquals(ProductionStatus.RELEASED, state.status());
        assertEquals(ProductionQuantity.zero(), state.activeProductionQuantity());
        assertEquals(ProductionQuantity.positive(10), state.releasedQuantity());
        assertTrue(releases.findByDocumentId(docId).orElseThrow().posted());
        assertFalse(warehouseTouched.get());
    }

    @Test
    void onPostSchedulesExactlyOneProductionReleasedEvent() {
        CapturingEventPublisher events = new CapturingEventPublisher();
        processor = new ProductionReleaseProcessor(releases, items, events);
        Fixture fx = launch(10);
        UUID docId = prepareRelease(fx, 3, List.of());
        processor.onPost(context(docId));
        assertEquals(1, events.events.size());
        assertEquals(
                ProductionReleased.EVENT_TYPE,
                ((ProductionReleased) events.events.getFirst()).eventType());
    }

    @Test
    void partialThenRepeatedRelease() {
        Fixture fx = launch(10);
        UUID doc1 = prepareRelease(fx, 3, List.of());
        processor.onPost(context(doc1));
        ProductionItemState after1 = items.require(fx.orderId, fx.itemId, fx.specId);
        assertEquals(ProductionStatus.PARTIALLY_RELEASED, after1.status());
        assertEquals(ProductionQuantity.nonNegative(7), after1.activeProductionQuantity());
        assertEquals(ProductionQuantity.nonNegative(3), after1.releasedQuantity());

        UUID doc2 = prepareRelease(fx, 4, List.of());
        processor.onPost(context(doc2));
        ProductionItemState after2 = items.require(fx.orderId, fx.itemId, fx.specId);
        assertEquals(ProductionStatus.PARTIALLY_RELEASED, after2.status());
        assertEquals(ProductionQuantity.nonNegative(3), after2.activeProductionQuantity());
        assertEquals(ProductionQuantity.nonNegative(7), after2.releasedQuantity());
    }

    @Test
    void overReleaseRejectedWithoutMutation() {
        Fixture fx = launch(3);
        UUID docId = prepareRelease(fx, 4, List.of());
        assertThrows(ProductionReleaseValidationException.class, () -> processor.onPost(context(docId)));
        ProductionItemState state = items.require(fx.orderId, fx.itemId, fx.specId);
        assertEquals(ProductionStatus.IN_PRODUCTION, state.status());
        assertEquals(ProductionQuantity.positive(3), state.activeProductionQuantity());
        assertFalse(releases.findByDocumentId(docId).orElseThrow().posted());
    }

    @Test
    void releasedAndCancelledStatusesRejected() {
        Fixture fx = launch(5);
        items.save(
                items.require(fx.orderId, fx.itemId, fx.specId)
                        .release(ProductionQuantity.positive(5), T1));
        UUID docReleased = prepareRelease(fx, 1, List.of());
        assertThrows(
                ProductionReleaseValidationException.class,
                () -> processor.onPost(context(docReleased)));

        Fixture cancelled = launch(5);
        items.save(items.require(cancelled.orderId, cancelled.itemId, cancelled.specId).cancel(T1));
        UUID docCancelled = prepareRelease(cancelled, 1, List.of());
        assertThrows(
                ProductionReleaseValidationException.class,
                () -> processor.onPost(context(docCancelled)));
    }

    @Test
    void multiItemAtomicPreValidation() {
        Fixture a = launch(10);
        Fixture b = launchSameOrder(a.orderId, 3);
        Fixture c = launchSameOrder(a.orderId, 5);

        UUID docId = UUID.randomUUID();
        ProductionRelease release =
                ProductionRelease.draft(
                        docId,
                        a.orderId,
                        T1,
                        List.of(
                                itemLine(a.itemId, a.specId, 2),
                                itemLine(b.itemId, b.specId, 4),
                                itemLine(c.itemId, c.specId, 1)),
                        List.of());
        releases.saveDraft(release);

        assertThrows(ProductionReleaseValidationException.class, () -> processor.onPost(context(docId)));
        assertEquals(ProductionStatus.IN_PRODUCTION, items.require(a.orderId, a.itemId, a.specId).status());
        assertEquals(ProductionStatus.IN_PRODUCTION, items.require(b.orderId, b.itemId, b.specId).status());
        assertEquals(ProductionStatus.IN_PRODUCTION, items.require(c.orderId, c.itemId, c.specId).status());
        assertFalse(releases.findByDocumentId(docId).orElseThrow().posted());
    }

    @Test
    void specificationMismatchRejected() {
        Fixture fx = launch(5);
        UUID docId = UUID.randomUUID();
        ProductionRelease release =
                ProductionRelease.draft(
                        docId,
                        fx.orderId,
                        T1,
                        List.of(itemLine(fx.itemId, SpecificationId.generate(), 1)),
                        List.of());
        releases.saveDraft(release);
        assertThrows(ProductionReleaseValidationException.class, () -> processor.onPost(context(docId)));
        assertEquals(ProductionStatus.IN_PRODUCTION, items.require(fx.orderId, fx.itemId, fx.specId).status());
    }

    @Test
    void planFactLinesDoNotMutateItemStateBeyondReleaseQuantity() {
        Fixture fx = launch(10);
        List<ProductionRelease.MaterialLine> materials =
                List.of(
                        material(10, 8),
                        material(10, 10),
                        material(10, 12),
                        material(5, 0));
        UUID docId = prepareRelease(fx, 10, materials);
        processor.onPost(context(docId));
        ProductionRelease loaded = releases.findByDocumentId(docId).orElseThrow();
        assertEquals(4, loaded.materialLines().size());
        assertEquals(new BigDecimal("8"), loaded.materialLines().get(0).actualQuantity());
        assertEquals(new BigDecimal("12"), loaded.materialLines().get(2).actualQuantity());
        assertEquals(BigDecimal.ZERO, loaded.materialLines().get(3).actualQuantity());
        assertFalse(warehouseTouched.get());
    }

    @Test
    void onUnpostUnsupported() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> processor.onUnpost(context(UUID.randomUUID())));
    }

    @Test
    void materialLineReferencingForeignItemRejected() {
        Fixture fx = launch(5);
        SourceOrderItemId foreignItemId = SourceOrderItemId.generate();
        UUID docId = UUID.randomUUID();
        ProductionRelease release =
                ProductionRelease.draft(
                        docId,
                        fx.orderId,
                        T1,
                        List.of(itemLine(fx.itemId, fx.specId, 1)),
                        List.of(
                                new ProductionRelease.MaterialLine(
                                        MaterialReferenceId.generate(),
                                        BigDecimal.ONE,
                                        BigDecimal.ONE,
                                        MaterialPlanningSource.SPECIFICATION,
                                        Optional.empty(),
                                        Optional.of(foreignItemId),
                                        Optional.empty())));
        releases.saveDraft(release);
        assertThrows(
                ProductionReleaseValidationException.class, () -> processor.onPost(context(docId)));
        assertEquals(ProductionStatus.IN_PRODUCTION, items.require(fx.orderId, fx.itemId, fx.specId).status());
    }

    @Test
    void postedPayloadCannotBeReplaced() {
        Fixture fx = launch(2);
        UUID docId = prepareRelease(fx, 2, List.of());
        processor.onPost(context(docId));
        ProductionRelease posted = releases.findByDocumentId(docId).orElseThrow();
        assertThrows(
                ProductionReleaseImmutableException.class,
                () ->
                        releases.saveDraft(
                                ProductionRelease.draft(
                                        docId,
                                        fx.orderId,
                                        T1,
                                        List.of(itemLine(fx.itemId, fx.specId, 1)),
                                        List.of())));
        assertTrue(posted.posted());
    }

    private UUID prepareRelease(
            Fixture fx, long qty, List<ProductionRelease.MaterialLine> materials) {
        UUID docId = UUID.randomUUID();
        releases.saveDraft(
                ProductionRelease.draft(
                        docId,
                        fx.orderId,
                        T1,
                        List.of(itemLine(fx.itemId, fx.specId, qty)),
                        materials));
        return docId;
    }

    private Fixture launch(long qty) {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        items.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemId, specId, T0),
                        ProductionQuantity.positive(qty),
                        T0));
        return new Fixture(orderId, itemId, specId);
    }

    private Fixture launchSameOrder(SourceOrderId orderId, long qty) {
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        items.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemId, specId, T0),
                        ProductionQuantity.positive(qty),
                        T0));
        return new Fixture(orderId, itemId, specId);
    }

    private static ProductionRelease.ItemLine itemLine(
            SourceOrderItemId itemId, SpecificationId specId, long qty) {
        return new ProductionRelease.ItemLine(
                itemId, specId, ProductionQuantity.positive(qty));
    }

    private static ProductionRelease.MaterialLine material(long plan, long fact) {
        return new ProductionRelease.MaterialLine(
                MaterialReferenceId.generate(),
                BigDecimal.valueOf(plan),
                BigDecimal.valueOf(fact),
                MaterialPlanningSource.SPECIFICATION,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static DocumentOperationContext context(UUID documentId) {
        DocumentMetadata meta =
                new DocumentMetadata(
                        documentId,
                        ProductionReleaseProcessor.DOCUMENT_TYPE_ID,
                        "PR-001",
                        "test",
                        DocumentStatus.DRAFT,
                        0L,
                        Instant.now(),
                        Instant.now(),
                        null,
                        null);
        return () -> meta;
    }

    private record Fixture(SourceOrderId orderId, SourceOrderItemId itemId, SpecificationId specId) {}

    private static final class InMemoryItemRepository implements ProductionItemStateRepository {
        private final Map<String, ProductionItemState> store = new ConcurrentHashMap<>();

        @Override
        public ProductionItemState save(ProductionItemState state) {
            store.put(key(state.sourceOrderId(), state.sourceOrderItemId(), state.specificationId()), state);
            return state;
        }

        @Override
        public Optional<ProductionItemState> findByIdentity(
                SourceOrderId sourceOrderId,
                SourceOrderItemId sourceOrderItemId,
                SpecificationId specificationId) {
            return Optional.ofNullable(store.get(key(sourceOrderId, sourceOrderItemId, specificationId)));
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

        private static String key(
                SourceOrderId o, SourceOrderItemId i, SpecificationId s) {
            return o.value() + ":" + i.value() + ":" + s.value();
        }
    }

    private static final class InMemoryReleaseRepository implements ProductionReleaseRepository {
        private final Map<UUID, ProductionRelease> store = new ConcurrentHashMap<>();

        @Override
        public ProductionRelease saveDraft(ProductionRelease release) {
            ProductionRelease existing = store.get(release.documentId());
            if (existing != null && existing.posted()) {
                throw new ProductionReleaseImmutableException(release.documentId());
            }
            store.put(release.documentId(), release);
            return release;
        }

        @Override
        public ProductionRelease markPosted(ProductionRelease release) {
            store.put(release.documentId(), release);
            return release;
        }

        @Override
        public Optional<ProductionRelease> findByDocumentId(UUID documentId) {
            return Optional.ofNullable(store.get(documentId));
        }
    }

    private static final class CapturingEventPublisher
            implements com.tmp.document.api.TransactionalEventPublisher {

        final List<com.tmp.core.api.event.DomainEvent> events = new ArrayList<>();

        @Override
        public void publishAfterCommit(com.tmp.core.api.event.DomainEvent event) {
            events.add(event);
        }
    }
}
