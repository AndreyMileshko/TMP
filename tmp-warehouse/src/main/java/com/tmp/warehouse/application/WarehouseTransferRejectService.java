package com.tmp.warehouse.application;

import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentStatus;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.warehouse.application.document.WarehouseTransferDocumentProcessor;
import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.TransferDocumentSettlement;
import com.tmp.warehouse.domain.TransferReceiptSettlementItem;
import com.tmp.warehouse.domain.TransferSettlementOptimisticLockException;
import com.tmp.warehouse.domain.TransferSettlementState;
import com.tmp.warehouse.domain.WarehouseDemand;
import com.tmp.warehouse.domain.WarehouseDemandId;
import com.tmp.warehouse.domain.WarehouseDemandLine;
import com.tmp.warehouse.domain.WarehouseDemandLineId;
import com.tmp.warehouse.domain.WarehouseDemandResupplyWaiver;
import com.tmp.warehouse.domain.WarehouseDemandResupplyWaiverRules;
import com.tmp.warehouse.domain.WarehouseDemandSupplyTaskRules;
import com.tmp.warehouse.domain.WarehouseDemandTransferLink;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import com.tmp.warehouse.domain.repository.DemandTaskStateRepository;
import com.tmp.warehouse.domain.repository.TransferDocumentSendAllocationRepository;
import com.tmp.warehouse.domain.repository.TransferDocumentSettlementRepository;
import com.tmp.warehouse.domain.repository.TransferReceiptSettlementItemRepository;
import com.tmp.warehouse.domain.repository.TransferTaskStateRepository;
import com.tmp.warehouse.domain.repository.WarehouseDemandRepository;
import com.tmp.warehouse.domain.repository.WarehouseDemandResupplyWaiverRepository;
import com.tmp.warehouse.domain.repository.WarehouseTransferDocumentRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseDemandFulfillmentReadQuery;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Whole-document reject for POSTED Warehouse Transfer Documents (Stage 3.5.8.3 + ADR-038 optional
 * resupply waiver).
 *
 * <p>No stock mutation and no continuation. Settlement becomes {@code RETURN_PENDING}/{@code
 * REJECTED}; source gets {@code RETURN_MATERIALS} task projection after commit. When {@code
 * resupplyRequired=false}, attributable Demand quantities are waived atomically in this
 * transaction.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators.")
public final class WarehouseTransferRejectService {

    private final DocumentEngine documentEngine;
    private final WarehouseTransferDocumentRepository transferDocuments;
    private final TransferDocumentSettlementRepository settlements;
    private final TransferDocumentSendAllocationRepository sendAllocations;
    private final TransferReceiptSettlementItemRepository receiptItems;
    private final TransferTaskStateRepository taskStates;
    private final WarehouseDemandRepository demands;
    private final WarehouseDemandResupplyWaiverRepository waivers;
    private final JdbcWarehouseDemandFulfillmentReadQuery fulfillmentRead;
    private final DemandTaskStateRepository demandTaskStates;
    private final WarehouseResponsibilityGuard responsibilityGuard;
    private final AuthenticationService authentication;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public WarehouseTransferRejectService(
            DocumentEngine documentEngine,
            WarehouseTransferDocumentRepository transferDocuments,
            TransferDocumentSettlementRepository settlements,
            TransferDocumentSendAllocationRepository sendAllocations,
            TransferReceiptSettlementItemRepository receiptItems,
            TransferTaskStateRepository taskStates,
            WarehouseDemandRepository demands,
            WarehouseDemandResupplyWaiverRepository waivers,
            JdbcWarehouseDemandFulfillmentReadQuery fulfillmentRead,
            DemandTaskStateRepository demandTaskStates,
            WarehouseResponsibilityGuard responsibilityGuard,
            AuthenticationService authentication,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
        this.transferDocuments = Objects.requireNonNull(transferDocuments, "transferDocuments");
        this.settlements = Objects.requireNonNull(settlements, "settlements");
        this.sendAllocations = Objects.requireNonNull(sendAllocations, "sendAllocations");
        this.receiptItems = Objects.requireNonNull(receiptItems, "receiptItems");
        this.taskStates = Objects.requireNonNull(taskStates, "taskStates");
        this.demands = Objects.requireNonNull(demands, "demands");
        this.waivers = Objects.requireNonNull(waivers, "waivers");
        this.fulfillmentRead = Objects.requireNonNull(fulfillmentRead, "fulfillmentRead");
        this.demandTaskStates = Objects.requireNonNull(demandTaskStates, "demandTaskStates");
        this.responsibilityGuard =
                Objects.requireNonNull(responsibilityGuard, "responsibilityGuard");
        this.authentication = Objects.requireNonNull(authentication, "authentication");
        this.transactionTemplate =
                Objects.requireNonNull(transactionTemplate, "transactionTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RejectResult reject(RejectCommand command) {
        Objects.requireNonNull(command, "command");
        String normalizedReason =
                TransferDocumentSettlement.requireNormalizedRejectionReason(command.rejectionReason());
        UUID rejectedBy =
                authentication
                        .currentSession()
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "Access denied: authentication required"))
                        .userId()
                        .value();

