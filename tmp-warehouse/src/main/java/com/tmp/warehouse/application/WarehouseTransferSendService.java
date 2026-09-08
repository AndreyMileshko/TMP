package com.tmp.warehouse.application;

import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentStatus;
import com.tmp.warehouse.application.document.WarehouseTransferDocumentProcessor;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.TransferDocumentOptimisticLockException;
import com.tmp.warehouse.domain.TransferDocumentSendAllocation;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import com.tmp.warehouse.domain.WarehouseTransferLineId;
import com.tmp.warehouse.domain.repository.TransferDocumentSendAllocationRepository;
import com.tmp.warehouse.domain.repository.TransferTaskStateRepository;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import com.tmp.warehouse.domain.repository.WarehouseTransferDocumentRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Application orchestrator for atomic physical SEND of a Warehouse Transfer Document (Stage 3.5.6).
 *
 * <p>Persists send allocations then calls {@link DocumentEngine#postDocument(UUID)}; the processor
 * performs physical {@code transferSend} + deferred contexts inside the same local transaction.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators.")
public final class WarehouseTransferSendService {

    private final DocumentEngine documentEngine;
    private final WarehouseTransferDocumentRepository transferDocuments;
    private final TransferDocumentSendAllocationRepository sendAllocations;
    private final TransferTaskStateRepository taskStates;
    private final WarehouseCatalogRepository catalog;
    private final WarehouseResponsibilityGuard responsibilityGuard;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public WarehouseTransferSendService(
            DocumentEngine documentEngine,
            WarehouseTransferDocumentRepository transferDocuments,
            TransferDocumentSendAllocationRepository sendAllocations,
            TransferTaskStateRepository taskStates,
            WarehouseCatalogRepository catalog,
            WarehouseResponsibilityGuard responsibilityGuard,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
        this.transferDocuments = Objects.requireNonNull(transferDocuments, "transferDocuments");
        this.sendAllocations = Objects.requireNonNull(sendAllocations, "sendAllocations");
        this.taskStates = Objects.requireNonNull(taskStates, "taskStates");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.responsibilityGuard =
                Objects.requireNonNull(responsibilityGuard, "responsibilityGuard");
        this.transactionTemplate =
                Objects.requireNonNull(transactionTemplate, "transactionTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SendResult send(SendCommand command) {
        Objects.requireNonNull(command, "command");
        SendResult result =
                transactionTemplate.execute(
                        status -> {
                            WarehouseTransferDocument payload =
                                    transferDocuments
                                            .lockByDocumentId(command.documentId())
                                            .orElseThrow(
                                                    () ->
                                                            new IllegalArgumentException(
                                                                    "Transfer document payload not found: "
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
                            if (metadata.status() != DocumentStatus.DRAFT) {
                                throw new IllegalStateException(
                                        "Transfer document send requires DRAFT status: documentId="
                                                + command.documentId()
                                                + ", status="
                                                + metadata.status());
                            }
                            if (metadata.version() != command.expectedDocumentVersion()) {
                                throw new IllegalStateException(
                                        "Stale document version for transfer send: documentId="
                                                + command.documentId()
                                                + ", expected="
                                                + command.expectedDocumentVersion()
                                                + ", actual="
                                                + metadata.version());
                            }
                            if (payload.payloadRevision() != command.expectedPayloadRevision()) {
                                throw new TransferDocumentOptimisticLockException(
                                        command.documentId(),
                                        command.expectedPayloadRevision(),
                                        payload.payloadRevision());
                            }
                            responsibilityGuard.requireResponsible(payload.sourceWarehouseId());

                            List<TransferDocumentSendAllocationValidator.AllocationInput> inputs =
                                    mapInputs(command.allocations());
                            TransferDocumentSendAllocationValidator.requireCompleteCoverage(
                                    payload, inputs);
                            TransferDocumentSendAllocationValidator
                                    .requireSourceCellsBelongToSourceWarehouse(
                                            payload.sourceWarehouseId(), inputs, catalog);

                            Instant now = clock.instant();
                            List<TransferDocumentSendAllocation> rows =
                                    new ArrayList<>(command.allocations().size());
                            for (SourceAllocationInput allocation : command.allocations()) {
                                rows.add(
                                        TransferDocumentSendAllocation.pending(
                                                UUID.randomUUID(),
                                                command.documentId(),
                                                WarehouseTransferLineId.of(allocation.lineId()),
                                                StorageCellId.of(
                                                        allocation.sourceStorageCellId()),
                                                StockQuantity.of(allocation.quantity()),
                                                now));
                            }
                            sendAllocations.insertAll(command.documentId(), rows);

                            DocumentMetadata posted =
                                    documentEngine.postDocument(command.documentId());
                            taskStates.clear(command.documentId());

                            List<UUID> sendOperationIds =
                                    sendAllocations.findByDocumentId(command.documentId()).stream()
                                            .map(
                                                    row ->
                                                            row.sendOperationIdOptional()
                                                                    .orElseThrow(
                                                                            () ->
                                                                                    new IllegalStateException(
                                                                                            "Send allocation missing operation after POST: "
                                                                                                    + row
                                                                                                            .id()))
                                                                    .value())
                                            .toList();
                            return new SendResult(
                                    posted.id(),
                                    posted.status().name(),
                                    posted.version(),
                                    payload.payloadRevision(),
                                    sendOperationIds);
                        });
        if (result == null) {
            throw new IllegalStateException("Transfer document send returned null");
        }
        return result;
    }

    private static List<TransferDocumentSendAllocationValidator.AllocationInput> mapInputs(
            List<SourceAllocationInput> allocations) {
        List<TransferDocumentSendAllocationValidator.AllocationInput> inputs =
                new ArrayList<>(allocations.size());
        for (SourceAllocationInput allocation : allocations) {
            Objects.requireNonNull(allocation, "allocation");
            inputs.add(
                    new TransferDocumentSendAllocationValidator.AllocationInput(
                            allocation.lineId(),
                            allocation.sourceStorageCellId(),
                            allocation.quantity()));
        }
        return inputs;
    }

    public record SourceAllocationInput(
            UUID lineId, UUID sourceStorageCellId, BigDecimal quantity) {
        public SourceAllocationInput {
            Objects.requireNonNull(lineId, "lineId");
            Objects.requireNonNull(sourceStorageCellId, "sourceStorageCellId");
            Objects.requireNonNull(quantity, "quantity");
        }
    }

    public record SendCommand(
            UUID documentId,
            long expectedDocumentVersion,
            long expectedPayloadRevision,
            List<SourceAllocationInput> allocations) {
        public SendCommand {
            Objects.requireNonNull(documentId, "documentId");
            allocations = allocations == null ? List.of() : List.copyOf(allocations);
        }
    }

    public record SendResult(
            UUID documentId,
            String documentStatus,
            long documentVersion,
            long payloadRevision,
            List<UUID> sendOperationIds) {
        public SendResult {
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(documentStatus, "documentStatus");
            sendOperationIds =
                    sendOperationIds == null ? List.of() : List.copyOf(sendOperationIds);
        }
    }
}
