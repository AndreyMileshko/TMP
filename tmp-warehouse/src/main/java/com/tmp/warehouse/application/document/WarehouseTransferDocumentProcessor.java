package com.tmp.warehouse.application.document;

import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.warehouse.application.TransferDocumentSendAllocationValidator;
import com.tmp.warehouse.application.WarehouseOperationEngine;
import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.MaterialReferenceId;
import com.tmp.warehouse.domain.TransferDocumentSendAllocation;
import com.tmp.warehouse.domain.TransferDocumentSettlement;
import com.tmp.warehouse.domain.TransferOperationContext;
import com.tmp.warehouse.domain.TransferReceiptSettlementItem;
import com.tmp.warehouse.domain.TransferSettlementState;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import com.tmp.warehouse.domain.WarehouseTransferLine;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.TransferDocumentSendAllocationRepository;
import com.tmp.warehouse.domain.repository.TransferDocumentSettlementRepository;
import com.tmp.warehouse.domain.repository.TransferOperationContextRepository;
import com.tmp.warehouse.domain.repository.TransferReceiptSettlementItemRepository;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import com.tmp.warehouse.domain.repository.WarehouseTransferDocumentRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Document Engine processor for {@code warehouse.transfer}.
 *
 * <p>POST executes atomic physical multi-line send (Stage 3.5.6): one {@code TRANSFER_SEND} per
 * persisted source-cell allocation, deferred destination context, and allocation→operation links.
 * DELETE removes the Warehouse-owned payload (no cross-schema FK to {@code documents.documents}).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators.")
public final class WarehouseTransferDocumentProcessor implements DocumentProcessor {

    public static final String DOCUMENT_TYPE_ID = "warehouse.transfer";

    private final WarehouseTransferDocumentRepository repository;
    private final TransferDocumentSendAllocationRepository sendAllocations;
    private final WarehouseOperationEngine operationEngine;
    private final TransferOperationContextRepository transferContexts;
    private final MaterialReferenceRepository materials;
    private final WarehouseCatalogRepository catalog;
    private final TransferDocumentSettlementRepository settlements;
    private final TransferReceiptSettlementItemRepository receiptItems;
    private final Clock clock;

    /** Stage 3.5.2 foundation constructor — POST remains unsupported without send wiring. */
    public WarehouseTransferDocumentProcessor(WarehouseTransferDocumentRepository repository) {
        this(repository, null, null, null, null, null, null, null, null);
    }

    /** Stage 3.5.6 send wiring without settlement (tests may still use this overload). */
    public WarehouseTransferDocumentProcessor(
            WarehouseTransferDocumentRepository repository,
            TransferDocumentSendAllocationRepository sendAllocations,
            WarehouseOperationEngine operationEngine,
            TransferOperationContextRepository transferContexts,
            MaterialReferenceRepository materials,
            WarehouseCatalogRepository catalog) {
        this(
                repository,
                sendAllocations,
                operationEngine,
                transferContexts,
                materials,
                catalog,
                null,
                null,
                null);
    }

    public WarehouseTransferDocumentProcessor(
            WarehouseTransferDocumentRepository repository,
            TransferDocumentSendAllocationRepository sendAllocations,
            WarehouseOperationEngine operationEngine,
            TransferOperationContextRepository transferContexts,
            MaterialReferenceRepository materials,
            WarehouseCatalogRepository catalog,
            TransferDocumentSettlementRepository settlements,
            TransferReceiptSettlementItemRepository receiptItems,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sendAllocations = sendAllocations;
        this.operationEngine = operationEngine;
        this.transferContexts = transferContexts;
        this.materials = materials;
        this.catalog = catalog;
        this.settlements = settlements;
        this.receiptItems = receiptItems;
        this.clock = clock;
    }

    @Override
    public String documentTypeId() {
        return DOCUMENT_TYPE_ID;
    }

    @Override
    public void validateCreate(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
    }

    @Override
    public void validateUpdate(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
    }