        RejectResult result =
                transactionTemplate.execute(
                        status -> {
                            TransferDocumentSettlement locked =
                                    settlements
                                            .lockByDocumentId(command.documentId())
                                            .orElseThrow(
                                                    () ->
                                                            new InvalidWarehouseStateException(
                                                                    "Transfer settlement missing for POSTED document: "
                                                                            + command
                                                                                    .documentId()));
                            DocumentMetadata metadata =
                                    documentEngine
                                            .findById(command.documentId())
                                            .orElseThrow(
                                                    () ->
                                                            new IllegalArgumentException(
                                                                    "Document not found: "
                                                                            + command
                                                                                    .documentId()));
                            if (!WarehouseTransferDocumentProcessor.DOCUMENT_TYPE_ID.equals(
                                    metadata.documentTypeId())) {
                                throw new IllegalArgumentException(
                                        "Not a warehouse.transfer document: "
                                                + command.documentId());
                            }
                            if (metadata.status() != DocumentStatus.POSTED) {
                                throw new IllegalStateException(
                                        "Transfer document reject requires POSTED status: documentId="
                                                + command.documentId()
                                                + ", status="
                                                + metadata.status());
                            }
                            if (locked.settlementState()
                                    != TransferSettlementState.AWAITING_RECEIPT) {
                                throw new InvalidWarehouseStateException(
                                        "Transfer reject requires AWAITING_RECEIPT: documentId="
                                                + command.documentId()
                                                + ", state="
                                                + locked.settlementState());
                            }
                            if (locked.operationalRevision()
                                    != command.expectedOperationalRevision()) {
                                throw new TransferSettlementOptimisticLockException(
                                        command.documentId(),
                                        command.expectedOperationalRevision(),
                                        locked.operationalRevision());
                            }

                            WarehouseTransferDocument payload =
                                    transferDocuments
                                            .findByDocumentId(command.documentId())
                                            .orElseThrow(
                                                    () ->
                                                            new IllegalArgumentException(
                                                                    "Transfer document payload not found: "
                                                                            + command
                                                                                    .documentId()));
                            responsibilityGuard.requireResponsible(
                                    payload.destinationWarehouseId());

                            if (sendAllocations.findByDocumentId(command.documentId()).isEmpty()) {
                                throw new InvalidWarehouseStateException(
                                        "Transfer reject requires send allocations: documentId="
                                                + command.documentId());
                            }

                            List<TransferReceiptSettlementItem> existingReceipts =
                                    receiptItems.findByDocumentId(command.documentId());
                            if (!existingReceipts.isEmpty()) {
                                throw new InvalidWarehouseStateException(
                                        "Invariant violation: receipt settlement items exist while AWAITING_RECEIPT: documentId="
                                                + command.documentId());
                            }

                            Instant now = clock.instant();
                            Set<WarehouseDemandId> affectedDemands = Set.of();
                            if (!command.resupplyRequired()) {
                                affectedDemands =
                                        applyResupplyWaivers(
                                                command.documentId(), now, rejectedBy);
                            }

                            TransferDocumentSettlement rejected =
                                    locked.markRejectedAndReturnPending(
                                            command.expectedOperationalRevision(),
                                            normalizedReason,
                                            now,
                                            rejectedBy,
                                            command.resupplyRequired());
                            settlements.markRejectedAndReturnPending(
                                    command.documentId(),
                                    command.expectedOperationalRevision(),
                                    rejected);
                            taskStates.clear(command.documentId());

                            for (WarehouseDemandId demandId : affectedDemands) {
                                clearSupplyAssignmentIfNoLongerWaiting(demandId);
                            }

                            DocumentMetadata stillPosted =
                                    documentEngine
                                            .findById(command.documentId())
                                            .orElseThrow(
                                                    () ->
                                                            new IllegalStateException(
                                                                    "Document missing after reject: "
                                                                            + command
                                                                                    .documentId()));
                            return new RejectResult(
                                    stillPosted.id(),
                                    stillPosted.status().name(),
                                    rejected.settlementState().name(),
                                    rejected.decision().map(Enum::name).orElse(null),
                                    rejected.operationalRevision(),
                                    rejected.rejectionReason().orElse(null),
                                    rejected.resupplyRequired());
                        });
        if (result == null) {
            throw new IllegalStateException("Transfer document reject returned null");
        }
        return result;
    }

    /**
     * Locks affected Demands, computes attributable waive quantities, and inserts ledger rows.
     * Missing Demand links → no waiver (fail closed / skip). Returns Demands that may need supply
     * task cleanup.
     */
    private Set<WarehouseDemandId> applyResupplyWaivers(
            UUID transferDocumentId, Instant waivedAt, UUID waivedBy) {
        List<WarehouseDemandTransferLink> links =
                demands.findTransferLinksByTransferDocumentId(transferDocumentId);
        if (links.isEmpty()) {
            return Set.of();
        }

        Map<WarehouseDemandLineId, BigDecimal> attributableByLine = new LinkedHashMap<>();
        for (WarehouseDemandTransferLink link : links) {
            attributableByLine.merge(
                    link.demandLineId(), link.linkedQuantity().value(), BigDecimal::add);
        }

        Map<WarehouseDemandLineId, WarehouseDemandId> demandIdByLine =
                new HashMap<>(demands.findDemandIdsByLineIds(attributableByLine.keySet()));
        if (demandIdByLine.size() != attributableByLine.size()) {
            throw new IllegalStateException(
                    "Demand line ownership missing for waiver on transfer "
                            + transferDocumentId);
        }

        Set<UUID> demandIds = new HashSet<>();
        for (WarehouseDemandId demandId : demandIdByLine.values()) {
            demandIds.add(demandId.value());
        }

        // Deterministic Demand lock order to avoid deadlocks across concurrent rejects.
        List<UUID> orderedDemandIds = new ArrayList<>(demandIds);
        orderedDemandIds.sort(Comparator.naturalOrder());
        Map<WarehouseDemandId, WarehouseDemand> lockedDemands = new LinkedHashMap<>();
        for (UUID demandIdValue : orderedDemandIds) {
            WarehouseDemandId demandId = WarehouseDemandId.of(demandIdValue);
            WarehouseDemand locked =
                    demands.lockById(demandId)
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Demand missing during waiver lock: "
                                                            + demandIdValue));
            lockedDemands.put(demandId, locked);
        }

        Map<WarehouseDemandLineId, BigDecimal> receivedByLine =
                fulfillmentRead.receivedQuantitiesByDemandLines(demandIds);
        Map<WarehouseDemandLineId, BigDecimal> alreadyWaivedByLine =
                new HashMap<>(waivers.waivedQuantitiesByDemandLines(demandIds));
        Map<WarehouseDemandLineId, BigDecimal> otherActiveLinked =
                fulfillmentRead.activeLinkedQuantitiesByDemandLineExcludingDocument(
                        attributableByLine.keySet(), transferDocumentId);

        for (Map.Entry<WarehouseDemandLineId, BigDecimal> entry : attributableByLine.entrySet()) {
            WarehouseDemandLineId lineId = entry.getKey();
            WarehouseDemandId demandId = demandIdByLine.get(lineId);
            WarehouseDemand demand = lockedDemands.get(demandId);
            WarehouseDemandLine line =
                    demand.lines().stream()
                            .filter(l -> l.id().equals(lineId))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Demand line missing under locked Demand: "
                                                            + lineId.value()));
            BigDecimal required = line.requiredQuantity().value();
            BigDecimal received = receivedByLine.getOrDefault(lineId, BigDecimal.ZERO);
            BigDecimal alreadyWaived = alreadyWaivedByLine.getOrDefault(lineId, BigDecimal.ZERO);
            BigDecimal otherActive = otherActiveLinked.getOrDefault(lineId, BigDecimal.ZERO);
            BigDecimal waive =
                    WarehouseDemandResupplyWaiverRules.computeWaiveQuantity(
                            entry.getValue(), required, received, alreadyWaived, otherActive);
            if (waive.signum() <= 0) {
                continue;
            }
            waivers.insert(
                    WarehouseDemandResupplyWaiver.create(
                            lineId,
                            transferDocumentId,
                            StockQuantity.of(waive),
                            waivedAt,
                            waivedBy));
            alreadyWaivedByLine.merge(lineId, waive, BigDecimal::add);
        }
        return Set.copyOf(lockedDemands.keySet());
    }

    private void clearSupplyAssignmentIfNoLongerWaiting(WarehouseDemandId demandId) {
        WarehouseDemand demand =
                demands.findById(demandId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Demand missing after waiver: " + demandId.value()));
        Map<WarehouseDemandLineId, BigDecimal> receivedByLine =
                fulfillmentRead.receivedQuantitiesByDemandLine(
                        demand.id(), demand.destinationWarehouseId());
        Map<WarehouseDemandLineId, BigDecimal> waivedByLine =
                waivers.waivedQuantitiesByDemandLine(demand.id());
        Set<WarehouseDemandLineId> activeLines =
                fulfillmentRead.demandLinesWithActiveTransfer(demand.id());
        if (!WarehouseDemandSupplyTaskRules.qualifiesForSupplyTask(
                demand, receivedByLine, waivedByLine, activeLines)) {
            demandTaskStates.clear(demand.id().value());
        }
    }

    public record RejectCommand(
            UUID documentId,
            long expectedOperationalRevision,
            String rejectionReason,
            boolean resupplyRequired) {
        public RejectCommand {
            Objects.requireNonNull(documentId, "documentId");
        }

        /** Backward-compatible factory: resupply required (CHECKED / historical default). */
        public static RejectCommand requiringResupply(
                UUID documentId, long expectedOperationalRevision, String rejectionReason) {
            return new RejectCommand(documentId, expectedOperationalRevision, rejectionReason, true);
        }
    }

    public record RejectResult(
            UUID documentId,
            String documentStatus,
            String settlementState,
            String decision,
            long operationalRevision,
            String rejectionReason,
            boolean resupplyRequired) {
        public RejectResult {
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(documentStatus, "documentStatus");
            Objects.requireNonNull(settlementState, "settlementState");
        }
    }
}
