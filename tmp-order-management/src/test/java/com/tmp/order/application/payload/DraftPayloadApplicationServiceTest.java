package com.tmp.order.application.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.tmp.order.domain.CurrencyCode;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderDirection;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.PayloadRevision;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DraftPayloadApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-25T13:00:00Z");

    private FakeDocumentEngine documents;
    private InMemoryOrderDocumentPayloadPort port;
    private DraftPayloadApplicationService useCases;

    @BeforeEach
    void setUp() {
        documents = new FakeDocumentEngine();
        port = new InMemoryOrderDocumentPayloadPort();
        useCases = new DraftPayloadApplicationService(documents, port);
    }

    @Test
    void createDraftStoresPayloadForDraftDocument() {
        DocumentId documentId = documents.register(DocumentStatus.DRAFT);
        OrderCreatePayload payload =
                OrderCreatePayload.create(documentId, orderNumber(), commercialData(), NOW);

        OrderDocumentPayload stored = useCases.createDraft(payload);

        assertEquals(documentId, stored.documentId());
        assertEquals(PayloadRevision.initial(), stored.identity().payloadRevision());
        assertTrue(useCases.load(documentId).isPresent());
    }

    @Test
    void updateWithMatchingPayloadRevisionSucceedsAndIncrements() {
        DocumentId documentId = documents.register(DocumentStatus.DRAFT);
        OrderCreatePayload created =
                OrderCreatePayload.create(documentId, orderNumber(), commercialData(), NOW);
        useCases.createDraft(created);

        OrderCommercialData updated =
                OrderCommercialData.of(
                        "C-9",
                        "Renamed",
                        null,
                        null,
                        null,
                        OrderDirection.PRIVATE,
                        CurrencyCode.of("USD"));
        OrderCreatePayload candidate = created.withCommercialData(updated, NOW.plusSeconds(5));

        OrderDocumentPayload result =
                useCases.updateDraft(candidate, PayloadRevision.initial());

        assertEquals(PayloadRevision.of(1L), result.identity().payloadRevision());
        assertEquals("Renamed", ((OrderCreatePayload) result).commercialData().customerName());
    }

    @Test
    void optimisticLockConflictIsRejectedExplicitly() {
        DocumentId documentId = documents.register(DocumentStatus.DRAFT);
        OrderCreatePayload created =
                OrderCreatePayload.create(documentId, orderNumber(), commercialData(), NOW);
        useCases.createDraft(created);

        OrderCreatePayload first =
                created.withCommercialData(
                        OrderCommercialData.of(
                                "A",
                                "First",
                                null,
                                null,
                                null,
                                OrderDirection.DEALER,
                                CurrencyCode.of("EUR")),
                        NOW.plusSeconds(1));
        useCases.updateDraft(first, PayloadRevision.initial());

        OrderCreatePayload stale =
                created.withCommercialData(
                        OrderCommercialData.of(
                                "B",
                                "Stale",
                                null,
                                null,
                                null,
                                OrderDirection.CORPORATE,
                                CurrencyCode.of("USD")),
                        NOW.plusSeconds(2));

        PayloadOptimisticLockException conflict =
                assertThrows(
                        PayloadOptimisticLockException.class,
                        () -> useCases.updateDraft(stale, PayloadRevision.initial()));
        assertEquals(documentId, conflict.documentId());
        assertEquals(PayloadRevision.initial(), conflict.expected());
        assertEquals(PayloadRevision.of(1L), conflict.actual());
    }

    @Test
    void nonDraftDocumentCannotBeEdited() {
        DocumentId posted = documents.register(DocumentStatus.POSTED);
        OrderCreatePayload payload =
                OrderCreatePayload.create(posted, orderNumber(), commercialData(), NOW);
        assertThrows(NonDraftPayloadEditException.class, () -> useCases.createDraft(payload));

        DocumentId closed = documents.register(DocumentStatus.CLOSED);
        OrderCreatePayload closedPayload =
                OrderCreatePayload.create(closed, orderNumber(), commercialData(), NOW);
        assertThrows(NonDraftPayloadEditException.class, () -> useCases.createDraft(closedPayload));

        DocumentId draft = documents.register(DocumentStatus.DRAFT);
        OrderCreatePayload draftPayload =
                OrderCreatePayload.create(draft, orderNumber(), commercialData(), NOW);
        useCases.createDraft(draftPayload);
        documents.setStatus(draft, DocumentStatus.POSTED);

        OrderCreatePayload attempt =
                draftPayload.withCommercialData(commercialData(), NOW.plusSeconds(1));
        assertThrows(
                NonDraftPayloadEditException.class,
                () -> useCases.updateDraft(attempt, PayloadRevision.initial()));
    }

    @Test
    void deleteDraftRemovesPayloadOnlyWhileDocumentIsDraft() {
        DocumentId documentId = documents.register(DocumentStatus.DRAFT);
        useCases.createDraft(
                OrderCreatePayload.create(documentId, orderNumber(), commercialData(), NOW));

        useCases.deleteDraft(documentId);
        assertFalse(port.existsByDocumentId(documentId));
        assertTrue(useCases.load(documentId).isEmpty());

        DocumentId posted = documents.register(DocumentStatus.DRAFT);
        useCases.createDraft(
                OrderCreatePayload.create(posted, orderNumber(), commercialData(), NOW));
        documents.setStatus(posted, DocumentStatus.POSTED);
        assertThrows(NonDraftPayloadEditException.class, () -> useCases.deleteDraft(posted));
        assertTrue(port.existsByDocumentId(posted));
    }

    @Test
    void loadDraftRejectsPostedDocument() {
        DocumentId documentId = documents.register(DocumentStatus.DRAFT);
        useCases.createDraft(
                OrderCreatePayload.create(documentId, orderNumber(), commercialData(), NOW));
        documents.setStatus(documentId, DocumentStatus.POSTED);
        assertThrows(NonDraftPayloadEditException.class, () -> useCases.loadDraft(documentId));
        assertTrue(useCases.load(documentId).isPresent());
    }

    private static OrderNumber orderNumber() {
        return OrderNumber.of("PR-2026-000100");
    }

    private static OrderCommercialData commercialData() {
        return OrderCommercialData.of(
                "C-1",
                "Customer",
                null,
                null,
                null,
                OrderDirection.PRIVATE,
                CurrencyCode.of("USD"));
    }

    /**
     * Minimal Document Engine test double exposing only {@link #findById(UUID)} used by draft use
     * cases.
     */
    private static final class FakeDocumentEngine implements DocumentEngine {

        private final Map<UUID, DocumentStatus> statuses = new ConcurrentHashMap<>();

        DocumentId register(DocumentStatus status) {
            UUID id = UUID.randomUUID();
            statuses.put(id, status);
            return DocumentId.of(id);
        }

        void setStatus(DocumentId documentId, DocumentStatus status) {
            statuses.put(documentId.value(), status);
        }

        @Override
        public Optional<DocumentMetadata> findById(UUID documentId) {
            DocumentStatus status = statuses.get(documentId);
            if (status == null) {
                return Optional.empty();
            }
            Instant now = NOW;
            return Optional.of(
                    new DocumentMetadata(
                            documentId,
                            "ORDER_CREATE",
                            "DOC-" + documentId,
                            "title",
                            status,
                            0L,
                            now,
                            now,
                            status == DocumentStatus.POSTED || status == DocumentStatus.CLOSED
                                    ? now
                                    : null,
                            status == DocumentStatus.CLOSED ? now : null));
        }

        @Override
        public DocumentProcessorRegistration registerProcessor(DocumentProcessor processor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DocumentMetadata createDocument(CreateDocumentCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DocumentMetadata updateDocument(UpdateDocumentCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DocumentMetadata postDocument(UUID documentId) {
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DocumentMetadata> search(DocumentQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DocumentTypeDescriptor> registeredTypes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DocumentEngineStatus status() {
            throw new UnsupportedOperationException();
        }
    }
}
