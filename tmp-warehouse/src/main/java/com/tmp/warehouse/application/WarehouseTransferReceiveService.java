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
import com.tmp.warehouse.domain.TransferContinuationReason;
import com.tmp.warehouse.domain.TransferDocumentSendAllocation;
import com.tmp.warehouse.domain.TransferDocumentSettlement;
import com.tmp.warehouse.domain.TransferReceiptSettlementItem;
import com.tmp.warehouse.domain.TransferSettlementOptimisticLockException;
import com.tmp.warehouse.domain.TransferSettlementState;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import com.tmp.warehouse.domain.WarehouseTransferLine;
import com.tmp.warehouse.domain.WarehouseTransferLineId;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.TransferDocumentSendAllocationRepository;
import com.tmp.warehouse.domain.repository.TransferDocumentSettlementRepository;
import com.tmp.warehouse.domain.repository.TransferReceiptSettlementItemRepository;
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
 * Document-level receive for POSTED Warehouse Transfer Documents (Stage 3.5.8.1 / 3.5.8.2).
 *
 * <p>Full accept → SETTLED + Document CLOSED. Partial accept → RETURN_PENDING + RECEIVE_SHORTFALL
 * continuation; original POSTED payload remains immutable. Full reject is Stage 3.5.8.3.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators.")
public final class WarehouseTransferReceiveService {

