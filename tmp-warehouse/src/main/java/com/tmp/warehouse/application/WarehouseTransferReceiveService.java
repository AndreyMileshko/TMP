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
import com.tmp.warehouse.domain.TransferSettlementOptimisticLockException;
import com.tmp.warehouse.domain.TransferSettlementState;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import com.tmp.warehouse.domain.WarehouseTransferLine;
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
 * Full document-level receive for POSTED Warehouse Transfer Documents (Stage 3.5.8.1).
 *
 * <p>Partial acceptance, reject, and return are out of scope — destination allocation totals must
 * equal posted line quantities exactly.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators.")
public final class WarehouseTransferReceiveService {

    private final DocumentEngine documentEngine;
    private final WarehouseTransferDocumentRepository transferDocuments;
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
                            requireExactLineCoverage(payload, normalized);
                            requireDestinationCells(payload.destinationWarehouseId(), normalized);

                            List<ReceiveSegment> segments =
                                    mapSendToReceive(payload, allocations, normalized);

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
                                    receiveOperationIds);
                        });
        if (result == null) {
            throw new IllegalStateException("Transfer document receive returned null");
        }
        return result;
    }

    /**
     * Normalizes destination allocations: rejects blank/non-positive quantities and duplicate
     * (lineId, destinationCellId) pairs; preserves quantities; orders by document lineOrder then
     * destination cell UUID.
     */
    static List<DestinationAllocationInput> normalizeDestinationAllocations(
            WarehouseTransferDocument payload, List<DestinationAllocationInput> inputs) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(inputs, "inputs");
        if (inputs.isEmpty()) {
            throw new InvalidWarehouseStateException(
                    "Transfer receive requires destination allocations: documentId="
                            + payload.documentId());
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

    static void requireExactLineCoverage(
            WarehouseTransferDocument payload, List<DestinationAllocationInput> allocations) {
        Map<UUID, BigDecimal> totals = new LinkedHashMap<>();
        for (WarehouseTransferLine line : payload.orderedLines()) {
            totals.put(line.id().value(), BigDecimal.ZERO);
        }
        for (DestinationAllocationInput allocation : allocations) {
            totals.merge(allocation.lineId(), allocation.quantity(), BigDecimal::add);
        }
        for (WarehouseTransferLine line : payload.orderedLines()) {
            UUID lineId = line.id().value();
            BigDecimal accepted = totals.getOrDefault(lineId, BigDecimal.ZERO);
            BigDecimal sent = line.quantity().value();
            int cmp = accepted.compareTo(sent);
            if (cmp < 0) {
                throw new InvalidWarehouseStateException(
                        "Partial receive is not supported in Stage 3.5.8.1: lineId="
                                + lineId
                                + ", accepted="
                                + accepted
                                + ", sent="
                                + sent);
            }
            if (cmp > 0) {
                throw new InvalidWarehouseStateException(
                        "Over-receive is forbidden: lineId="
                                + lineId
                                + ", accepted="
                                + accepted
                                + ", sent="
                                + sent);
            }
        }
        for (UUID lineId : totals.keySet()) {
            boolean known =
                    payload.orderedLines().stream().anyMatch(l -> l.id().value().equals(lineId));
            if (!known) {
                throw new InvalidWarehouseStateException(
                        "Destination allocation references unknown line: lineId=" + lineId);
            }
        }
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
     */
    static List<ReceiveSegment> mapSendToReceive(
            WarehouseTransferDocument payload,
            List<TransferDocumentSendAllocation> allocations,
            List<DestinationAllocationInput> destinations) {
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
        // Repository already returns ORDER BY created_at, id — preserve that order within each line.
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
            int si = 0;
            int di = 0;
            BigDecimal sourceRemaining =
                    sources.isEmpty() ? BigDecimal.ZERO : sources.get(0).quantity().value();
            BigDecimal destRemaining =
                    dests.isEmpty() ? BigDecimal.ZERO : dests.get(0).quantity();
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
            if (si < sources.size() || di < dests.size()
                    || sourceRemaining.signum() != 0
                    || destRemaining.signum() != 0) {
                throw new InvalidWarehouseStateException(
                        "Failed to map send allocations to destination allocations for line: "
                                + lineId);
            }
        }
        return List.copyOf(segments);
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
            List<UUID> receiveOperationIds) {
        public ReceiveResult {
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(documentStatus, "documentStatus");
            Objects.requireNonNull(settlementState, "settlementState");
            receiveOperationIds =
                    receiveOperationIds == null ? List.of() : List.copyOf(receiveOperationIds);
        }
    }

    record ReceiveSegment(
            UUID sendAllocationId,
            com.tmp.warehouse.domain.MaterialReferenceId materialReferenceId,
            StorageCellId sourceStorageCellId,
            StorageCellId destinationStorageCellId,
            BigDecimal quantity) {}
}
