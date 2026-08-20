package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentStatus;
import com.tmp.production.application.document.ProductionLaunchPayload;
import com.tmp.production.application.document.ProductionLaunchPayloadHolder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import com.tmp.production.application.port.OrderSpecificationQueryPort;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedSpecification;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.SpecificationNotAvailableForLaunchException;
import com.tmp.document.api.DocumentEngineStatus;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.DocumentQuery;
import com.tmp.document.api.DocumentTypeDescriptor;
import com.tmp.document.api.UpdateDocumentCommand;
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

class ProductionLaunchServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-19T10:00:00Z");

    private StubSpecificationQuery specificationQuery;
    private ProductionLaunchPayloadHolder payloadHolder;
    private ProductionLaunchPayload lastPayload;
    private StubDocumentEngine documentEngine;
    private ProductionLaunchService service;

    @BeforeEach
    void setUp() {
        specificationQuery = new StubSpecificationQuery();
        payloadHolder = spy(new ProductionLaunchPayloadHolder());
        doAnswer(
                        invocation -> {
                            lastPayload = invocation.getArgument(1);
                            return invocation.callRealMethod();
                        })
                .when(payloadHolder)
                .set(any(UUID.class), any(ProductionLaunchPayload.class));
        documentEngine = new StubDocumentEngine();
        service =
                new ProductionLaunchService(
                        documentEngine,
                        payloadHolder,
                        specificationQuery,
                        Clock.fixed(T0, ZoneOffset.UTC));
    }

    @Test
    void launchFreezesCurrentSpecificationFromOrderManagement() {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        SpecificationId specId = SpecificationId.generate();
        specificationQuery.currentSpec =
                Optional.of(
                        new ResolvedSpecification(
                                specId,
                                SourceOrderItemId.of(itemId),
                                BigDecimal.TEN,
                                List.of(
                                        new ResolvedMaterialLine(
                                                "MAT-1",
                                                "Panel",
                                                "white",
                                                BigDecimal.valueOf(1000),
                                                BigDecimal.valueOf(2),
                                                "PCS"))));

        DocumentMetadata posted =
                service.launch(new LaunchProductionCommand(orderId, itemId, 10, "operator"));

        assertEquals(DocumentStatus.POSTED, posted.status());
        ProductionLaunchPayload payload = lastPayload;
        assertEquals(specId, payload.foundation().specificationId());
        assertEquals(SourceOrderId.of(orderId), payload.foundation().sourceOrderId());
        assertEquals(SourceOrderItemId.of(itemId), payload.foundation().sourceOrderItemId());
        assertEquals(T0, payload.foundation().frozenAt());
        assertEquals(1, specificationQuery.resolveCurrentCalls);
        assertEquals(0, specificationQuery.resolveByIdCalls);
    }

    @Test
    void launchFailsWhenCurrentSpecificationMissing() {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        assertThrows(
                SpecificationNotAvailableForLaunchException.class,
                () -> service.launch(new LaunchProductionCommand(orderId, itemId, 5, "op")));
    }

    @Test
    void launchCommandNoLongerRequiresSpecificationId() {
        LaunchProductionCommand command =
                new LaunchProductionCommand(UUID.randomUUID(), UUID.randomUUID(), 3, "user");
        assertTrue(command.orderedQuantity() > 0);
    }

    private static final class StubSpecificationQuery implements OrderSpecificationQueryPort {

        Optional<ResolvedSpecification> currentSpec = Optional.empty();
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
            return Optional.empty();
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
                            draft.postedAt(),
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
}
