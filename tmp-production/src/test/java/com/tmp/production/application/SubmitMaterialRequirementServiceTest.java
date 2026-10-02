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

import com.tmp.production.domain.CuttingPlanLinks;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementCoverageConflictException;
import com.tmp.production.domain.MaterialRequirementId;
import com.tmp.production.domain.MaterialRequirementLine;
import com.tmp.production.domain.MaterialRequirementLineContribution;
import com.tmp.production.domain.MaterialRequirementOptimisticLockException;
import com.tmp.production.domain.MaterialRequirementShortageException;
import com.tmp.production.domain.MaterialRequirementSourceItem;
import com.tmp.production.domain.MaterialRequirementSourceItemKey;
import com.tmp.production.domain.MaterialRequirementStatus;
import com.tmp.production.domain.MaterialRequirementSubmissionCorruptedException;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.MaterialRequirementRepository;
import com.tmp.production.domain.repository.MaterialRequirementSubmissionRepository;
import com.tmp.production.domain.repository.MaterialRequirementSubmissionRepository.GeneratedDocumentLink;
import com.tmp.production.domain.repository.MaterialRequirementSubmissionRepository.RoutingSnapshotRow;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class SubmitMaterialRequirementServiceTest {

    private static final Instant T0 = Instant.parse("2026-09-10T10:00:00Z");
    private static final UUID DEST = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID SOURCE = UUID.fromString("00000000-0000-4000-8000-000000000003");

    private InMemoryItemRepository itemStates;
    private InMemoryRequirementRepository requirements;
    private InMemorySubmissionRepository submissions;
    private WarehouseDemandCommandApi warehouseDemand;
    private SubmitMaterialRequirementService service;
    private MaterialRequirement draft;

    @BeforeEach
    void setUp() {
        itemStates = new InMemoryItemRepository();
        requirements = new InMemoryRequirementRepository();
        submissions = new InMemorySubmissionRepository();
        warehouseDemand = Mockito.mock(WarehouseDemandCommandApi.class);
        ProductionOrderViewService orderViewService = new ProductionOrderViewService(itemStates);
        MaterialRequirementCoverageService coverageService =
                new MaterialRequirementCoverageService(orderViewService, requirements);
        service =
                new SubmitMaterialRequirementService(
                        requirements,
                        submissions,
                        warehouseDemand,
                        orderViewService,
                        coverageService,
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

    @Test
    void submittedCoverageSequentialReducesRequestableUntilExhausted() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        itemStates.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(
                                orderId, itemId, SpecificationId.generate(), T0),
                        ProductionQuantity.positive(10),
                        T0,
                        CuttingPlanLinks.empty()));
        MaterialRequirementSourceItemKey key =
                MaterialRequirementSourceItemKey.of(orderId, itemId);

        MaterialRequirement first = requirements.save(draftFor(orderId, itemId, 4L, "10"));
        when(warehouseDemand.createRoutedTransferDocuments(any()))
                .thenReturn(successResult(first, UUID.randomUUID(), UUID.randomUUID(), "10", "0"));
        service.submit(first.requirementId(), 0L, "user-1");
        assertEquals(
                4L,
                requirements.sumSubmittedProductQuantities(List.of(key)).getOrDefault(key, 0L));

        ProductionItemState state =
                itemStates.findBySourceOrderId(orderId).getFirst();
        assertEquals(
                6L,
                new MaterialRequirementCoverageService(
                                new ProductionOrderViewService(itemStates), requirements)
                        .coverageFor(state)
                        .requestableProductQuantity());

        MaterialRequirement second = requirements.save(draftFor(orderId, itemId, 6L, "10"));
        when(warehouseDemand.createRoutedTransferDocuments(any()))
                .thenReturn(successResult(second, UUID.randomUUID(), UUID.randomUUID(), "10", "0"));
        service.submit(second.requirementId(), 0L, "user-1");
        assertEquals(
                10L,
                requirements.sumSubmittedProductQuantities(List.of(key)).getOrDefault(key, 0L));
        assertEquals(
                0L,
                new MaterialRequirementCoverageService(
                                new ProductionOrderViewService(itemStates), requirements)
                        .coverageFor(itemStates.findBySourceOrderId(orderId).getFirst())
                        .requestableProductQuantity());

        MaterialRequirement third = requirements.save(draftFor(orderId, itemId, 1L, "1"));
        assertThrows(
                MaterialRequirementCoverageConflictException.class,
                () -> service.submit(third.requirementId(), 0L, "user-1"));
    }

    @Test
    void twoDraftsDoNotReserveCoverageAndSecondSubmitConflicts() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        itemStates.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(
                                orderId, itemId, SpecificationId.generate(), T0),
                        ProductionQuantity.positive(10),
                        T0,
                        CuttingPlanLinks.empty()));
        MaterialRequirementSourceItemKey key =
                MaterialRequirementSourceItemKey.of(orderId, itemId);

        MaterialRequirement draftA = requirements.save(draftFor(orderId, itemId, 6L, "10"));
        MaterialRequirement draftB = requirements.save(draftFor(orderId, itemId, 6L, "10"));
        assertEquals(
                0L,
                requirements.sumSubmittedProductQuantities(List.of(key)).getOrDefault(key, 0L));

        when(warehouseDemand.createRoutedTransferDocuments(any()))
                .thenReturn(successResult(draftA, UUID.randomUUID(), UUID.randomUUID(), "10", "0"));
        service.submit(draftA.requirementId(), 0L, "user-1");

        assertThrows(
                MaterialRequirementCoverageConflictException.class,
                () -> service.submit(draftB.requirementId(), 0L, "user-2"));
        assertEquals(MaterialRequirementStatus.DRAFT, draftB.status());
        assertEquals(
                MaterialRequirementStatus.DRAFT,
                requirements.findById(draftB.requirementId()).orElseThrow().status());
    }

    @Test
    void submitUsesEditedMaterialQuantityAsWarehouseDemand() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        itemStates.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(
                                orderId, itemId, SpecificationId.generate(), T0),
                        ProductionQuantity.positive(10),
                        T0,
                        CuttingPlanLinks.empty()));
        MaterialRequirement calculated =
                requirements.save(draftFor(orderId, itemId, 10L, "40"));
        MaterialRequirement edited =
                calculated.changeLineQuantity(
                        calculated.lines().getFirst().lineId(),
                        new BigDecimal("42"),
                        T0);
        MaterialRequirement persisted = requirements.save(edited);
        assertEquals(10L, persisted.sourceItems().getFirst().requestedProductQuantity());
        assertEquals(0, persisted.lines().getFirst().quantity().compareTo(new BigDecimal("42")));

        when(warehouseDemand.createRoutedTransferDocuments(any()))
                .thenReturn(
                        successResult(persisted, UUID.randomUUID(), UUID.randomUUID(), "42", "0"));

        service.submit(persisted.requirementId(), persisted.version(), "user-1");

        ArgumentCaptor<WarehouseDemandCommandApi.CreateRoutedTransferCommand> captor =
                ArgumentCaptor.forClass(WarehouseDemandCommandApi.CreateRoutedTransferCommand.class);
        verify(warehouseDemand).createRoutedTransferDocuments(captor.capture());
        assertEquals(0, new BigDecimal("42").compareTo(captor.getValue().lines().getFirst().quantity()));
    }

    @Test
    void modeChangeDoesNotChangeFrozenDraftProductQuantityOnSubmit() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        itemStates.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(
                                orderId, itemId, SpecificationId.generate(), T0),
                        ProductionQuantity.positive(10),
                        T0,
                        CuttingPlanLinks.empty()));
        MaterialRequirement draft = requirements.save(draftFor(orderId, itemId, 4L, "16"));
        when(warehouseDemand.createRoutedTransferDocuments(any()))
                .thenReturn(successResult(draft, UUID.randomUUID(), UUID.randomUUID(), "16", "0"));

        SubmitMaterialRequirementResult result =
                service.submit(draft.requirementId(), 0L, "user-1");

        assertEquals(4L, result.requirement().sourceItems().getFirst().requestedProductQuantity());
        assertTrue(result.created());
    }

    private MaterialRequirement draftFor(
            SourceOrderId orderId, SourceOrderItemId itemId, long productQty, String materialQty) {
        BigDecimal qty = new BigDecimal(materialQty);
        return MaterialRequirement.create(
                DEST,
                T0,
                List.of(MaterialRequirementSourceItem.of(orderId, itemId, productQty)),
                List.of(
                        MaterialRequirementLine.create(
                                MaterialReferenceId.generate(),
                                "MAT-1",
                                "Material",
                                "WHITE",
                                "M",
                                qty,
                                List.of(
                                        MaterialRequirementLineContribution.of(
                                                orderId, itemId, qty)))));
    }

    private MaterialRequirement newDraft() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        itemStates.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(
                                orderId, itemId, SpecificationId.generate(), T0),
                        ProductionQuantity.positive(1),
                        T0,
                        CuttingPlanLinks.empty()));
        BigDecimal materialQty = new BigDecimal("100");
        return MaterialRequirement.create(
                DEST,
                T0,
                List.of(MaterialRequirementSourceItem.of(orderId, itemId, 1L)),
                List.of(
                        MaterialRequirementLine.create(
                                MaterialReferenceId.generate(),
                                "MAT-1",
                                "Material",
                                "WHITE",
                                "PCS",
                                materialQty,
                                List.of(
                                        MaterialRequirementLineContribution.of(
                                                orderId, itemId, materialQty)))));
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

    private static final class InMemoryItemRepository implements ProductionItemStateRepository {
        private final Map<String, ProductionItemState> store = new ConcurrentHashMap<>();

        @Override
        public ProductionItemState save(ProductionItemState state) {
            store.put(
                    state.sourceOrderId()
                            + ":"
                            + state.sourceOrderItemId()
                            + ":"
                            + state.specificationId(),
                    state);
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
                    .toList();
        }
    }

    private static final class InMemoryRequirementRepository implements MaterialRequirementRepository {
        private final Map<MaterialRequirementId, MaterialRequirement> store = new ConcurrentHashMap<>();

        @Override
        public MaterialRequirement save(MaterialRequirement requirement) {
            MaterialRequirement existing = store.get(requirement.requirementId());
            MaterialRequirement saved =
                    MaterialRequirement.rehydrate(
                            requirement.requirementId(),
                            requirement.destinationWarehouseId(),
                            requirement.createdAt(),
                            requirement.updatedAt(),
                            existing == null ? 0L : requirement.version() + 1,
                            requirement.status(),
                            requirement.submittedAt().orElse(null),
                            requirement.submittedBy().orElse(null),
                            requirement.sourceItems(),
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

        @Override
        public Map<MaterialRequirementSourceItemKey, Long> sumSubmittedProductQuantities(
                Collection<MaterialRequirementSourceItemKey> keys) {
            Map<MaterialRequirementSourceItemKey, Long> sums = new LinkedHashMap<>();
            for (MaterialRequirement requirement : store.values()) {
                if (requirement.status() != MaterialRequirementStatus.SUBMITTED) {
                    continue;
                }
                for (MaterialRequirementSourceItem item : requirement.sourceItems()) {
                    MaterialRequirementSourceItemKey key =
                            MaterialRequirementSourceItemKey.of(
                                    item.sourceOrderId(), item.sourceOrderItemId());
                    if (keys.contains(key)) {
                        sums.merge(key, item.requestedProductQuantity(), Long::sum);
                    }
                }
            }
            return sums;
        }

        @Override
        public List<MaterialRequirement> findByIds(Collection<MaterialRequirementId> ids) {
            return ids.stream().map(store::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public List<MaterialRequirement> findBySourceOrderItemIds(
                Collection<SourceOrderItemId> itemIds) {
            Set<SourceOrderItemId> wanted = Set.copyOf(itemIds);
            return store.values().stream()
                    .filter(
                            requirement ->
                                    requirement.sourceItems().stream()
                                            .anyMatch(
                                                    item ->
                                                            wanted.contains(
                                                                    item.sourceOrderItemId())))
                    .toList();
        }

        @Override
        public List<MaterialRequirement> findDraftsNewestFirst() {
            return store.values().stream()
                    .filter(requirement -> requirement.status() == MaterialRequirementStatus.DRAFT)
                    .sorted(
                            (a, b) -> {
                                int byCreated = b.createdAt().compareTo(a.createdAt());
                                if (byCreated != 0) {
                                    return byCreated;
                                }
                                return b.requirementId()
                                        .value()
                                        .compareTo(a.requirementId().value());
                            })
                    .toList();
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