    @Override
    public void onPost(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
        requireSendWiring();
        UUID documentId = context.document().id();
        WarehouseTransferDocument payload =
                repository
                        .findByDocumentId(documentId)
                        .orElseThrow(
                                () ->
                                        new InvalidWarehouseStateException(
                                                "Transfer document payload not found for POST: "
                                                        + documentId));
        List<TransferDocumentSendAllocation> allocations =
                sendAllocations.findByDocumentId(documentId);
        if (allocations.isEmpty()) {
            throw new InvalidWarehouseStateException(
                    "Transfer document POST requires staged send allocations: documentId="
                            + documentId);
        }

        List<TransferDocumentSendAllocationValidator.AllocationInput> inputs =
                new ArrayList<>(allocations.size());
        for (TransferDocumentSendAllocation allocation : allocations) {
            if (!documentId.equals(allocation.documentId())) {
                throw new InvalidWarehouseStateException(
                        "Send allocation document mismatch: documentId="
                                + documentId
                                + ", allocationDocumentId="
                                + allocation.documentId());
            }
            inputs.add(
                    TransferDocumentSendAllocationValidator.AllocationInput.of(
                            allocation.lineId(),
                            allocation.sourceStorageCellId(),
                            allocation.quantity().value()));
        }
        TransferDocumentSendAllocationValidator.requireCompleteCoverage(payload, inputs);
        TransferDocumentSendAllocationValidator.requireSourceCellsBelongToSourceWarehouse(
                payload.sourceWarehouseId(), inputs, catalog);

        Map<UUID, WarehouseTransferLine> linesById = new HashMap<>();
        for (WarehouseTransferLine line : payload.lines()) {
            linesById.put(line.id().value(), line);
        }

        for (TransferDocumentSendAllocation allocation : allocations) {
            if (allocation.hasSendOperation()) {
                throw new InvalidWarehouseStateException(
                        "Send allocation already linked before physical send: "
                                + allocation.id());
            }
            WarehouseTransferLine line = linesById.get(allocation.lineId().value());
            if (line == null) {
                throw new InvalidWarehouseStateException(
                        "Send allocation line missing from payload: lineId="
                                + allocation.lineId());
            }
            MaterialReference material = requireMaterial(line.materialReferenceId());
            WarehouseOperation send =
                    operationEngine.transferSend(
                            material,
                            payload.sourceWarehouseId(),
                            allocation.sourceStorageCellId(),
                            allocation.quantity());
            transferContexts.save(
                    TransferOperationContext.deferredDestination(
                            send.id(), payload.destinationWarehouseId()));
            sendAllocations.attachSendOperation(allocation.id(), send.id());
        }

        List<TransferDocumentSendAllocation> linked =
                sendAllocations.findByDocumentId(documentId);
        if (linked.size() != allocations.size()) {
            throw new InvalidWarehouseStateException(
                    "Send allocation count changed during POST: documentId=" + documentId);
        }
        for (TransferDocumentSendAllocation row : linked) {
            if (!row.hasSendOperation()) {
                throw new InvalidWarehouseStateException(
                        "Send allocation missing sendOperationId after physical send: "
                                + row.id());
            }
        }

        requireSettlementWiring();
        settlements.insertAwaitingReceipt(
                TransferDocumentSettlement.awaitingReceipt(documentId, clock.instant()));
    }

    @Override
    public void onUnpost(DocumentOperationContext context) {
        throw new UnsupportedOperationException(
                "Warehouse Transfer Document does not support UNPOST");
    }

    @Override
    public void onClose(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
        requireSettlementWiring();
        UUID documentId = context.document().id();
        TransferDocumentSettlement settlement =
                settlements
                        .findByDocumentId(documentId)
                        .orElseThrow(
                                () ->
                                        new InvalidWarehouseStateException(
                                                "Transfer settlement missing on close: "
                                                        + documentId));
        if (settlement.settlementState() != TransferSettlementState.SETTLED) {
            throw new InvalidWarehouseStateException(
                    "Transfer document close requires SETTLED settlement: documentId="
                            + documentId
                            + ", state="
                            + settlement.settlementState());
        }
        List<TransferDocumentSendAllocation> allocations =
                sendAllocations.findByDocumentId(documentId);
        List<TransferReceiptSettlementItem> items = receiptItems.findByDocumentId(documentId);
        Map<UUID, BigDecimal> acceptedByAllocation = new HashMap<>();
        for (TransferReceiptSettlementItem item : items) {
            acceptedByAllocation.merge(
                    item.sendAllocationId(), item.quantity().value(), BigDecimal::add);
        }
        for (TransferDocumentSendAllocation allocation : allocations) {
            BigDecimal accepted =
                    acceptedByAllocation.getOrDefault(allocation.id(), BigDecimal.ZERO);
            if (accepted.compareTo(allocation.quantity().value()) != 0) {
                throw new InvalidWarehouseStateException(
                        "Transfer close conservation failed: sendAllocationId="
                                + allocation.id()
                                + ", sent="
                                + allocation.quantity().value()
                                + ", accepted="
                                + accepted);
            }
        }
    }

    @Override
    public void onDelete(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
        repository.deleteByDocumentId(context.document().id());
    }

    private void requireSendWiring() {
        if (sendAllocations == null
                || operationEngine == null
                || transferContexts == null
                || materials == null
                || catalog == null) {
            throw new UnsupportedOperationException(
                    "Warehouse Transfer Document POST (physical send) is not fully wired");
        }
    }

    private void requireSettlementWiring() {
        if (settlements == null || receiptItems == null || clock == null || sendAllocations == null) {
            throw new UnsupportedOperationException(
                    "Warehouse Transfer Document settlement/close is not fully wired");
        }
    }

    private MaterialReference requireMaterial(MaterialReferenceId materialReferenceId) {
        return materials
                .findById(materialReferenceId)
                .orElseThrow(
                        () ->
                                new InvalidWarehouseStateException(
                                        "Material reference not found: "
                                                + materialReferenceId.value()));
    }
}
