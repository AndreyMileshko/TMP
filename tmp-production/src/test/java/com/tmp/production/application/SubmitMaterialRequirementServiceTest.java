package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementId;
import com.tmp.production.domain.MaterialRequirementLine;
import com.tmp.production.domain.MaterialRequirementOptimisticLockException;
import com.tmp.production.domain.MaterialRequirementShortageException;
import com.tmp.production.domain.MaterialRequirementStatus;
import com.tmp.production.domain.MaterialRequirementSubmissionCorruptedException;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.repository.MaterialRequirementRepository;
import com.tmp.production.domain.repository.MaterialRequirementSubmissionRepository;
import com.tmp.production.domain.repository.MaterialRequirementSubmissionRepository.GeneratedDocumentLink;
import com.tmp.production.domain.repository.MaterialRequirementSubmissionRepository.RoutingSnapshotRow;
import com.tmp.warehouse.api.DemandSourceUnavailableException;
import com.tmp.warehouse.api.DemandSourceUnavailableException.UnavailableDemand;
import com.tmp.warehouse.api.WarehouseDemandCommandApi;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.GeneratedDocument;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.RoutedLine;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.RoutedTransferResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class SubmitMaterialRequirementServiceTest {

    private static final Instant T0 = Instant.parse("2026-09-10T10:00:00Z");
    private static final UUID DEST = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID SOURCE = UUID.fromString("00000000-0000-4000-8000-000000000003");

    private InMemoryRequirementRepository requirements;
    private InMemorySubmissionRepository submissions;
    private WarehouseDemandCommandApi warehouseDemand;
    private SubmitMaterialRequirementService service;
    private MaterialRequirement draft;

    @BeforeEach
    void setUp() {
        requirements = new InMemoryRequirementRepository();
        submissions = new InMemorySubmissionRepository();
        warehouseDemand = Mockito.mock(WarehouseDemandCommandApi.class);
        service =
                new SubmitMaterialRequirementService(
                        requirements,
                        submissions,
                        warehouseDemand,
                        new NoopTransactionManager(),
                        Clock.fixed(T0, ZoneOffset.UTC));
        draft = requirements.save(newDraft());
    }

    @Test
    void firstSubmitRoutesCreatesDocumentsAndMarksSubmitted() {
        UUID documentId = UUID.randomUUID();
        UUID transferLineId = UUID.randomUUID();
        when(warehouseDemand.createRoutedTransferDocuments(any()))
                .thenReturn(successResult(draft, documentId, transferLineId, "60", "40"));

        SubmitMaterialRequirementResult result = service.submit(draft.requirementId(), 0L, "user-1");

        assertTrue(result.created());
        assertEquals(MaterialRequirementStatus.SUBMITTED, result.requirement().status());
        assertEquals(Optional.of("user-1"), result.requirement().submittedBy());
        assertEquals(1L, result.requirement().version());
        assertEquals(1, result.documents().size());
        assertEquals(documentId, result.documents().getFirst().warehouseDocumentId());
        assertEquals(1, result.routing().size());
        assertEquals(0, new BigDecimal("60").compareTo(result.routing().getFirst().routedQuantity()));
        assertEquals(0, new BigDecimal("40").compareTo(result.routing().getFirst().uncoveredQuantity()));
        verify(warehouseDemand, times(1)).createRoutedTransferDocuments(any());
        assertThrows(
                IllegalStateException.class,
                () -> result.requirement().changeLineQuantity(draft.lines().getFirst().lineId(), BigDecimal.ONE, T0));
    }

    @Test
    void staleDraftVersionIsRejectedBeforeRouting() {
        assertThrows(
                MaterialRequirementOptimisticLockException.class,
                () -> service.submit(draft.requirementId(), 5L, "user-1"));
        verify(warehouseDemand, never()).createRoutedTransferDocuments(any());
        assertEquals(MaterialRequirementStatus.DRAFT, requirements.findById(draft.requirementId()).orElseThrow().status());
        assertTrue(submissions.documents.isEmpty());
    }

    @Test
    void shortageFailsClosedWithoutPersistingSubmission() {
        when(warehouseDemand.createRoutedTransferDocuments(any()))
                .thenThrow(
                        new DemandSourceUnavailableException(
                                List.of(
                                        new UnavailableDemand(
                                                draft.lines().getFirst().lineId().value().toString(),
                                                draft.lines().getFirst().materialReferenceId().value()))));

        MaterialRequirementShortageException ex =
                assertThrows(
                        MaterialRequirementShortageException.class,
                        () -> service.submit(draft.requirementId(), 0L, "user-1"));
        assertEquals(1, ex.shortages().size());
        assertEquals("MAT-1", ex.shortages().getFirst().materialCode());
        assertEquals(MaterialRequirementStatus.DRAFT, requirements.findById(draft.requirementId()).orElseThrow().status());
        assertTrue(submissions.documents.isEmpty());
        assertTrue(submissions.snapshots.isEmpty());
    }

    @Test
    void submittedRetryReturnsPersistedResultWithoutReroute() {
        UUID documentId = UUID.randomUUID();
        when(warehouseDemand.createRoutedTransferDocuments(any()))
                .thenReturn(successResult(draft, documentId, UUID.randomUUID(), "10", "0"));
        service.submit(draft.requirementId(), 0L, "user-1");

        SubmitMaterialRequirementResult retry =
                service.submit(draft.requirementId(), 0L, "other-user");

        assertFalse(retry.created());
        assertEquals(MaterialRequirementStatus.SUBMITTED, retry.requirement().status());
        assertEquals(documentId, retry.documents().getFirst().warehouseDocumentId());
        verify(warehouseDemand, times(1)).createRoutedTransferDocuments(any());
    }

    @Test
    void submittedWithoutLinksFailsClosed() {
        MaterialRequirement submitted = draft.submit("user-1", T0);
        requirements.store.put(submitted.requirementId(), submitted);

        assertThrows(
                MaterialRequirementSubmissionCorruptedException.class,
                () -> service.submit(submitted.requirementId(), 99L, "user-1"));
        verify(warehouseDemand, never()).createRoutedTransferDocuments(any());
    }

    private static MaterialRequirement newDraft() {
        return MaterialRequirement.create(
                SourceOrderId.generate(),
                DEST,
                T0,
                List.of(
                        MaterialRequirementLine.create(
                                MaterialReferenceId.generate(),
                                "MAT-1",
                                "Material",
                                "WHITE",
                                "PCS",
                                new BigDecimal("100"),
                                Set.of(SourceOrderItemId.generate()))));
    }

    private static RoutedTransferResult successResult(
            MaterialRequirement requirement,
            UUID documentId,
            UUID transferLineId,
            String routed,
            String uncovered) {
        MaterialRequirementLine line = requirement.lines().getFirst();
        return new RoutedTransferResult(
                List.of(new GeneratedDocument(documentId, SOURCE, DEST)),
                List.of(
                        new RoutedLine(
                                line.lineId().value().toString(),
                                line.materialReferenceId().value(),
                                SOURCE,
                                "SRC-A",
                                new BigDecimal(routed),
                                new BigDecimal(routed),
                                new BigDecimal(uncovered),
                                documentId,
                                transferLineId)));
    }

    private static final class InMemoryRequirementRepository implements MaterialRequirementRepository {
        private final Map<MaterialRequirementId, MaterialRequirement> store = new ConcurrentHashMap<>();

        @Override
        public MaterialRequirement save(MaterialRequirement requirement) {
            MaterialRequirement existing = store.get(requirement.requirementId());
            MaterialRequirement saved =
                    MaterialRequirement.rehydrate(
                            requirement.requirementId(),
                            requirement.sourceOrderId(),
                            requirement.destinationWarehouseId(),
                            requirement.createdAt(),
                            requirement.updatedAt(),
                            existing == null ? 0L : requirement.version() + 1,
                            requirement.status(),
                            requirement.submittedAt().orElse(null),
                            requirement.submittedBy().orElse(null),
                            requirement.lines());
            store.put(saved.requirementId(), saved);
            return saved;
        }

        @Override
        public Optional<MaterialRequirement> findById(MaterialRequirementId id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<MaterialRequirement> findByIdForUpdate(MaterialRequirementId id) {
            return findById(id);
        }

        @Override
        public MaterialRequirement markSubmitted(MaterialRequirement requirement) {
            return save(requirement);
        }
    }

    private static final class InMemorySubmissionRepository implements MaterialRequirementSubmissionRepository {
        private final Map<MaterialRequirementId, List<GeneratedDocumentLink>> documents = new ConcurrentHashMap<>();
        private final Map<MaterialRequirementId, List<RoutingSnapshotRow>> snapshots = new ConcurrentHashMap<>();

        @Override
        public void saveGeneratedDocuments(
                MaterialRequirementId requirementId, List<GeneratedDocumentLink> docs) {
            documents.put(requirementId, List.copyOf(docs));
        }

        @Override
        public void saveRoutingSnapshot(
                MaterialRequirementId requirementId, List<RoutingSnapshotRow> snapshot) {
            snapshots.put(requirementId, List.copyOf(snapshot));
        }

        @Override
        public List<GeneratedDocumentLink> findGeneratedDocuments(MaterialRequirementId requirementId) {
            return documents.getOrDefault(requirementId, List.of());
        }

        @Override
        public List<RoutingSnapshotRow> findRoutingSnapshot(MaterialRequirementId requirementId) {
            return snapshots.getOrDefault(requirementId, List.of());
        }
    }

    private static final class NoopTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() throws TransactionException {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition)
                throws TransactionException {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) throws TransactionException {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) throws TransactionException {}
    }
}