    private final DocumentEngine documentEngine;
    private final WarehouseTransferDocumentRepository transferDocuments;
    private final WarehouseTransferDocumentService transferDocumentService;
    private final TransferDocumentSettlementRepository settlements;
    private final TransferDocumentSendAllocationRepository sendAllocations;
    private final TransferReceiptSettlementItemRepository receiptItems;
    private final TransferTaskStateRepository taskStates;
    private final WarehouseOperationEngine operationEngine;
    private final MaterialReferenceRepository materials;
    private final WarehouseCatalogRepository catalog;
    private final WarehouseResponsibilityGuard responsibilityGuard;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public WarehouseTransferReceiveService(
            DocumentEngine documentEngine,
            WarehouseTransferDocumentRepository transferDocuments,
            WarehouseTransferDocumentService transferDocumentService,
            TransferDocumentSettlementRepository settlements,
            TransferDocumentSendAllocationRepository sendAllocations,
            TransferReceiptSettlementItemRepository receiptItems,
            TransferTaskStateRepository taskStates,
            WarehouseOperationEngine operationEngine,
            MaterialReferenceRepository materials,
            WarehouseCatalogRepository catalog,
            WarehouseResponsibilityGuard responsibilityGuard,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
        this.transferDocuments = Objects.requireNonNull(transferDocuments, "transferDocuments");
        this.transferDocumentService =
                Objects.requireNonNull(transferDocumentService, "transferDocumentService");
        this.settlements = Objects.requireNonNull(settlements, "settlements");
        this.sendAllocations = Objects.requireNonNull(sendAllocations, "sendAllocations");
        this.receiptItems = Objects.requireNonNull(receiptItems, "receiptItems");
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

    public ReceiveResult receive(ReceiveCommand command) {
        Objects.requireNonNull(command, "command");
        ReceiveResult result =
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
                                        "Transfer document receive requires POSTED status: documentId="
                                                + command.documentId()
                                                + ", status="
                                                + metadata.status());
                            }
                            if (locked.settlementState()
                                    != TransferSettlementState.AWAITING_RECEIPT) {
                                throw new InvalidWarehouseStateException(
                                        "Transfer receive requires AWAITING_RECEIPT: documentId="
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

                            List<TransferDocumentSendAllocation> allocations =
                                    sendAllocations.findByDocumentId(command.documentId());
                            if (allocations.isEmpty()) {
                                throw new InvalidWarehouseStateException(
                                        "Transfer receive requires send allocations: documentId="
                                                + command.documentId());
                            }

                            List<DestinationAllocationInput> normalized =
                                    normalizeDestinationAllocations(
                                            payload, command.destinationAllocations());
                            LineAcceptanceCoverage coverage =
                                    requireLineAcceptanceCoverage(payload, normalized);
                            requireDestinationCells(payload.destinationWarehouseId(), normalized);

                            List<ReceiveSegment> segments =
                                    mapSendToReceive(
                                            payload,
                                            allocations,
                                            normalized,
                                            coverage.partial());

                            Instant now = clock.instant();
                            List<TransferReceiptSettlementItem> items =
                                    new ArrayList<>(segments.size());
                            List<UUID> receiveOperationIds = new ArrayList<>(segments.size());
                            for (ReceiveSegment segment : segments) {
                                MaterialReference material =
                                        materials
                                                .findById(segment.materialReferenceId())
                                                .orElseThrow(
                                                        () ->
                                                                new InvalidWarehouseStateException(
                                                                        "Material reference not found: "
                                                                                + segment
                                                                                        .materialReferenceId()
                                                                                        .value()));
                                WarehouseOperation receiveOp =
                                        operationEngine.transferReceive(
                                                material,
                                                payload.sourceWarehouseId(),
                                                segment.sourceStorageCellId(),
                                                payload.destinationWarehouseId(),
                                                segment.destinationStorageCellId(),
                                                StockQuantity.of(segment.quantity()));
                                receiveOperationIds.add(receiveOp.id().value());
                                items.add(
                                        TransferReceiptSettlementItem.of(
                                                UUID.randomUUID(),
                                                command.documentId(),
                                                segment.sendAllocationId(),
                                                segment.destinationStorageCellId(),
                                                StockQuantity.of(segment.quantity()),
                                                receiveOp.id(),
                                                now));
                            }
                            receiptItems.insertAll(command.documentId(), items);

                            if (coverage.partial()) {
                                List<WarehouseTransferLine> remainderLines =
                                        buildRemainderLines(payload, coverage.acceptedByLine());
                                WarehouseTransferDocumentService.CreatedTransferDocument
                                        continuation =
                                                transferDocumentService.createContinuation(
                                                        payload.documentId(),
                                                        TransferContinuationReason
                                                                .RECEIVE_SHORTFALL,
                                                        payload.sourceWarehouseId(),
                                                        payload.destinationWarehouseId(),
                                                        remainderLines);
                                TransferDocumentSettlement returnPending =
                                        locked.markAcceptedAndReturnPending(
                                                command.expectedOperationalRevision(), now);
                                settlements.markAcceptedAndReturnPending(
                                        command.documentId(),
                                        command.expectedOperationalRevision(),
                                        returnPending);
                                taskStates.clear(command.documentId());
                                DocumentMetadata stillPosted =
                                        documentEngine
                                                .findById(command.documentId())
                                                .orElseThrow();
                                return new ReceiveResult(
                                        stillPosted.id(),
                                        stillPosted.status().name(),
                                        stillPosted.version(),
                                        returnPending.settlementState().name(),
                                        returnPending.decision().map(Enum::name).orElse(null),
                                        returnPending.operationalRevision(),
                                        receiveOperationIds,
                                        continuation.metadata().id());
                            }

                            TransferDocumentSettlement settled =
                                    locked.markAcceptedAndSettled(
                                            command.expectedOperationalRevision(), now);
                            settlements.markAcceptedAndSettled(
                                    command.documentId(),
                                    command.expectedOperationalRevision(),
                                    settled);
                            taskStates.clear(command.documentId());

                            DocumentMetadata closed =
                                    documentEngine.closeDocument(command.documentId());
                            return new ReceiveResult(
                                    closed.id(),
                                    closed.status().name(),
                                    closed.version(),
                                    settled.settlementState().name(),
                                    settled.decision().map(Enum::name).orElse(null),
                                    settled.operationalRevision(),
                                    receiveOperationIds,
                                    null);
                        });
        if (result == null) {
            throw new IllegalStateException("Transfer document receive returned null");
        }
        return result;
    }

    /**
     * Normalizes destination allocations: rejects blank/non-positive quantities and duplicate
     * (lineId, destinationCellId) pairs; preserves quantities; orders by document lineOrder then
     * destination cell UUID. Empty list is rejected (full reject belongs to 3.5.8.3).
     */
    static List<DestinationAllocationInput> normalizeDestinationAllocations(
            WarehouseTransferDocument payload, List<DestinationAllocationInput> inputs) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(inputs, "inputs");
        if (inputs.isEmpty()) {
            throw new InvalidWarehouseStateException(
                    "Transfer receive requires destination allocations: documentId="
                            + payload.documentId()
                            + " (document-level accepted quantity must be > 0; full reject is Stage 3.5.8.3)");
        }
        Map<UUID, Integer> lineOrderById = new HashMap<>();
        for (WarehouseTransferLine line : payload.orderedLines()) {
            lineOrderById.put(line.id().value(), line.lineOrder());
        }
        Set<String> uniqueKeys = new HashSet<>();
        List<DestinationAllocationInput> copy = new ArrayList<>(inputs.size());
        for (DestinationAllocationInput input : inputs) {
            Objects.requireNonNull(input, "destinationAllocation");
            Objects.requireNonNull(input.lineId(), "lineId");
            Objects.requireNonNull(input.destinationStorageCellId(), "destinationStorageCellId");
            Objects.requireNonNull(input.quantity(), "quantity");
            if (input.quantity().signum() <= 0) {
                throw new InvalidWarehouseStateException(
                        "Destination allocation quantity must be positive: " + input.quantity());
            }
            if (!lineOrderById.containsKey(input.lineId())) {
                throw new InvalidWarehouseStateException(
                        "Destination allocation references unknown line: lineId=" + input.lineId());
            }
            String key = input.lineId() + "|" + input.destinationStorageCellId();
            if (!uniqueKeys.add(key)) {
                throw new InvalidWarehouseStateException(
                        "Duplicate destination allocation for line "
                                + input.lineId()
                                + " cell="
                                + input.destinationStorageCellId());
            }
            copy.add(input);
        }
        copy.sort(
                Comparator.comparingInt(
                                (DestinationAllocationInput a) -> lineOrderById.get(a.lineId()))
                        .thenComparing(DestinationAllocationInput::destinationStorageCellId));
        return List.copyOf(copy);
    }

    /**
     * Validates per-line {@code 0 <= accepted <= sent} and document-level {@code totalAccepted > 0}.
     * Per-line zero (omitted line) is allowed when other lines accept quantity.
     */
    static LineAcceptanceCoverage requireLineAcceptanceCoverage(
            WarehouseTransferDocument payload, List<DestinationAllocationInput> allocations) {
        Map<UUID, BigDecimal> totals = new LinkedHashMap<>();
        for (WarehouseTransferLine line : payload.orderedLines()) {
            totals.put(line.id().value(), BigDecimal.ZERO);
        }
        for (DestinationAllocationInput allocation : allocations) {
            totals.merge(allocation.lineId(), allocation.quantity(), BigDecimal::add);
        }
        boolean partial = false;
        BigDecimal totalAccepted = BigDecimal.ZERO;
        for (WarehouseTransferLine line : payload.orderedLines()) {
            UUID lineId = line.id().value();
            BigDecimal accepted = totals.getOrDefault(lineId, BigDecimal.ZERO);
            BigDecimal sent = line.quantity().value();
            if (accepted.compareTo(sent) > 0) {
                throw new InvalidWarehouseStateException(
                        "Over-receive is forbidden: lineId="
                                + lineId
                                + ", accepted="
                                + accepted
                                + ", sent="
                                + sent);
            }
            if (accepted.compareTo(sent) < 0) {
                partial = true;
            }
            totalAccepted = totalAccepted.add(accepted);
        }
        if (totalAccepted.signum() <= 0) {
            throw new InvalidWarehouseStateException(
                    "Document-level accepted quantity must be > 0 (full reject is Stage 3.5.8.3): documentId="
                            + payload.documentId());
        }
        return new LineAcceptanceCoverage(partial, Map.copyOf(totals));
    }

    private Map<UUID, StorageCell> requireDestinationCells(
            com.tmp.warehouse.domain.WarehouseId destinationWarehouseId,
            List<DestinationAllocationInput> allocations) {
        Map<UUID, StorageCell> byId = new HashMap<>();
        for (StorageCell cell : catalog.findStorageCellsByWarehouse(destinationWarehouseId)) {
            byId.put(cell.id().value(), cell);
        }
        Map<UUID, StorageCell> used = new HashMap<>();
        for (DestinationAllocationInput allocation : allocations) {
            StorageCell cell = byId.get(allocation.destinationStorageCellId());
            if (cell == null) {
                throw new InvalidWarehouseStateException(
                        "Destination storage cell does not belong to destination warehouse: cellId="
                                + allocation.destinationStorageCellId()
                                + ", warehouseId="
                                + destinationWarehouseId);
            }
            if (!cell.active()) {
                throw new InvalidWarehouseStateException(
                        "Destination storage cell is inactive: cellId="
                                + allocation.destinationStorageCellId());
            }
            used.put(cell.id().value(), cell);
        }
        return used;
    }

    /**
     * Deterministic two-pointer mapping: send allocations ordered by {@code created_at, id}
     * (repository order); destination allocations already ordered by lineOrder then cell id.
     *
     * <p>For partial accept, destination allocations must be fully consumed; unconsumed source
     * send allocations remain outstanding IN_TRANSIT.
     */
    static List<ReceiveSegment> mapSendToReceive(
            WarehouseTransferDocument payload,
            List<TransferDocumentSendAllocation> allocations,
            List<DestinationAllocationInput> destinations,
            boolean partial) {
        Map<UUID, List<TransferDocumentSendAllocation>> byLine = new LinkedHashMap<>();
        for (WarehouseTransferLine line : payload.orderedLines()) {
            byLine.put(line.id().value(), new ArrayList<>());
        }
        for (TransferDocumentSendAllocation allocation : allocations) {
            List<TransferDocumentSendAllocation> list = byLine.get(allocation.lineId().value());
            if (list == null) {
                throw new InvalidWarehouseStateException(
                        "Send allocation line missing from payload: lineId=" + allocation.lineId());
            }
            if (!allocation.hasSendOperation()) {
                throw new InvalidWarehouseStateException(
                        "Send allocation missing send operation: " + allocation.id());
            }
            list.add(allocation);
        }
        Map<UUID, List<DestinationAllocationInput>> destByLine = new LinkedHashMap<>();
        for (DestinationAllocationInput dest : destinations) {
            destByLine.computeIfAbsent(dest.lineId(), ignored -> new ArrayList<>()).add(dest);
        }

        List<ReceiveSegment> segments = new ArrayList<>();
        for (WarehouseTransferLine line : payload.orderedLines()) {
            UUID lineId = line.id().value();
            List<TransferDocumentSendAllocation> sources = byLine.get(lineId);
            List<DestinationAllocationInput> dests =
                    destByLine.getOrDefault(lineId, List.of());
            if (dests.isEmpty()) {
                if (!partial) {
                    throw new InvalidWarehouseStateException(
                            "Failed to map send allocations to destination allocations for line: "
                                    + lineId);
                }
                continue;
            }
            int si = 0;
            int di = 0;
            BigDecimal sourceRemaining =
                    sources.isEmpty() ? BigDecimal.ZERO : sources.get(0).quantity().value();
            BigDecimal destRemaining = dests.get(0).quantity();
            while (si < sources.size() && di < dests.size()) {
                BigDecimal qty = sourceRemaining.min(destRemaining);
                if (qty.signum() > 0) {
                    TransferDocumentSendAllocation source = sources.get(si);
                    DestinationAllocationInput dest = dests.get(di);
                    segments.add(
                            new ReceiveSegment(
                                    source.id(),
                                    line.materialReferenceId(),
                                    source.sourceStorageCellId(),
                                    StorageCellId.of(dest.destinationStorageCellId()),
                                    qty));
                    sourceRemaining = sourceRemaining.subtract(qty);
                    destRemaining = destRemaining.subtract(qty);
                }
                if (sourceRemaining.signum() == 0) {
                    si++;
                    if (si < sources.size()) {
                        sourceRemaining = sources.get(si).quantity().value();
                    }
                }
                if (destRemaining.signum() == 0) {
                    di++;
                    if (di < dests.size()) {
                        destRemaining = dests.get(di).quantity();
                    }
                }
            }
            if (di < dests.size() || destRemaining.signum() != 0) {
                throw new InvalidWarehouseStateException(
                        "Failed to map send allocations to destination allocations for line: "
                                + lineId);
            }
            if (!partial
                    && (si < sources.size() || sourceRemaining.signum() != 0)) {
                throw new InvalidWarehouseStateException(
                        "Failed to map send allocations to destination allocations for line: "
                                + lineId);
            }
        }
        return List.copyOf(segments);
    }

    static List<WarehouseTransferLine> buildRemainderLines(
            WarehouseTransferDocument payload, Map<UUID, BigDecimal> acceptedByLine) {
        List<WarehouseTransferLine> remainder = new ArrayList<>();
        for (WarehouseTransferLine line : payload.orderedLines()) {
            BigDecimal accepted =
                    acceptedByLine.getOrDefault(line.id().value(), BigDecimal.ZERO);
            BigDecimal outstanding = line.quantity().value().subtract(accepted);
            if (outstanding.signum() > 0) {
                remainder.add(
                        WarehouseTransferLine.of(
                                WarehouseTransferLineId.generate(),
                                line.materialReferenceId(),
                                StockQuantity.of(outstanding),
                                line.lineOrder()));
            }
        }
        if (remainder.isEmpty()) {
            throw new IllegalStateException(
                    "Partial receive requires remainder lines: documentId=" + payload.documentId());
        }
        return List.copyOf(remainder);
    }

    public record DestinationAllocationInput(
            UUID lineId, UUID destinationStorageCellId, BigDecimal quantity) {
        public DestinationAllocationInput {
            Objects.requireNonNull(lineId, "lineId");
            Objects.requireNonNull(destinationStorageCellId, "destinationStorageCellId");
            Objects.requireNonNull(quantity, "quantity");
        }
    }

    public record ReceiveCommand(
            UUID documentId,
            long expectedOperationalRevision,
            List<DestinationAllocationInput> destinationAllocations) {
        public ReceiveCommand {
            Objects.requireNonNull(documentId, "documentId");
            destinationAllocations =
                    destinationAllocations == null
                            ? List.of()
                            : List.copyOf(destinationAllocations);
        }
    }

    public record ReceiveResult(
            UUID documentId,
            String documentStatus,
            long documentVersion,
            String settlementState,
            String decision,
            long operationalRevision,
            List<UUID> receiveOperationIds,
            UUID continuationDocumentId) {
        public ReceiveResult {
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(documentStatus, "documentStatus");
            Objects.requireNonNull(settlementState, "settlementState");
            receiveOperationIds =
                    receiveOperationIds == null ? List.of() : List.copyOf(receiveOperationIds);
        }
    }

    record LineAcceptanceCoverage(boolean partial, Map<UUID, BigDecimal> acceptedByLine) {
        LineAcceptanceCoverage {
            acceptedByLine = acceptedByLine == null ? Map.of() : Map.copyOf(acceptedByLine);
        }
    }

    record ReceiveSegment(
            UUID sendAllocationId,
            com.tmp.warehouse.domain.MaterialReferenceId materialReferenceId,
            StorageCellId sourceStorageCellId,
            StorageCellId destinationStorageCellId,
            BigDecimal quantity) {}
}
