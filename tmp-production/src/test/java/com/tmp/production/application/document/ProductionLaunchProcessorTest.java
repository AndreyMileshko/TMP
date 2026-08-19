package com.tmp.production.application.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentStatus;
import com.tmp.production.application.event.ProductionLaunched;
import com.tmp.production.domain.AlreadyLaunchedForProductionException;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
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

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        eventPublisher = new CapturingEventPublisher();
        payloadHolder = new ProductionLaunchPayloadHolder();
        processor = new ProductionLaunchProcessor(repository, eventPublisher, payloadHolder);
    }

    @Test
    void documentTypeId() {
        assertEquals("production.launch", processor.documentTypeId());
    }

    @Test
    void onPostCreatesStateInProduction() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        Instant now = Instant.now();

        ProductionLaunchPayload payload =
                new ProductionLaunchPayload(
                        orderId, itemId, specId,
                        ProductionQuantity.positive(10), now, "operator");

        UUID docId = UUID.randomUUID();
        payloadHolder.set(docId, payload);

        processor.onPost(contextFor(docId));

        Optional<ProductionItemState> saved =
                repository.findByIdentity(orderId, itemId, specId);
        assertTrue(saved.isPresent());
        assertEquals(ProductionStatus.IN_PRODUCTION, saved.get().status());
        assertEquals(ProductionQuantity.positive(10), saved.get().orderedQuantity());
        assertEquals(specId, saved.get().specificationId());
    }

    @Test
    void onPostPublishesDomainEvent() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();

        ProductionLaunchPayload payload =
                new ProductionLaunchPayload(
                        orderId, itemId, specId,
                        ProductionQuantity.positive(5), Instant.now(), "user1");

        UUID docId = UUID.randomUUID();
        payloadHolder.set(docId, payload);

        processor.onPost(contextFor(docId));

        assertEquals(1, eventPublisher.events.size());
        ProductionLaunched event = (ProductionLaunched) eventPublisher.events.get(0);
        assertEquals(orderId.value(), event.sourceOrderId());
        assertEquals(itemId.value(), event.sourceOrderItemId());
        assertEquals(specId.value(), event.specificationId());
        assertEquals(5, event.orderedQuantity());
        assertEquals(ProductionLaunched.EVENT_TYPE, event.eventType());
        assertEquals("production", event.sourceCapabilityId());
    }

    @Test
    void duplicateLaunchThrowsBusinessException() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();

        ProductionLaunchPayload payload =
                new ProductionLaunchPayload(
                        orderId, itemId, specId,
                        ProductionQuantity.positive(3), Instant.now(), "op");

        UUID docId1 = UUID.randomUUID();
        payloadHolder.set(docId1, payload);
        processor.onPost(contextFor(docId1));

        UUID docId2 = UUID.randomUUID();
        payloadHolder.set(docId2, payload);
        AlreadyLaunchedForProductionException ex =
                assertThrows(
                        AlreadyLaunchedForProductionException.class,
                        () -> processor.onPost(contextFor(docId2)));

        assertEquals(itemId, ex.sourceOrderItemId());
        assertEquals(specId, ex.specificationId());
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

    @Test
    void specificationIdIsPreserved() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();

        ProductionLaunchPayload payload =
                new ProductionLaunchPayload(
                        orderId, itemId, specId,
                        ProductionQuantity.positive(1), Instant.now(), "user");

        UUID docId = UUID.randomUUID();
        payloadHolder.set(docId, payload);
        processor.onPost(contextFor(docId));

        ProductionItemState state =
                repository.findByIdentity(orderId, itemId, specId).orElseThrow();
        assertEquals(specId, state.specificationId());
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
