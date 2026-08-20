package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import com.tmp.production.application.document.ProductionLaunchPayload;
import com.tmp.production.application.document.ProductionLaunchPayloadHolder;
import com.tmp.production.application.port.OrderForProductionQueryPort;
import com.tmp.production.application.port.OrderForProductionQueryPort.ResolvedItemLine;
import com.tmp.production.application.port.OrderForProductionQueryPort.ResolvedOrderForLaunch;
import com.tmp.production.domain.OrderNotEligibleForProductionException;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
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

    private StubOrderForProductionQuery orderQuery;
    private ProductionLaunchPayloadHolder payloadHolder;
    private ProductionLaunchPayload lastPayload;
    private StubDocumentEngine documentEngine;
    private ProductionLaunchService service;

    @BeforeEach
    void setUp() {
        orderQuery = new StubOrderForProductionQuery();
        payloadHolder =
                org.mockito.Mockito.spy(new ProductionLaunchPayloadHolder());
        org.mockito.Mockito.doAnswer(
                        invocation -> {
                            lastPayload = invocation.getArgument(1);
                            return invocation.callRealMethod();
                        })
                .when(payloadHolder)
                .set(
                        org.mockito.ArgumentMatchers.any(UUID.class),
                        org.mockito.ArgumentMatchers.any(ProductionLaunchPayload.class));
        documentEngine = new StubDocumentEngine();
        service =
                new ProductionLaunchService(
                        documentEngine,
                        payloadHolder,
                        orderQuery,
                        Clock.fixed(T0, ZoneOffset.UTC));
    }

    @Test
    void launchBuildsMultiLinePayloadFromOrderManagement() {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        SpecificationId specId = SpecificationId.generate();
        orderQuery.order =
                new ResolvedOrderForLaunch(
                        SourceOrderId.of(orderId),
                        OrderStatus.ACTIVE,
                        1,
                        List.of(new ResolvedItemLine(SourceOrderItemId.of(itemId), specId, BigDecimal.TEN)));

        DocumentMetadata posted =
                service.launch(new LaunchProductionCommand(orderId, "operator"));

        assertEquals(DocumentStatus.POSTED, posted.status());
        assertEquals(1, lastPayload.lines().size());
        assertEquals(specId, lastPayload.lines().getFirst().foundation().specificationId());
        assertEquals(SourceOrderId.of(orderId), lastPayload.sourceOrderId());
        assertEquals(SourceOrderItemId.of(itemId), lastPayload.lines().getFirst().foundation().sourceOrderItemId());
        assertEquals(T0, lastPayload.launchTimestamp());
    }

    @Test
    void launchFailsWhenOrderMissing() {
        assertThrows(
                OrderNotEligibleForProductionException.class,
                () -> service.launch(new LaunchProductionCommand(UUID.randomUUID(), "op")));
    }

    @Test
    void launchFailsWhenOrderNotActive() {
        UUID orderId = UUID.randomUUID();
        orderQuery.order =
                new ResolvedOrderForLaunch(
                        SourceOrderId.of(orderId),
                        OrderStatus.CANCELLED,
                        1,
                        List.of(
                                new ResolvedItemLine(
                                        SourceOrderItemId.generate(),
                                        SpecificationId.generate(),
                                        BigDecimal.ONE)));

        assertThrows(
                OrderNotEligibleForProductionException.class,
                () -> service.launch(new LaunchProductionCommand(orderId, "op")));
    }

    private static final class StubOrderForProductionQuery implements OrderForProductionQueryPort {

        ResolvedOrderForLaunch order;

        @Override
        public Optional<ResolvedOrderForLaunch> resolveForLaunch(SourceOrderId sourceOrderId) {
            if (order == null || !order.sourceOrderId().equals(sourceOrderId)) {
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
