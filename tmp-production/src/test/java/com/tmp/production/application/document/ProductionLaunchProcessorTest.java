package com.tmp.production.application.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentStatus;
import com.tmp.production.application.ProductionHistoryService;
import com.tmp.production.application.event.OrderAcceptedIntoProduction;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionLaunchConflictException;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import com.tmp.production.testsupport.InMemoryProductionHistoryRepository;
import com.tmp.production.testsupport.ProductionHistoryTestSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductionLaunchProcessorTest {

    private InMemoryRepository repository;
    private CapturingEventPublisher eventPublisher;
    private ProductionLaunchPayloadHolder payloadHolder;
    private ProductionLaunchProcessor processor;
    private InMemoryProductionHistoryRepository historyRepository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        eventPublisher = new CapturingEventPublisher();
        payloadHolder = new ProductionLaunchPayloadHolder();
        historyRepository = new InMemoryProductionHistoryRepository();
        ProductionHistoryService historyService =
                ProductionHistoryTestSupport.historyService(historyRepository);
        processor =
                new ProductionLaunchProcessor(
                        repository, eventPublisher, payloadHolder, historyService);
    }

    @Test
    void documentTypeId() {
        assertEquals("production.launch", processor.documentTypeId());
    }

    @Test
    void onPostCreatesAllItemStates() {
        SourceOrderId orderId = SourceOrderId.generate();
        Instant now = Instant.now();
        ProductionLaunchPayload payload =
                payload(
                        orderId,
                        List.of(
                                line(orderId, SourceOrderItemId.generate(), SpecificationId.generate(), 10, now),
                                line(orderId, SourceOrderItemId.generate(), SpecificationId.generate(), 5, now)),
                        now,
                        "operator");

        UUID docId = UUID.randomUUID();
        payloadHolder.set(docId, payload);
        processor.onPost(contextFor(docId));

        assertEquals(2, repository.size());
        for (ProductionLaunchLine launchLine : payload.lines()) {
            Optional<ProductionItemState> saved =
                    repository.findByIdentity(
                            launchLine.foundation().sourceOrderId(),
                            launchLine.foundation().sourceOrderItemId(),
                            launchLine.foundation().specificationId());
            assertTrue(saved.isPresent());
            assertEquals(ProductionStatus.IN_PRODUCTION, saved.get().status());
            assertEquals(launchLine.orderedQuantity(), saved.get().orderedQuantity());
        }
    }

    @Test
    void onPostPublishesSingleOrderAcceptedEvent() {
        SourceOrderId orderId = SourceOrderId.generate();
        Instant now = Instant.now();
        SourceOrderItemId item1 = SourceOrderItemId.generate();
        SourceOrderItemId item2 = SourceOrderItemId.generate();
        ProductionLaunchPayload payload =
                payload(
                        orderId,
                        List.of(
                                line(orderId, item1, SpecificationId.generate(), 3, now),
                                line(orderId, item2, SpecificationId.generate(), 7, now)),
                        now,
                        "user1");

        UUID docId = UUID.randomUUID();
        payloadHolder.set(docId, payload);
        processor.onPost(contextFor(docId));

        assertEquals(1, eventPublisher.events.size());
        OrderAcceptedIntoProduction event =
                (OrderAcceptedIntoProduction) eventPublisher.events.getFirst();
        assertEquals(orderId.value(), event.sourceOrderId());
        assertEquals(2, event.acceptedItemCount());
        assertEquals(
                List.of(item1.value(), item2.value()), event.sourceOrderItemIds());
        assertEquals(OrderAcceptedIntoProduction.EVENT_TYPE, event.eventType());
        assertEquals(
                1,
                historyRepository
                        .ofType(
                                com.tmp.production.domain.ProductionHistoryEntry.ProductionHistoryType
                                        .ORDER_ACCEPTED)
                        .size());
        assertEquals(
                "user1",
                historyRepository.listByOrder(orderId).getFirst().actorRef().orElseThrow());
    }

    @Test
    void duplicateLaunchRejectsWholeOrder() {
        SourceOrderId orderId = SourceOrderId.generate();
        Instant now = Instant.now();
        SourceOrderItemId existingItem = SourceOrderItemId.generate();
        SpecificationId existingSpec = SpecificationId.generate();
        ProductionLaunchPayload existing =
                payload(
                        orderId,
                        List.of(line(orderId, existingItem, existingSpec, 3, now)),
                        now,
                        "op");
        UUID docId1 = UUID.randomUUID();
        payloadHolder.set(docId1, existing);
        processor.onPost(contextFor(docId1));

        ProductionLaunchPayload conflict =
                payload(
                        orderId,
                        List.of(
                                line(orderId, SourceOrderItemId.generate(), SpecificationId.generate(), 2, now),
                                line(orderId, existingItem, existingSpec, 4, now)),
                        now,
                        "op");
        UUID docId2 = UUID.randomUUID();
        payloadHolder.set(docId2, conflict);

        assertThrows(
                ProductionLaunchConflictException.class, () -> processor.onPost(contextFor(docId2)));
        assertEquals(1, repository.size());
        assertEquals(1, eventPublisher.events.size());
    }

    @Test
    void onUnpostThrows() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> processor.onUnpost(contextFor(UUID.randomUUID())));
    }

    @Test
    void noPayloadThrows() {
        assertThrows(
                IllegalStateException.class,
                () -> processor.onPost(contextFor(UUID.randomUUID())));
    }

    private static ProductionLaunchLine line(
            SourceOrderId orderId,
            SourceOrderItemId itemId,
            SpecificationId specId,
            long quantity,
            Instant frozenAt) {
        ProductionFoundation foundation = ProductionFoundation.freeze(orderId, itemId, specId, frozenAt);
        return new ProductionLaunchLine(foundation, ProductionQuantity.positive(quantity));
    }

    private static ProductionLaunchPayload payload(
            SourceOrderId orderId,
            List<ProductionLaunchLine> lines,
            Instant launchTimestamp,
            String createdBy) {
        return new ProductionLaunchPayload(orderId, lines, createdBy, launchTimestamp);
    }

    private static DocumentOperationContext contextFor(UUID documentId) {
        DocumentMetadata meta =
                new DocumentMetadata(
                        documentId,
                        ProductionLaunchProcessor.DOCUMENT_TYPE_ID,
                        "PL-001",
                        "test",
                        DocumentStatus.DRAFT,
                        0L,
                        Instant.now(),
                        Instant.now(),
                        null,
                        null);
        return () -> meta;
    }

    private static final class InMemoryRepository implements ProductionItemStateRepository {

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

        int size() {
            return store.size();
        }

        private static String key(
                SourceOrderId o, SourceOrderItemId i, SpecificationId s) {
            return o.value() + ":" + i.value() + ":" + s.value();
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
