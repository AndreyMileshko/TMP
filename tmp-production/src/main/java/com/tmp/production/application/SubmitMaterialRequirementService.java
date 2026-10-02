package com.tmp.production.application;

import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementCoverageConflictException;
import com.tmp.production.domain.MaterialRequirementId;
import com.tmp.production.domain.MaterialRequirementLine;
import com.tmp.production.domain.MaterialRequirementLineId;
import com.tmp.production.domain.MaterialRequirementOptimisticLockException;
import com.tmp.production.domain.MaterialRequirementSelectionException;
import com.tmp.production.domain.MaterialRequirementShortageException;
import com.tmp.production.domain.MaterialRequirementShortageException.ShortageLine;
import com.tmp.production.domain.MaterialRequirementSourceItem;
import com.tmp.production.domain.MaterialRequirementSourceItemKey;
import com.tmp.production.domain.MaterialRequirementStatus;
import com.tmp.production.domain.MaterialRequirementSubmissionCorruptedException;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.repository.MaterialRequirementRepository;
import com.tmp.production.domain.repository.MaterialRequirementSubmissionRepository;
import com.tmp.production.domain.repository.MaterialRequirementSubmissionRepository.GeneratedDocumentLink;
import com.tmp.production.domain.repository.MaterialRequirementSubmissionRepository.RoutingSnapshotRow;
import com.tmp.warehouse.api.DemandSourceUnavailableException;
import com.tmp.warehouse.api.WarehouseDemandCommandApi;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.CreateRoutedTransferCommand;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.DemandLine;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.GeneratedDocument;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.RoutedLine;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.RoutedTransferResult;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Production-owned transaction owner for «Отправить требование» (Stage 3.5.10 / Stage 7 Phase 2).
 *
 * <p>One outer REQUIRED transaction: lock requirement → idempotent SUBMITTED short-circuit → verify
 * DRAFT + expectedVersion → lock affected Production item states in deterministic OrderId order →
 * revalidate product coverage → Warehouse demand routing/creation → mark SUBMITTED → persist
 * generated document links + routing snapshot.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators injected by the container.")
public final class SubmitMaterialRequirementService {

    private final MaterialRequirementRepository requirementRepository;
    private final MaterialRequirementSubmissionRepository submissionRepository;
    private final WarehouseDemandCommandApi warehouseDemandCommandApi;
    private final ProductionOrderStateLockService orderStateLockService;
    private final MaterialRequirementCoverageService coverageService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public SubmitMaterialRequirementService(
            MaterialRequirementRepository requirementRepository,
            MaterialRequirementSubmissionRepository submissionRepository,
            WarehouseDemandCommandApi warehouseDemandCommandApi,
            ProductionOrderViewService orderViewService,
            MaterialRequirementCoverageService coverageService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this(
                requirementRepository,
                submissionRepository,
                warehouseDemandCommandApi,
                new ProductionOrderStateLockService(orderViewService),
                coverageService,
                transactionManager,
                clock);
    }

