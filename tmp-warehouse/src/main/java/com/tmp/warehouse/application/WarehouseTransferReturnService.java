package com.tmp.warehouse.application;

import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentStatus;
import com.tmp.warehouse.application.document.WarehouseTransferDocumentProcessor;
import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.TransferDocumentSendAllocation;
import com.tmp.warehouse.domain.TransferDocumentSettlement;
import com.tmp.warehouse.domain.TransferReceiptSettlementItem;
import com.tmp.warehouse.domain.TransferReturnSettlementItem;
import com.tmp.warehouse.domain.TransferSettlementDecision;
import com.tmp.warehouse.domain.TransferSettlementOptimisticLockException;
import com.tmp.warehouse.domain.TransferSettlementState;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import com.tmp.warehouse.domain.WarehouseTransferLine;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.TransferDocumentSendAllocationRepository;
import com.tmp.warehouse.domain.repository.TransferDocumentSettlementRepository;
import com.tmp.warehouse.domain.repository.TransferReceiptSettlementItemRepository;
import com.tmp.warehouse.domain.repository.TransferReturnSettlementItemRepository;
import com.tmp.warehouse.domain.repository.TransferTaskStateRepository;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import com.tmp.warehouse.domain.repository.WarehouseTransferDocumentRepository;
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
 * Physical return of outstanding Transfer Document materials to the source warehouse (Stage
 * 3.5.8.3). Settles {@code RETURN_PENDING} → {@code SETTLED} and closes the document atomically.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators.")
public final class WarehouseTransferReturnService {

