package com.tmp.warehouse.application;

import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentStatus;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.warehouse.application.document.WarehouseTransferDocumentProcessor;
import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.TransferDocumentSettlement;
import com.tmp.warehouse.domain.TransferReceiptSettlementItem;
import com.tmp.warehouse.domain.TransferSettlementOptimisticLockException;
import com.tmp.warehouse.domain.TransferSettlementState;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import com.tmp.warehouse.domain.repository.TransferDocumentSendAllocationRepository;
import com.tmp.warehouse.domain.repository.TransferDocumentSettlementRepository;
import com.tmp.warehouse.domain.repository.TransferReceiptSettlementItemRepository;
import com.tmp.warehouse.domain.repository.TransferTaskStateRepository;
import com.tmp.warehouse.domain.repository.WarehouseTransferDocumentRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Whole-document reject for POSTED Warehouse Transfer Documents (Stage 3.5.8.3).
 *
 * <p>No stock mutation and no continuation. Settlement becomes {@code RETURN_PENDING}/{@code
 * REJECTED}; source gets {@code RETURN_MATERIALS} task projection after commit.
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
                            TransferDocumentSettlement rejected =
                                    locked.markRejectedAndReturnPending(
                                            command.expectedOperationalRevision(),
                                            normalizedReason,
                                            now,
                                            rejectedBy);
                            settlements.markRejectedAndReturnPending(
                                    command.documentId(),
                                    command.expectedOperationalRevision(),
                                    rejected);
                            taskStates.clear(command.documentId());

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
                                    rejected.rejectionReason().orElse(null));
                        });
        if (result == null) {
            throw new IllegalStateException("Transfer document reject returned null");
        }
        return result;
    }

    public record RejectCommand(
            UUID documentId, long expectedOperationalRevision, String rejectionReason) {
        public RejectCommand {
            Objects.requireNonNull(documentId, "documentId");
        }
    }

    public record RejectResult(
            UUID documentId,
            String documentStatus,
            String settlementState,
            String decision,
            long operationalRevision,
            String rejectionReason) {
        public RejectResult {
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(documentStatus, "documentStatus");
            Objects.requireNonNull(settlementState, "settlementState");
        }
    }
}
