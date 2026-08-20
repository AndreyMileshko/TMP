package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentEngineStatus;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.DocumentQuery;
import com.tmp.document.api.DocumentStatus;
import com.tmp.document.api.DocumentTypeDescriptor;
import com.tmp.document.api.UpdateDocumentCommand;
import com.tmp.order.api.OrderStatus;
import com.tmp.production.application.document.ProductionLaunchPayloadHolder;
import com.tmp.production.application.document.ProductionLaunchProcessor;
import com.tmp.production.application.port.OrderForProductionQueryPort;
import com.tmp.production.application.port.OrderForProductionQueryPort.ResolvedItemLine;
import com.tmp.production.application.port.OrderForProductionQueryPort.ResolvedOrderForLaunch;
import com.tmp.production.application.port.OrderSpecificationQueryPort;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedSpecification;
import com.tmp.production.domain.FrozenSpecificationUnavailableException;
import com.tmp.production.domain.OrderNotEligibleForProductionException;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionLaunchConflictException;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.SpecificationNotAvailableForLaunchException;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import com.tmp.production.testsupport.ProductionTestFixtures;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WholeOrderProductionLaunchTest {

    private static final Instant T0 = Instant.parse("2026-08-20T06:00:00Z");

    private StubOrderForProductionQuery orderQuery;
    private StubDocumentEngine documentEngine;
    private ProductionLaunchPayloadHolder payloadHolder;
    private InMemoryRepository repository;
    private CapturingEventPublisher eventPublisher;
    private ProductionLaunchService launchService;
    private ProductionLaunchProcessor processor;
    private TrackingSpecificationQuery specificationQuery;
    private ProductionFoundationQueryService foundationQueryService;

    @BeforeEach
    void setUp() {
        orderQuery = new StubOrderForProductionQuery();
        documentEngine = new StubDocumentEngine();
        payloadHolder = new ProductionLaunchPayloadHolder();
        repository = new InMemoryRepository();
        eventPublisher = new CapturingEventPublisher();
        launchService =
                new ProductionLaunchService(
                        documentEngine,
                        payloadHolder,
                        orderQuery,
                        Clock.fixed(T0, ZoneOffset.UTC));
        processor = new ProductionLaunchProcessor(repository, eventPublisher, payloadHolder);
        specificationQuery = new TrackingSpecificationQuery();
        foundationQueryService = new ProductionFoundationQueryService(specificationQuery);
    }

    @Test
    void successfulOrderLaunchCreatesAllItemStates() {
        SourceOrderId orderId = SourceOrderId.generate();
        SpecificationId spec1 = SpecificationId.generate();
        SpecificationId spec2 = SpecificationId.generate();
        SpecificationId spec3 = SpecificationId.generate();
        SourceOrderItemId item1 = SourceOrderItemId.generate();
        SourceOrderItemId item2 = SourceOrderItemId.generate();
        SourceOrderItemId item3 = SourceOrderItemId.generate();
        orderQuery.order =
                activeOrder(
                        orderId,
                        List.of(
                                itemLine(item1, spec1, 10),
                                itemLine(item2, spec2, 5),
                                itemLine(item3, spec3, 2)));

        DocumentMetadata posted =
                launchService.launch(new LaunchProductionCommand(orderId.value(), "operator"));
        assertEquals(DocumentStatus.POSTED, posted.status());

        processor.onPost(() -> posted);

        assertEquals(3, repository.size());
        assertState(orderId, item1, spec1, 10);
        assertState(orderId, item2, spec2, 5);
        assertState(orderId, item3, spec3, 2);
        assertEquals(1, eventPublisher.events.size());
    }

    @Test
    void orderedQuantitiesComeFromOrderManagementOnly() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemA = SourceOrderItemId.generate();
        SourceOrderItemId itemB = SourceOrderItemId.generate();
        orderQuery.order =
                activeOrder(
                        orderId,
                        List.of(
                                itemLine(itemA, SpecificationId.generate(), 10),
                                itemLine(itemB, SpecificationId.generate(), 5)));

        DocumentMetadata posted =
                launchService.launch(new LaunchProductionCommand(orderId.value(), "operator"));
        processor.onPost(() -> posted);

        assertEquals(
                ProductionQuantity.positive(10),
                repository.find(orderId, itemA).orElseThrow().orderedQuantity());
        assertEquals(
                ProductionQuantity.positive(5),
                repository.find(orderId, itemB).orElseThrow().orderedQuantity());
    }

    @Test
    void nonActiveOrderRejected() {
        SourceOrderId orderId = SourceOrderId.generate();
        orderQuery.order =
                new ResolvedOrderForLaunch(
                        orderId, OrderStatus.DRAFT, 1, List.of(itemLine(SourceOrderItemId.generate(), SpecificationId.generate(), 1)), List.of());

        assertThrows(
                OrderNotEligibleForProductionException.class,
                () -> launchService.launch(new LaunchProductionCommand(orderId.value(), "op")));
        assertEquals(0, repository.size());
        assertEquals(0, eventPublisher.events.size());
    }

    @Test
    void duplicateItemConflictRollsBackWholeOrder() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId existingItem = SourceOrderItemId.generate();
        SpecificationId existingSpec = SpecificationId.generate();
        seedExistingState(orderId, existingItem, existingSpec, 1);

        orderQuery.order =
                activeOrder(
                        orderId,
                        List.of(
                                itemLine(SourceOrderItemId.generate(), SpecificationId.generate(), 2),
                                itemLine(existingItem, existingSpec, 3),
                                itemLine(SourceOrderItemId.generate(), SpecificationId.generate(), 4)));

        DocumentMetadata posted =
                launchService.launch(new LaunchProductionCommand(orderId.value(), "operator"));
        assertThrows(
                ProductionLaunchConflictException.class, () -> processor.onPost(() -> posted));
        assertEquals(1, repository.size());
        assertEquals(0, eventPublisher.events.size());
    }

    @Test
    void failureOnMiddleItemRollsBackAllNewStates() {
        SourceOrderId orderId = SourceOrderId.generate();
        orderQuery.order =
                activeOrder(
                        orderId,
                        List.of(
                                itemLine(SourceOrderItemId.generate(), SpecificationId.generate(), 1),
                                itemLine(SourceOrderItemId.generate(), SpecificationId.generate(), 2),
                                itemLine(SourceOrderItemId.generate(), SpecificationId.generate(), 3)));

        DocumentMetadata posted =
                launchService.launch(new LaunchProductionCommand(orderId.value(), "operator"));
        repository.failOnSaveCount = 2;

        assertThrows(RuntimeException.class, () -> processor.onPost(() -> posted));
        assertEquals(0, repository.size());
        assertEquals(0, eventPublisher.events.size());
    }

    @Test
    void specificationFreezeSurvivesNewCurrentSpecification() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId frozenSpec = SpecificationId.generate();
        orderQuery.order = activeOrder(orderId, List.of(itemLine(itemId, frozenSpec, 4)));

        DocumentMetadata posted =
                launchService.launch(new LaunchProductionCommand(orderId.value(), "operator"));
        processor.onPost(() -> posted);

        ProductionItemState state = repository.find(orderId, itemId).orElseThrow();
        specificationQuery.byIdSpec =
                Optional.of(
                        resolvedSpec(
                                frozenSpec,
                                itemId,
                                4,
                                List.of(new ResolvedMaterialLine("MAT-FROZEN", "Frozen", null, null, BigDecimal.ONE, "PCS"))));
        specificationQuery.currentSpec =
                Optional.of(
                        resolvedSpec(
                                SpecificationId.generate(),
                                itemId,
                                4,
                                List.of(new ResolvedMaterialLine("MAT-NEW", "New", null, null, BigDecimal.TEN, "PCS"))));

        ResolvedSpecification resolved =
                foundationQueryService.resolveFrozenSpecification(state).orElseThrow();
        assertEquals(frozenSpec, resolved.specificationId());
        assertEquals("MAT-FROZEN", resolved.materialLines().getFirst().materialCode());
        assertEquals(0, specificationQuery.resolveCurrentCalls);
    }

    @Test
    void unavailableFrozenSpecificationThrowsExplicitError() {
        var foundation = ProductionTestFixtures.sampleFoundation(T0);
        assertThrows(
                FrozenSpecificationUnavailableException.class,
                () -> foundationQueryService.materialLines(foundation));
    }

    @Test
    void zeroMaterialLinesIsValidWhenSpecificationExists() {
        var foundation = ProductionTestFixtures.sampleFoundation(T0);
        specificationQuery.byIdSpec =
                Optional.of(
                        resolvedSpec(
                                foundation.specificationId(),
                                foundation.sourceOrderItemId(),
                                1,
                                List.of()));
        assertTrue(foundationQueryService.materialLines(foundation).isEmpty());
    }

    @Test
    void missingSpecificationOnActiveItemFailsBeforeDocumentPost() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId missingItem = SourceOrderItemId.generate();
        SourceOrderItemId readyItem = SourceOrderItemId.generate();
        orderQuery.order =
                new ResolvedOrderForLaunch(
                        orderId,
                        OrderStatus.ACTIVE,
                        2,
                        List.of(itemLine(readyItem, SpecificationId.generate(), 1)),
                        List.of(missingItem));

        SpecificationNotAvailableForLaunchException ex =
                assertThrows(
                        SpecificationNotAvailableForLaunchException.class,
                        () -> launchService.launch(new LaunchProductionCommand(orderId.value(), "operator")));
        assertEquals(missingItem, ex.sourceOrderItemId());
        assertEquals(0, repository.size());
        assertEquals(0, eventPublisher.events.size());
    }

    @Test
    void zeroActiveItemsRejectedAsNotEligible() {
        SourceOrderId orderId = SourceOrderId.generate();
        orderQuery.order =
                new ResolvedOrderForLaunch(orderId, OrderStatus.ACTIVE, 0, List.of(), List.of());

        assertThrows(
                OrderNotEligibleForProductionException.class,
                () -> launchService.launch(new LaunchProductionCommand(orderId.value(), "operator")));
    }

    @Test
    void oneActiveItemMissingSpecificationUsesThatItemId() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId missingItem = SourceOrderItemId.generate();
        orderQuery.order =
                new ResolvedOrderForLaunch(
                        orderId, OrderStatus.ACTIVE, 1, List.of(), List.of(missingItem));

        SpecificationNotAvailableForLaunchException ex =
                assertThrows(
                        SpecificationNotAvailableForLaunchException.class,
                        () -> launchService.launch(new LaunchProductionCommand(orderId.value(), "op")));
        assertEquals(missingItem, ex.sourceOrderItemId());
    }

    private void seedExistingState(
            SourceOrderId orderId,
            SourceOrderItemId itemId,
            SpecificationId specId,
            long quantity) {
        ProductionItemState existing =
                ProductionItemState.launch(
                        com.tmp.production.domain.ProductionFoundation.freeze(
                                orderId, itemId, specId, T0),
                        ProductionQuantity.positive(quantity),
                        T0);
        repository.save(existing);
    }

    private void assertState(
            SourceOrderId orderId,
            SourceOrderItemId itemId,
            SpecificationId specId,
            long quantity) {
        ProductionItemState state = repository.find(orderId, itemId).orElseThrow();
        assertEquals(ProductionStatus.IN_PRODUCTION, state.status());
        assertEquals(specId, state.specificationId());
        assertEquals(ProductionQuantity.positive(quantity), state.orderedQuantity());
        assertEquals(orderId, state.sourceOrderId());
    }

    private static ResolvedOrderForLaunch activeOrder(
            SourceOrderId orderId, List<ResolvedItemLine> lines) {
        return new ResolvedOrderForLaunch(orderId, OrderStatus.ACTIVE, lines.size(), lines, List.of());
    }

    private static ResolvedItemLine itemLine(
            SourceOrderItemId itemId, SpecificationId specId, long quantity) {
        return new ResolvedItemLine(itemId, specId, BigDecimal.valueOf(quantity));
    }

    private static ResolvedSpecification resolvedSpec(
            SpecificationId specId,
            SourceOrderItemId itemId,
            long quantity,
            List<ResolvedMaterialLine> lines) {
        return new ResolvedSpecification(
                specId, itemId, BigDecimal.valueOf(quantity), lines);
    }

    private static final class StubOrderForProductionQuery implements OrderForProductionQueryPort {

        ResolvedOrderForLaunch order = activeOrder(SourceOrderId.generate(), List.of());
        RuntimeException throwOnResolve;

        @Override
        public Optional<ResolvedOrderForLaunch> resolveForLaunch(SourceOrderId sourceOrderId) {
            if (throwOnResolve != null) {
                throw throwOnResolve;
            }
            if (!order.sourceOrderId().equals(sourceOrderId)) {
                return Optional.empty();
            }
            return Optional.of(order);
        }
    }

    private static final class StubDocumentEngine implements DocumentEngine {

        private final Map<UUID, DocumentMetadata> documents = new ConcurrentHashMap<>();

        @Override
        public DocumentMetadata createDocument(CreateDocumentCommand command) {
            UUID id = UUID.randomUUID();
            DocumentMetadata draft =
                    new DocumentMetadata(
                            id,
                            command.documentTypeId(),
                            "PL-001",
                            command.title(),
                            DocumentStatus.DRAFT,
                            0L,
                            T0,
                            T0,
                            null,
                            null);
            documents.put(id, draft);
            return draft;
        }

        @Override
        public DocumentMetadata postDocument(UUID documentId) {
            DocumentMetadata draft = documents.get(documentId);
            DocumentMetadata posted =
                    new DocumentMetadata(
                            draft.id(),
                            draft.documentTypeId(),
                            draft.documentNumber(),
                            draft.title(),
                            DocumentStatus.POSTED,
                            draft.version(),
                            draft.createdAt(),
                            T0,
                            T0,
                            draft.closedAt());
            documents.put(documentId, posted);
            return posted;
        }

        @Override
        public DocumentMetadata unpostDocument(UUID documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DocumentMetadata closeDocument(UUID documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DocumentMetadata updateDocument(UpdateDocumentCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteDocument(UUID documentId) {
            documents.remove(documentId);
        }

        @Override
        public Optional<DocumentMetadata> findById(UUID documentId) {
            return Optional.ofNullable(documents.get(documentId));
        }

        @Override
        public List<DocumentMetadata> search(DocumentQuery query) {
            return new ArrayList<>(documents.values());
        }

        @Override
        public DocumentProcessorRegistration registerProcessor(
                com.tmp.document.api.DocumentProcessor processor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DocumentTypeDescriptor> registeredTypes() {
            return List.of();
        }

        @Override
        public DocumentEngineStatus status() {
            return new DocumentEngineStatus(0, 0);
        }
    }

    private static final class InMemoryRepository implements ProductionItemStateRepository {

        private final Map<String, ProductionItemState> store = new ConcurrentHashMap<>();
        int failOnSaveCount = -1;
        private int saveAttempts;

        @Override
        public ProductionItemState save(ProductionItemState state) {
            saveAttempts++;
            if (saveAttempts == failOnSaveCount) {
                store.clear();
                throw new RuntimeException("controlled save failure");
            }
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

        Optional<ProductionItemState> find(SourceOrderId orderId, SourceOrderItemId itemId) {
            return store.values().stream()
                    .filter(
                            state ->
                                    state.sourceOrderId().equals(orderId)
                                            && state.sourceOrderItemId().equals(itemId))
                    .findFirst();
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

    private static final class TrackingSpecificationQuery implements OrderSpecificationQueryPort {

        Optional<ResolvedSpecification> currentSpec = Optional.empty();
        Optional<ResolvedSpecification> byIdSpec = Optional.empty();
        int resolveCurrentCalls;
        int resolveByIdCalls;

        @Override
        public Optional<ResolvedSpecification> resolveCurrentForLaunch(
                SourceOrderItemId sourceOrderItemId) {
            resolveCurrentCalls++;
            return currentSpec;
        }

        @Override
        public Optional<ResolvedSpecification> resolveById(SpecificationId specificationId) {
            resolveByIdCalls++;
            return byIdSpec;
        }
    }
}
