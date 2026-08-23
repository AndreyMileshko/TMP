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
import com.tmp.production.application.internal.ProductionReleaseDocumentService;
import com.tmp.production.application.internal.ProductionReleaseDocumentService.ProductionReleaseDocumentCommand;
import com.tmp.production.application.document.ProductionReleaseProcessor;
import com.tmp.production.domain.MaterialPlanningSource;
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

class ProductionReleaseDocumentServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-21T16:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-21T17:00:00Z");

    private InMemoryItemRepository items;
    private InMemoryReleaseRepository releases;
    private StubDocumentEngine documentEngine;
    private ProductionReleaseProcessor processor;
    private ProductionReleaseDocumentService service;

    @BeforeEach
    void setUp() {
        items = new InMemoryItemRepository();
        releases = new InMemoryReleaseRepository();
        processor = new ProductionReleaseProcessor(releases, items);
        documentEngine = new StubDocumentEngine(processor);
        service =
                new ProductionReleaseDocumentService(
                        documentEngine, releases, Clock.fixed(T1, ZoneOffset.UTC));
    }

    @Test
    void createDraftAndPostReleasesItemState() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        items.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemId, specId, T0),
                        ProductionQuantity.positive(10),
                        T0));

        DocumentMetadata draft =
                service.createDraft(
                        new ProductionReleaseDocumentCommand(
                                orderId.value(),
                                T1,
                                List.of(
                                        new ProductionReleaseDocumentCommand.ItemLine(
                                                itemId.value(),
                                                specId.value(),
                                                BigDecimal.TEN)),
                                List.of(
                                        new ProductionReleaseDocumentCommand.MaterialLine(
                                                UUID.randomUUID(),
                                                BigDecimal.TEN,
                                                BigDecimal.valueOf(9),
                                                MaterialPlanningSource.SPECIFICATION,
                                                null,
                                                itemId.value(),
                                                null))));
        assertEquals(DocumentStatus.DRAFT, draft.status());

        DocumentMetadata posted = service.post(draft.id());
        assertEquals(DocumentStatus.POSTED, posted.status());

        ProductionItemState state = items.require(orderId, itemId, specId);
        assertEquals(ProductionStatus.RELEASED, state.status());
        ProductionRelease release = service.findReleaseByDocumentId(draft.id()).orElseThrow();
        assertTrue(release.posted());
        assertEquals(1, service.actualMaterialUsages(draft.id()).size());
        assertEquals(
                0,
                BigDecimal.valueOf(9)
                        .compareTo(service.actualMaterialUsages(draft.id()).getFirst().actualQuantity()));
    }

    @Test
    void postFailureKeepsDraftAndUnchangedState() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        items.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemId, specId, T0),
                        ProductionQuantity.positive(3),
                        T0));

        DocumentMetadata draft =
                service.createDraft(
                        new ProductionReleaseDocumentCommand(
                                orderId.value(),
                                T1,
                                List.of(
                                        new ProductionReleaseDocumentCommand.ItemLine(
                                                itemId.value(),
                                                specId.value(),
                                                BigDecimal.valueOf(4))),
                                List.of()));

        assertThrows(ProductionReleaseValidationException.class, () -> service.post(draft.id()));
        assertEquals(DocumentStatus.DRAFT, documentEngine.findById(draft.id()).orElseThrow().status());
        assertEquals(
                ProductionStatus.IN_PRODUCTION, items.require(orderId, itemId, specId).status());
        assertTrue(service.findReleaseByDocumentId(draft.id()).orElseThrow().posted() == false);
    }

    @Test
    void postedPayloadCannotBeUpdated() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        items.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemId, specId, T0),
                        ProductionQuantity.positive(2),
                        T0));
        DocumentMetadata draft =
                service.createDraft(
                        new ProductionReleaseDocumentCommand(
                                orderId.value(),
                                T1,
                                List.of(
                                        new ProductionReleaseDocumentCommand.ItemLine(
                                                itemId.value(),
                                                specId.value(),
                                                BigDecimal.valueOf(2))),
                                List.of()));
        service.post(draft.id());

        assertThrows(
                ProductionReleaseImmutableException.class,
                () ->
                        service.updateDraft(
                                draft.id(),
                                new ProductionReleaseDocumentCommand(
                                        orderId.value(),
                                        T1,
                                        List.of(
                                                new ProductionReleaseDocumentCommand.ItemLine(
                                                        itemId.value(),
                                                        specId.value(),
                                                        BigDecimal.ONE)),
                                        List.of(
                                                new ProductionReleaseDocumentCommand.MaterialLine(
                                                        UUID.randomUUID(),
                                                        BigDecimal.ONE,
                                                        BigDecimal.ONE,
                                                        MaterialPlanningSource.SPECIFICATION,
                                                        null,
                                                        itemId.value(),
                                                        "changed")))));
    }

    @Test
    void unpostUnsupportedThroughDocumentEngine() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        items.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemId, specId, T0),
                        ProductionQuantity.positive(1),
                        T0));
        DocumentMetadata draft =
                service.createDraft(
                        new ProductionReleaseDocumentCommand(
                                orderId.value(),
                                T1,
                                List.of(
                                        new ProductionReleaseDocumentCommand.ItemLine(
                                                itemId.value(),
                                                specId.value(),
                                                BigDecimal.ONE)),
                                List.of()));
        service.post(draft.id());
        assertThrows(UnsupportedOperationException.class, () -> documentEngine.unpostDocument(draft.id()));
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
                            "PR-STUB",
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
                            current.closedAt());
            documents.put(documentId, posted);
            return posted;
        }

        @Override
        public DocumentMetadata unpostDocument(UUID documentId) {
            processor.onUnpost(() -> documents.get(documentId));
            return documents.get(documentId);
        }

        @Override
        public DocumentMetadata closeDocument(UUID documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteDocument(UUID documentId) {
            throw new UnsupportedOperationException();
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
}