    SubmitMaterialRequirementService(
            MaterialRequirementRepository requirementRepository,
            MaterialRequirementSubmissionRepository submissionRepository,
            WarehouseDemandCommandApi warehouseDemandCommandApi,
            ProductionOrderStateLockService orderStateLockService,
            MaterialRequirementCoverageService coverageService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.requirementRepository =
                Objects.requireNonNull(requirementRepository, "requirementRepository");
        this.submissionRepository =
                Objects.requireNonNull(submissionRepository, "submissionRepository");
        this.warehouseDemandCommandApi =
                Objects.requireNonNull(warehouseDemandCommandApi, "warehouseDemandCommandApi");
        this.orderStateLockService =
                Objects.requireNonNull(orderStateLockService, "orderStateLockService");
        this.coverageService = Objects.requireNonNull(coverageService, "coverageService");
        this.transactionTemplate =
                new TransactionTemplate(
                        Objects.requireNonNull(transactionManager, "transactionManager"));
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SubmitMaterialRequirementResult submit(
            MaterialRequirementId requirementId, long expectedVersion, String submittedBy) {
        Objects.requireNonNull(requirementId, "requirementId");
        Objects.requireNonNull(submittedBy, "submittedBy");
        if (submittedBy.isBlank()) {
            throw new IllegalArgumentException("submittedBy must not be blank");
        }
        SubmitMaterialRequirementResult result =
                transactionTemplate.execute(
                        status -> doSubmit(requirementId, expectedVersion, submittedBy));
        if (result == null) {
            throw new IllegalStateException(
                    "Submit orchestration returned null for requirement " + requirementId);
        }
        return result;
    }

    private SubmitMaterialRequirementResult doSubmit(
            MaterialRequirementId requirementId, long expectedVersion, String submittedBy) {
        MaterialRequirement requirement =
                requirementRepository
                        .findByIdForUpdate(requirementId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Material requirement not found: "
                                                        + requirementId));

        if (requirement.status() == MaterialRequirementStatus.SUBMITTED) {
            return existingResult(requirement);
        }

        if (requirement.version() != expectedVersion) {
            throw new MaterialRequirementOptimisticLockException(requirementId, expectedVersion);
        }
        if (requirement.lines().isEmpty()) {
            throw new IllegalStateException(
                    "Material requirement with no lines cannot be submitted: " + requirementId);
        }

        revalidateProductCoverage(requirement);

        RoutedTransferResult routed = routeAndCreate(requirement);

        Instant now = clock.instant();
        MaterialRequirement submitted = requirement.submit(submittedBy, now);
        MaterialRequirement persisted = requirementRepository.markSubmitted(submitted);

        List<GeneratedDocumentLink> documentLinks = toDocumentLinks(routed.documents(), now);
        submissionRepository.saveGeneratedDocuments(requirementId, documentLinks);

        List<RoutingSnapshotRow> snapshot = toSnapshot(requirement, routed.routing());
        submissionRepository.saveRoutingSnapshot(requirementId, snapshot);

        return new SubmitMaterialRequirementResult(persisted, documentLinks, snapshot, true);
    }

    /**
     * Locks all affected orders' item states in opaque OrderId ascending order, then rechecks that
     * each frozen DRAFT product quantity is still requestable under SUBMITTED coverage.
     */
    private void revalidateProductCoverage(MaterialRequirement requirement) {
        List<SourceOrderId> orderIds =
                requirement.sourceOrderIds().stream()
                        .sorted(Comparator.comparing(id -> id.value()))
                        .toList();
        Map<MaterialRequirementSourceItemKey, ProductionItemState> lockedStates =
                new LinkedHashMap<>();
        for (SourceOrderId orderId : orderIds) {
            for (ProductionItemState state : orderStateLockService.lockAllItemStates(orderId)) {
                lockedStates.put(
                        MaterialRequirementSourceItemKey.of(
                                state.sourceOrderId(), state.sourceOrderItemId()),
                        state);
            }
        }

        List<MaterialRequirementSourceItemKey> keys =
                requirement.sourceItems().stream()
                        .map(
                                item ->
                                        MaterialRequirementSourceItemKey.of(
                                                item.sourceOrderId(), item.sourceOrderItemId()))
                        .toList();
        Map<MaterialRequirementSourceItemKey, Long> submitted =
                requirementRepository.sumSubmittedProductQuantities(keys);

        for (MaterialRequirementSourceItem sourceItem : requirement.sourceItems()) {
            MaterialRequirementSourceItemKey key =
                    MaterialRequirementSourceItemKey.of(
                            sourceItem.sourceOrderId(), sourceItem.sourceOrderItemId());
            ProductionItemState state = lockedStates.get(key);
            if (state == null) {
                throw new MaterialRequirementSelectionException(
                        "Source order item is no longer in Production: " + key);
            }
            if (state.status() != ProductionStatus.IN_PRODUCTION
                    && state.status() != ProductionStatus.PARTIALLY_RELEASED) {
                throw new MaterialRequirementSelectionException(
                        "Source order item is not eligible for material requirement submit: "
                                + key
                                + ", status="
                                + state.status());
            }
            var coverage =
                    coverageService.coverageFor(state, submitted.getOrDefault(key, 0L));
            long requestable = coverage.requestableProductQuantity();
            if (sourceItem.requestedProductQuantity() > requestable) {
                throw new MaterialRequirementCoverageConflictException(
                        sourceItem.sourceOrderId(),
                        sourceItem.sourceOrderItemId(),
                        sourceItem.requestedProductQuantity(),
                        requestable);
            }
        }
    }

