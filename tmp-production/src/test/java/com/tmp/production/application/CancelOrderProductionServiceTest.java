package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentEngineStatus;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.DocumentQuery;
import com.tmp.document.api.DocumentStatus;
import com.tmp.document.api.DocumentTypeDescriptor;
import com.tmp.document.api.UpdateDocumentCommand;
import com.tmp.production.application.document.ProductionCancellationProcessor;
import com.tmp.production.application.internal.ProductionCancellationDocumentService;
import com.tmp.production.domain.CancelOrderProductionException;
import com.tmp.production.domain.CancellationItemAction;
import com.tmp.production.domain.OrderProductionViewStatus;
import com.tmp.production.domain.ProductionCancellationAlreadyExistsException;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.ProductionCancellationQuery;
import com.tmp.production.domain.repository.ProductionCancellationRepository;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;

class CancelOrderProductionServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-23T10:00:00Z");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-23T11:00:00Z"), ZoneOffset.UTC);

    private InMemoryItemRepository itemRepository;
    private InMemoryCancellationRepository cancellationRepository;
    private CancelOrderProductionService service;
    private ProductionOrderViewService viewService;

    @BeforeEach
    void setUp() {
        itemRepository = new InMemoryItemRepository();
        cancellationRepository = new InMemoryCancellationRepository();
        ProductionCancellationProcessor processor =
                new ProductionCancellationProcessor(cancellationRepository, itemRepository, event -> {});
        ProductionCancellationDocumentService documentService =
                new ProductionCancellationDocumentService(
                        new StubDocumentEngine(processor),
                        cancellationRepository,
                        CLOCK);
        viewService =
                new ProductionOrderViewService(itemRepository, cancellationRepository);
        service =
                new CancelOrderProductionService(
                        viewService,
                        cancellationRepository,
                        documentService,
                        new PassthroughTransactionManager(),
                        CLOCK);
    }

    @Test
    void cancelsWholeOrderInProduction() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        itemRepository.save(launched(orderId, itemId, specId, 10));

        CancelOrderProductionResult result =
                service.cancelOrderProduction(new CancelOrderProductionCommand(orderId.value()));

        assertEquals(orderId.value(), result.sourceOrderId());
        assertEquals(1, result.itemResults().size());
        assertEquals(CancellationItemAction.CANCELLED_UNFINISHED, result.itemResults().get(0).action());
        ProductionItemState state = itemRepository.require(orderId, itemId, specId);
        assertEquals(ProductionStatus.CANCELLED, state.status());
        assertEquals(ProductionQuantity.zero(), state.activeProductionQuantity());
        assertEquals(OrderProductionViewStatus.CANCELLED, viewService.getOrderProductionView(orderId).status());
        assertTrue(cancellationRepository.hasPostedCancellation(orderId));
    }

    @Test
    void mixedOrderPreservesReleasedQuantities() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemA = SourceOrderItemId.generate();
        SourceOrderItemId itemB = SourceOrderItemId.generate();
        SourceOrderItemId itemC = SourceOrderItemId.generate();
        SpecificationId specA = SpecificationId.generate();
        SpecificationId specB = SpecificationId.generate();
        SpecificationId specC = SpecificationId.generate();
        itemRepository.save(launched(orderId, itemA, specA, 10));
        itemRepository.save(launched(orderId, itemB, specB, 10));
        itemRepository.save(
                itemRepository
                        .require(orderId, itemB, specB)
                        .release(ProductionQuantity.positive(2), T0));
        itemRepository.save(launched(orderId, itemC, specC, 5));
        itemRepository.save(
                itemRepository
                        .require(orderId, itemC, specC)
                        .release(ProductionQuantity.positive(5), T0));

        service.cancelOrderProduction(new CancelOrderProductionCommand(orderId.value()));

        assertEquals(ProductionStatus.CANCELLED, itemRepository.require(orderId, itemA, specA).status());
        ProductionItemState afterB = itemRepository.require(orderId, itemB, specB);
        assertEquals(ProductionStatus.CANCELLED, afterB.status());
        assertEquals(ProductionQuantity.nonNegative(2), afterB.releasedQuantity());
        assertEquals(ProductionQuantity.zero(), afterB.activeProductionQuantity());
        assertEquals(ProductionStatus.RELEASED, itemRepository.require(orderId, itemC, specC).status());
        assertEquals(OrderProductionViewStatus.CANCELLED, viewService.getOrderProductionView(orderId).status());
    }

    @Test
    void rejectsManufacturedOrder() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        itemRepository.save(launched(orderId, itemId, specId, 3));
        itemRepository.save(
                itemRepository
                        .require(orderId, itemId, specId)
                        .release(ProductionQuantity.positive(3), T0));

        assertThrows(
                CancelOrderProductionException.class,
                () -> service.cancelOrderProduction(new CancelOrderProductionCommand(orderId.value())));
    }

    @Test
    void rejectsDuplicateCancellation() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        itemRepository.save(launched(orderId, itemId, specId, 5));
        service.cancelOrderProduction(new CancelOrderProductionCommand(orderId.value()));

        assertThrows(
                ProductionCancellationAlreadyExistsException.class,
                () -> service.cancelOrderProduction(new CancelOrderProductionCommand(orderId.value())));
    }

    @Test
    void rejectsNotAcceptedOrder() {
        SourceOrderId orderId = SourceOrderId.generate();
        assertThrows(
                CancelOrderProductionException.class,
                () -> service.cancelOrderProduction(new CancelOrderProductionCommand(orderId.value())));
    }

    private static ProductionItemState launched(
            SourceOrderId orderId,
            SourceOrderItemId itemId,
            SpecificationId specId,
            long quantity) {
        return ProductionItemState.launch(
                ProductionFoundation.freeze(orderId, itemId, specId, T0),
                ProductionQuantity.positive(quantity),
                T0);
    }

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
                    .sorted(
                            (a, b) ->
                                    a.sourceOrderItemId()
                                            .value()
                                            .compareTo(b.sourceOrderItemId().value()))
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
            implements ProductionCancellationRepository, ProductionCancellationQuery {

        private final Map<UUID, com.tmp.production.domain.ProductionCancellation> byDocument =
                new ConcurrentHashMap<>();
        private final Map<UUID, com.tmp.production.domain.ProductionCancellation> byOrder =
                new ConcurrentHashMap<>();

        @Override
        public com.tmp.production.domain.ProductionCancellation saveDraft(
                com.tmp.production.domain.ProductionCancellation cancellation) {
            byDocument.put(cancellation.documentId(), cancellation);
            byOrder.put(cancellation.sourceOrderId().value(), cancellation);
            return cancellation;
        }

        @Override
        public com.tmp.production.domain.ProductionCancellation markPosted(
                com.tmp.production.domain.ProductionCancellation cancellation) {
            byDocument.put(cancellation.documentId(), cancellation);
            byOrder.put(cancellation.sourceOrderId().value(), cancellation);
            return cancellation;
        }

        @Override
        public Optional<com.tmp.production.domain.ProductionCancellation> findByDocumentId(
                UUID documentId) {
            return Optional.ofNullable(byDocument.get(documentId));
        }

        @Override
        public boolean hasPostedCancellation(SourceOrderId sourceOrderId) {
            com.tmp.production.domain.ProductionCancellation cancellation =
                    byOrder.get(sourceOrderId.value());
            return cancellation != null && cancellation.posted();
        }
    }

    private static final class StubDocumentEngine implements DocumentEngine {

        private final DocumentProcessor processor;
        private final Map<UUID, DocumentMetadata> documents = new ConcurrentHashMap<>();

        StubDocumentEngine(DocumentProcessor processor) {
            this.processor = processor;
        }

        @Override
        public DocumentProcessorRegistration registerProcessor(DocumentProcessor processor) {
            return new DocumentProcessorRegistration() {
                @Override
                public String documentTypeId() {
                    return processor.documentTypeId();
                }

                @Override
                public void unregister() {}

                @Override
                public void deactivate() {}
            };
        }

        @Override
        public DocumentMetadata createDocument(CreateDocumentCommand command) {
            DocumentMetadata draft =
                    new DocumentMetadata(
                            UUID.randomUUID(),
                            command.documentTypeId(),
                            "PC-STUB",
                            command.title(),
                            DocumentStatus.DRAFT,
                            0L,
                            Instant.now(),
                            Instant.now(),
                            null,
                            null);
            documents.put(draft.id(), draft);
            return draft;
        }

        @Override
        public DocumentMetadata updateDocument(UpdateDocumentCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DocumentMetadata postDocument(UUID documentId) {
            DocumentMetadata current = documents.get(documentId);
            processor.onPost(() -> current);
            DocumentMetadata posted =
                    new DocumentMetadata(
                            current.id(),
                            current.documentTypeId(),
                            current.documentNumber(),
                            current.title(),
                            DocumentStatus.POSTED,
                            current.version() + 1,
                            current.createdAt(),
                            Instant.now(),
                            Instant.now(),
                            null);
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
        public void deleteDocument(UUID documentId) {
            documents.remove(documentId);
        }

        @Override
        public Optional<DocumentMetadata> findById(UUID documentId) {
            return Optional.ofNullable(documents.get(documentId));
        }

        @Override
        public List<DocumentMetadata> search(DocumentQuery query) {
            return List.copyOf(documents.values());
        }

        @Override
        public List<DocumentTypeDescriptor> registeredTypes() {
            return List.of();
        }

        @Override
        public DocumentEngineStatus status() {
            return new DocumentEngineStatus(1, documents.size());
        }
    }

    private static final class PassthroughTransactionManager
            implements org.springframework.transaction.PlatformTransactionManager {

        @Override
        public org.springframework.transaction.TransactionStatus getTransaction(
                TransactionDefinition definition) throws TransactionException {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(org.springframework.transaction.TransactionStatus status)
                throws TransactionException {}

        @Override
        public void rollback(org.springframework.transaction.TransactionStatus status)
                throws TransactionException {}
    }
}