    private final DocumentEngine documentEngine;
    private final WarehouseTransferDocumentRepository transferDocuments;
    private final TransferDocumentSettlementRepository settlements;
    private final TransferDocumentSendAllocationRepository sendAllocations;
    private final TransferReceiptSettlementItemRepository receiptItems;
    private final TransferReturnSettlementItemRepository returnItems;
    private final TransferTaskStateRepository taskStates;
    private final WarehouseOperationEngine operationEngine;
    private final MaterialReferenceRepository materials;
    private final WarehouseCatalogRepository catalog;
    private final WarehouseResponsibilityGuard responsibilityGuard;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public WarehouseTransferReturnService(
            DocumentEngine documentEngine,
            WarehouseTransferDocumentRepository transferDocuments,
            TransferDocumentSettlementRepository settlements,
            TransferDocumentSendAllocationRepository sendAllocations,
            TransferReceiptSettlementItemRepository receiptItems,
            TransferReturnSettlementItemRepository returnItems,
            TransferTaskStateRepository taskStates,
            WarehouseOperationEngine operationEngine,
            MaterialReferenceRepository materials,
            WarehouseCatalogRepository catalog,
            WarehouseResponsibilityGuard responsibilityGuard,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
        this.transferDocuments = Objects.requireNonNull(transferDocuments, "transferDocuments");
        this.settlements = Objects.requireNonNull(settlements, "settlements");
        this.sendAllocations = Objects.requireNonNull(sendAllocations, "sendAllocations");
        this.receiptItems = Objects.requireNonNull(receiptItems, "receiptItems");
        this.returnItems = Objects.requireNonNull(returnItems, "returnItems");
        this.taskStates = Objects.requireNonNull(taskStates, "taskStates");
        this.operationEngine = Objects.requireNonNull(operationEngine, "operationEngine");
        this.materials = Objects.requireNonNull(materials, "materials");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.responsibilityGuard =
                Objects.requireNonNull(responsibilityGuard, "responsibilityGuard");
        this.transactionTemplate =
                Objects.requireNonNull(transactionTemplate, "transactionTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ReturnResult returnMaterials(ReturnCommand command) {
        Objects.requireNonNull(command, "command");
        ReturnResult result =
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
                                        "Transfer document return requires POSTED status: documentId="
                                                + command.documentId()
                                                + ", status="
                                                + metadata.status());
                            }
                            if (locked.settlementState()
                                    != TransferSettlementState.RETURN_PENDING) {
                                throw new InvalidWarehouseStateException(
                                        "Transfer return requires RETURN_PENDING: documentId="
                                                + command.documentId()
                                                + ", state="
                                                + locked.settlementState());
                            }
                            TransferSettlementDecision decision =
                                    locked.decision()
                                            .orElseThrow(
                                                    () ->
                                                            new InvalidWarehouseStateException(
                                                                    "Transfer return requires decided settlement: documentId="
                                                                            + command
                                                                                    .documentId()));
                            if (decision != TransferSettlementDecision.ACCEPTED
                                    && decision != TransferSettlementDecision.REJECTED) {
                                throw new InvalidWarehouseStateException(
                                        "Transfer return requires ACCEPTED or REJECTED: documentId="
                                                + command.documentId()
                                                + ", decision="
                                                + decision);
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
                            responsibilityGuard.requireResponsible(payload.sourceWarehouseId());

                            List<TransferDocumentSendAllocation> allocations =
                                    sendAllocations.findByDocumentId(command.documentId());
                            if (allocations.isEmpty()) {
                                throw new InvalidWarehouseStateException(
                                        "Transfer return requires send allocations: documentId="
                                                + command.documentId());
                            }
                            List<TransferReceiptSettlementItem> receipts =
                                    receiptItems.findByDocumentId(command.documentId());
                            List<TransferReturnSettlementItem> existingReturns =
                                    returnItems.findByDocumentId(command.documentId());

                            Map<UUID, BigDecimal> outstandingByAllocation =
                                    outstandingBySendAllocation(
                                            allocations, receipts, existingReturns);
                            BigDecimal totalOutstanding =
                                    outstandingByAllocation.values().stream()
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                            if (totalOutstanding.signum() <= 0) {
                                throw new InvalidWarehouseStateException(
                                        "Transfer return requires outstanding quantity: documentId="
                                                + command.documentId());
                            }

                            List<ReturnTargetInput> plan =
                                    command.returnAllocations().isEmpty()
                                            ? defaultReturnPlan(allocations, outstandingByAllocation)
                                            : normalizeExplicitReturnPlan(
                                                    payload, command.returnAllocations());
                            if (!command.returnAllocations().isEmpty()) {
                                requireExactOutstandingCoverage(
                                        payload, allocations, outstandingByAllocation, plan);
                            }
                            requireReturnCells(payload.sourceWarehouseId(), plan);

                            List<ReturnSegment> segments =
                                    mapOutstandingToReturn(
                                            payload, allocations, outstandingByAllocation, plan);

                            Instant now = clock.instant();
                            List<TransferReturnSettlementItem> items =
                                    new ArrayList<>(segments.size());
                            List<UUID> returnOperationIds = new ArrayList<>(segments.size());
                            for (ReturnSegment segment : segments) {
                                MaterialReference material =
                                        materials
                                                .findById(segment.materialReferenceId())
                                                .orElseThrow(
                                                        () ->
                                                                new InvalidWarehouseStateException(
                                                                        "Material reference not found: "
                                                                                + segment
                                                                                        .materialReferenceId()));
                                WarehouseOperation operation =
                                        operationEngine.transferReturn(
                                                material,
                                                payload.sourceWarehouseId(),
                                                segment.inTransitSourceCellId(),
                                                segment.returnStorageCellId(),
                                                StockQuantity.of(segment.quantity()));
                                returnOperationIds.add(operation.id().value());
                                items.add(
                                        TransferReturnSettlementItem.of(
                                                UUID.randomUUID(),
                                                command.documentId(),
                                                segment.sendAllocationId(),
                                                segment.returnStorageCellId(),
                                                StockQuantity.of(segment.quantity()),
                                                operation.id(),
                                                now));
                            }
                            returnItems.insertAll(command.documentId(), items);
                            verifyConservation(allocations, receipts, items, existingReturns);

                            TransferDocumentSettlement settled =
                                    locked.markReturnedAndSettled(
                                            command.expectedOperationalRevision(), now);
                            settlements.markReturnedAndSettled(
                                    command.documentId(),
                                    command.expectedOperationalRevision(),
                                    settled);
                            taskStates.clear(command.documentId());

                            DocumentMetadata closed =
                                    documentEngine.closeDocument(command.documentId());
                            return new ReturnResult(
                                    closed.id(),
                                    closed.status().name(),
                                    settled.settlementState().name(),
                                    settled.decision().map(Enum::name).orElse(null),
                                    settled.operationalRevision(),
                                    returnOperationIds);
                        });
        if (result == null) {
            throw new IllegalStateException("Transfer document return returned null");
        }
        return result;
    }

    static Map<UUID, BigDecimal> outstandingBySendAllocation(
            List<TransferDocumentSendAllocation> allocations,
            List<TransferReceiptSettlementItem> receipts,
            List<TransferReturnSettlementItem> returns) {
        Map<UUID, BigDecimal> accepted = new HashMap<>();
        for (TransferReceiptSettlementItem item : receipts) {
            accepted.merge(item.sendAllocationId(), item.quantity().value(), BigDecimal::add);
        }
        Map<UUID, BigDecimal> returned = new HashMap<>();
        for (TransferReturnSettlementItem item : returns) {
            returned.merge(item.sendAllocationId(), item.quantity().value(), BigDecimal::add);
        }
        Map<UUID, BigDecimal> outstanding = new LinkedHashMap<>();
        for (TransferDocumentSendAllocation allocation : allocations) {
            BigDecimal qty =
                    allocation
                            .quantity()
                            .value()
                            .subtract(accepted.getOrDefault(allocation.id(), BigDecimal.ZERO))
                            .subtract(returned.getOrDefault(allocation.id(), BigDecimal.ZERO));
            if (qty.signum() < 0) {
                throw new InvalidWarehouseStateException(
                        "Negative outstanding for sendAllocationId=" + allocation.id());
            }
            if (qty.signum() > 0) {
                outstanding.put(allocation.id(), qty);
            }
        }
        return outstanding;
    }

    static List<ReturnTargetInput> defaultReturnPlan(
            List<TransferDocumentSendAllocation> allocations,
            Map<UUID, BigDecimal> outstandingByAllocation) {
        List<ReturnTargetInput> plan = new ArrayList<>();
        for (TransferDocumentSendAllocation allocation : allocations) {
            BigDecimal outstanding = outstandingByAllocation.get(allocation.id());
            if (outstanding == null || outstanding.signum() <= 0) {
                continue;
            }
            plan.add(
                    new ReturnTargetInput(
                            allocation.lineId().value(),
                            allocation.sourceStorageCellId().value(),
                            outstanding));
        }
        return List.copyOf(plan);
    }

    static List<ReturnTargetInput> normalizeExplicitReturnPlan(
            WarehouseTransferDocument payload, List<ReturnTargetInput> inputs) {
        Map<UUID, Integer> lineOrderById = new HashMap<>();
        for (WarehouseTransferLine line : payload.orderedLines()) {
            lineOrderById.put(line.id().value(), line.lineOrder());
        }
        Set<String> uniqueKeys = new HashSet<>();
        List<ReturnTargetInput> copy = new ArrayList<>(inputs.size());
        for (ReturnTargetInput input : inputs) {
            Objects.requireNonNull(input, "returnAllocation");
            Objects.requireNonNull(input.lineId(), "lineId");
            Objects.requireNonNull(input.returnStorageCellId(), "returnStorageCellId");
            Objects.requireNonNull(input.quantity(), "quantity");
            if (input.quantity().signum() <= 0) {
                throw new InvalidWarehouseStateException(
                        "Return allocation quantity must be positive: " + input.quantity());
            }
            if (!lineOrderById.containsKey(input.lineId())) {
                throw new InvalidWarehouseStateException(
                        "Return allocation references unknown line: lineId=" + input.lineId());
            }
            String key = input.lineId() + "|" + input.returnStorageCellId();
            if (!uniqueKeys.add(key)) {
                throw new InvalidWarehouseStateException(
                        "Duplicate return allocation for line "
                                + input.lineId()
                                + " cell="
                                + input.returnStorageCellId());
            }
            copy.add(input);
        }
        copy.sort(
                Comparator.comparingInt((ReturnTargetInput a) -> lineOrderById.get(a.lineId()))
                        .thenComparing(ReturnTargetInput::returnStorageCellId));
        return List.copyOf(copy);
    }

    static void requireExactOutstandingCoverage(
            WarehouseTransferDocument payload,
            List<TransferDocumentSendAllocation> allocations,
            Map<UUID, BigDecimal> outstandingByAllocation,
            List<ReturnTargetInput> plan) {
        Map<UUID, BigDecimal> outstandingByLine = new HashMap<>();
        for (TransferDocumentSendAllocation allocation : allocations) {
            BigDecimal outstanding =
                    outstandingByAllocation.getOrDefault(allocation.id(), BigDecimal.ZERO);
            if (outstanding.signum() > 0) {
                outstandingByLine.merge(
                        allocation.lineId().value(), outstanding, BigDecimal::add);
            }
        }
        Map<UUID, BigDecimal> plannedByLine = new HashMap<>();
        for (ReturnTargetInput input : plan) {
            plannedByLine.merge(input.lineId(), input.quantity(), BigDecimal::add);
        }
        Set<UUID> lines = new HashSet<>();
        lines.addAll(outstandingByLine.keySet());
        lines.addAll(plannedByLine.keySet());
        for (UUID lineId : lines) {
            BigDecimal outstanding = outstandingByLine.getOrDefault(lineId, BigDecimal.ZERO);
            BigDecimal planned = plannedByLine.getOrDefault(lineId, BigDecimal.ZERO);
            if (outstanding.compareTo(planned) != 0) {
                throw new InvalidWarehouseStateException(
                        "Return plan must cover outstanding exactly for line "
                                + lineId
                                + ": outstanding="
                                + outstanding
                                + ", planned="
                                + planned
                                + ", documentId="
                                + payload.documentId());
            }
        }
    }

    private void requireReturnCells(
            com.tmp.warehouse.domain.WarehouseId sourceWarehouseId, List<ReturnTargetInput> plan) {
        Map<UUID, StorageCell> byId = new HashMap<>();
        for (StorageCell cell : catalog.findStorageCellsByWarehouse(sourceWarehouseId)) {
            byId.put(cell.id().value(), cell);
        }
        for (ReturnTargetInput input : plan) {
            StorageCell cell = byId.get(input.returnStorageCellId());
            if (cell == null) {
                throw new InvalidWarehouseStateException(
                        "Return storage cell does not belong to source warehouse: cellId="
                                + input.returnStorageCellId()
                                + ", warehouseId="
                                + sourceWarehouseId);
            }
            if (!cell.active()) {
                throw new InvalidWarehouseStateException(
                        "Return storage cell is inactive: cellId=" + input.returnStorageCellId());
            }
        }
    }

    static List<ReturnSegment> mapOutstandingToReturn(
            WarehouseTransferDocument payload,
            List<TransferDocumentSendAllocation> allocations,
            Map<UUID, BigDecimal> outstandingByAllocation,
            List<ReturnTargetInput> plan) {
        Map<UUID, List<OutstandingSource>> byLine = new LinkedHashMap<>();
        List<TransferDocumentSendAllocation> ordered = new ArrayList<>(allocations);
        ordered.sort(
                Comparator.comparing(TransferDocumentSendAllocation::createdAt)
                        .thenComparing(TransferDocumentSendAllocation::id));
        for (TransferDocumentSendAllocation allocation : ordered) {
            BigDecimal outstanding =
                    outstandingByAllocation.getOrDefault(allocation.id(), BigDecimal.ZERO);
            if (outstanding.signum() <= 0) {
                continue;
            }
            byLine.computeIfAbsent(allocation.lineId().value(), ignored -> new ArrayList<>())
                    .add(
                            new OutstandingSource(
                                    allocation.id(),
                                    allocation.sourceStorageCellId(),
                                    outstanding));
        }
        Map<UUID, List<ReturnTargetInput>> targetsByLine = new LinkedHashMap<>();
        for (ReturnTargetInput target : plan) {
            targetsByLine.computeIfAbsent(target.lineId(), ignored -> new ArrayList<>()).add(target);
        }

        Map<UUID, WarehouseTransferLine> lineById = new HashMap<>();
        for (WarehouseTransferLine line : payload.orderedLines()) {
            lineById.put(line.id().value(), line);
        }

        List<ReturnSegment> segments = new ArrayList<>();
        for (Map.Entry<UUID, List<OutstandingSource>> entry : byLine.entrySet()) {
            UUID lineId = entry.getKey();
            List<OutstandingSource> sources = entry.getValue();
            List<ReturnTargetInput> targets = targetsByLine.getOrDefault(lineId, List.of());
            WarehouseTransferLine line = lineById.get(lineId);
            if (line == null) {
                throw new InvalidWarehouseStateException(
                        "Outstanding send allocation references unknown line: " + lineId);
            }
            int si = 0;
            int ti = 0;
            BigDecimal sourceRemaining = sources.get(0).outstanding();
            BigDecimal targetRemaining = targets.isEmpty() ? BigDecimal.ZERO : targets.get(0).quantity();
            while (si < sources.size() && ti < targets.size()) {
                BigDecimal qty = sourceRemaining.min(targetRemaining);
                if (qty.signum() > 0) {
                    OutstandingSource source = sources.get(si);
                    ReturnTargetInput target = targets.get(ti);
                    segments.add(
                            new ReturnSegment(
                                    source.sendAllocationId(),
                                    line.materialReferenceId(),
                                    source.inTransitSourceCellId(),
                                    StorageCellId.of(target.returnStorageCellId()),
                                    qty));
                    sourceRemaining = sourceRemaining.subtract(qty);
                    targetRemaining = targetRemaining.subtract(qty);
                }
                if (sourceRemaining.signum() == 0) {
                    si++;
                    if (si < sources.size()) {
                        sourceRemaining = sources.get(si).outstanding();
                    }
                }
                if (targetRemaining.signum() == 0) {
                    ti++;
                    if (ti < targets.size()) {
                        targetRemaining = targets.get(ti).quantity();
                    }
                }
            }
            if (si < sources.size()
                    || sourceRemaining.signum() != 0
                    || ti < targets.size()
                    || targetRemaining.signum() != 0) {
                throw new InvalidWarehouseStateException(
                        "Failed to map outstanding send allocations to return targets for line: "
                                + lineId);
            }
        }
        for (UUID lineId : targetsByLine.keySet()) {
            if (!byLine.containsKey(lineId)) {
                throw new InvalidWarehouseStateException(
                        "Return plan references line with no outstanding quantity: " + lineId);
            }
        }
        return List.copyOf(segments);
    }

    private static void verifyConservation(
            List<TransferDocumentSendAllocation> allocations,
            List<TransferReceiptSettlementItem> receipts,
            List<TransferReturnSettlementItem> newReturns,
            List<TransferReturnSettlementItem> existingReturns) {
        Map<UUID, BigDecimal> accepted = new HashMap<>();
        for (TransferReceiptSettlementItem item : receipts) {
            accepted.merge(item.sendAllocationId(), item.quantity().value(), BigDecimal::add);
        }
        Map<UUID, BigDecimal> returned = new HashMap<>();
        for (TransferReturnSettlementItem item : existingReturns) {
            returned.merge(item.sendAllocationId(), item.quantity().value(), BigDecimal::add);
        }
        for (TransferReturnSettlementItem item : newReturns) {
            returned.merge(item.sendAllocationId(), item.quantity().value(), BigDecimal::add);
        }
        for (TransferDocumentSendAllocation allocation : allocations) {
            BigDecimal acceptedQty = accepted.getOrDefault(allocation.id(), BigDecimal.ZERO);
            BigDecimal returnedQty = returned.getOrDefault(allocation.id(), BigDecimal.ZERO);
            if (acceptedQty.add(returnedQty).compareTo(allocation.quantity().value()) != 0) {
                throw new InvalidWarehouseStateException(
                        "Return conservation failed: sendAllocationId="
                                + allocation.id()
                                + ", sent="
                                + allocation.quantity().value()
                                + ", accepted="
                                + acceptedQty
                                + ", returned="
                                + returnedQty);
            }
        }
    }

    public record ReturnTargetInput(
            UUID lineId, UUID returnStorageCellId, BigDecimal quantity) {
        public ReturnTargetInput {
            Objects.requireNonNull(lineId, "lineId");
            Objects.requireNonNull(returnStorageCellId, "returnStorageCellId");
            Objects.requireNonNull(quantity, "quantity");
        }
    }

    public record ReturnCommand(
            UUID documentId,
            long expectedOperationalRevision,
            List<ReturnTargetInput> returnAllocations) {
        public ReturnCommand {
            Objects.requireNonNull(documentId, "documentId");
            returnAllocations =
                    returnAllocations == null ? List.of() : List.copyOf(returnAllocations);
        }
    }

    public record ReturnResult(
            UUID documentId,
            String documentStatus,
            String settlementState,
            String decision,
            long operationalRevision,
            List<UUID> returnOperationIds) {
        public ReturnResult {
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(documentStatus, "documentStatus");
            Objects.requireNonNull(settlementState, "settlementState");
            returnOperationIds =
                    returnOperationIds == null ? List.of() : List.copyOf(returnOperationIds);
        }
    }

    record OutstandingSource(
            UUID sendAllocationId, StorageCellId inTransitSourceCellId, BigDecimal outstanding) {}

    record ReturnSegment(
            UUID sendAllocationId,
            com.tmp.warehouse.domain.MaterialReferenceId materialReferenceId,
            StorageCellId inTransitSourceCellId,
            StorageCellId returnStorageCellId,
            BigDecimal quantity) {}
}