    private RoutedTransferResult routeAndCreate(MaterialRequirement requirement) {
        List<DemandLine> demandLines = new ArrayList<>(requirement.lines().size());
        for (MaterialRequirementLine line : requirement.lines()) {
            demandLines.add(
                    new DemandLine(
                            line.lineId().value().toString(),
                            line.materialReferenceId().value(),
                            line.quantity()));
        }
        try {
            return warehouseDemandCommandApi.createRoutedTransferDocuments(
                    new CreateRoutedTransferCommand(
                            requirement.destinationWarehouseId(), demandLines));
        } catch (DemandSourceUnavailableException ex) {
            throw new MaterialRequirementShortageException(
                    requirement.requirementId(), toShortageLines(requirement, ex));
        }
    }

    private static List<ShortageLine> toShortageLines(
            MaterialRequirement requirement, DemandSourceUnavailableException ex) {
        List<ShortageLine> shortages = new ArrayList<>();
        for (DemandSourceUnavailableException.UnavailableDemand demand : ex.unavailableDemands()) {
            MaterialRequirementLineId lineId =
                    MaterialRequirementLineId.of(UUID.fromString(demand.demandKey()));
            String materialCode =
                    requirement.lines().stream()
                            .filter(line -> line.lineId().equals(lineId))
                            .map(MaterialRequirementLine::materialCode)
                            .findFirst()
                            .orElse("");
            shortages.add(new ShortageLine(lineId, demand.materialReferenceId(), materialCode));
        }
        return shortages;
    }

    private static List<GeneratedDocumentLink> toDocumentLinks(
            List<GeneratedDocument> documents, Instant createdAt) {
        List<GeneratedDocumentLink> links = new ArrayList<>(documents.size());
        int order = 1;
        for (GeneratedDocument document : documents) {
            links.add(
                    new GeneratedDocumentLink(
                            document.documentId(),
                            document.sourceWarehouseId(),
                            document.destinationWarehouseId(),
                            order++,
                            createdAt));
        }
        return links;
    }

    private static List<RoutingSnapshotRow> toSnapshot(
            MaterialRequirement requirement, List<RoutedLine> routing) {
        Map<String, RoutedLine> byKey = new LinkedHashMap<>();
        for (RoutedLine line : routing) {
            byKey.put(line.demandKey(), line);
        }
        List<RoutingSnapshotRow> snapshot = new ArrayList<>(requirement.lines().size());
        for (MaterialRequirementLine line : requirement.lines()) {
            String key = line.lineId().value().toString();
            RoutedLine routed = byKey.get(key);
            if (routed == null) {
                throw new IllegalStateException(
                        "Warehouse routing did not cover requirement line: " + line.lineId());
            }
            snapshot.add(
                    new RoutingSnapshotRow(
                            line.lineId(),
                            MaterialReferenceId.of(routed.materialReferenceId()),
                            routed.sourceWarehouseId(),
                            routed.sourceWarehouseCode(),
                            routed.warehouseDocumentId(),
                            routed.warehouseTransferLineId(),
                            routed.availableAtRouting(),
                            routed.routedQuantity(),
                            routed.uncoveredQuantity()));
        }
        return snapshot;
    }

    private SubmitMaterialRequirementResult existingResult(MaterialRequirement requirement) {
        List<GeneratedDocumentLink> documents =
                submissionRepository.findGeneratedDocuments(requirement.requirementId());
        List<RoutingSnapshotRow> snapshot =
                submissionRepository.findRoutingSnapshot(requirement.requirementId());
        if (documents.isEmpty()) {
            throw new MaterialRequirementSubmissionCorruptedException(
                    requirement.requirementId(), "no generated document links");
        }
        if (snapshot.size() != requirement.lines().size()) {
            throw new MaterialRequirementSubmissionCorruptedException(
                    requirement.requirementId(),
                    "routing snapshot rows ("
                            + snapshot.size()
                            + ") do not match requirement lines ("
                            + requirement.lines().size()
                            + ")");
        }
        return new SubmitMaterialRequirementResult(requirement, documents, snapshot, false);
    }
}
