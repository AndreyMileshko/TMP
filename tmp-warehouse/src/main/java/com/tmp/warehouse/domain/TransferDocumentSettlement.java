package com.tmp.warehouse.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Warehouse-owned post-send settlement header for a Transfer Document (Stage 3.5.8).
 *
 * <p>{@code operationalRevision} is the concurrency token for receive/reject/return decisions.
 * Rejection metadata columns exist for 3.5.8.3. Stage 3.5.8.2 transitions to {@link
 * TransferSettlementState#SETTLED} (full accept) or {@link TransferSettlementState#RETURN_PENDING}
 * (partial accept), both with {@link TransferSettlementDecision#ACCEPTED}.
 */
public final class TransferDocumentSettlement {

    private final UUID documentId;
    private final TransferSettlementState settlementState;
    private final long operationalRevision;
    private final TransferSettlementDecision decision;
    private final String rejectionReason;
    private final Instant rejectedAt;
    private final UUID rejectedBy;
    private final Instant createdAt;
    private final Instant updatedAt;

    private TransferDocumentSettlement(
            UUID documentId,
            TransferSettlementState settlementState,
            long operationalRevision,
            TransferSettlementDecision decision,
            String rejectionReason,
            Instant rejectedAt,
            UUID rejectedBy,
            Instant createdAt,
            Instant updatedAt) {
        this.documentId = documentId;
        this.settlementState = settlementState;
        this.operationalRevision = operationalRevision;
        this.decision = decision;
        this.rejectionReason = rejectionReason;
        this.rejectedAt = rejectedAt;
        this.rejectedBy = rejectedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static TransferDocumentSettlement awaitingReceipt(
            UUID documentId, Instant createdAt) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(createdAt, "createdAt");
        return new TransferDocumentSettlement(
                documentId,
                TransferSettlementState.AWAITING_RECEIPT,
                0L,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt);
    }

    public static TransferDocumentSettlement of(
            UUID documentId,
            TransferSettlementState settlementState,
            long operationalRevision,
            TransferSettlementDecision decision,
            String rejectionReason,
            Instant rejectedAt,
            UUID rejectedBy,
            Instant createdAt,
            Instant updatedAt) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(settlementState, "settlementState");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (operationalRevision < 0) {
            throw new IllegalArgumentException(
                    "operationalRevision must not be negative: " + operationalRevision);
        }
        validateRejectionMetadata(decision, rejectionReason, rejectedAt, rejectedBy);
        return new TransferDocumentSettlement(
                documentId,
                settlementState,
                operationalRevision,
                decision,
                rejectionReason,
                rejectedAt,
                rejectedBy,
                createdAt,
                updatedAt);
    }

    public TransferDocumentSettlement markAcceptedAndSettled(
            long expectedRevision, Instant updatedAt) {
        return markAccepted(TransferSettlementState.SETTLED, expectedRevision, updatedAt);
    }

    /**
     * Partial acceptance: {@code AWAITING_RECEIPT → RETURN_PENDING} with {@code ACCEPTED}. Outstanding
     * IN_TRANSIT quantity remains for Stage 3.5.8.3 physical return.
     */
    public TransferDocumentSettlement markAcceptedAndReturnPending(
            long expectedRevision, Instant updatedAt) {
        return markAccepted(TransferSettlementState.RETURN_PENDING, expectedRevision, updatedAt);
    }

    private TransferDocumentSettlement markAccepted(
            TransferSettlementState targetState, long expectedRevision, Instant updatedAt) {
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(targetState, "targetState");
        if (expectedRevision != operationalRevision) {
            throw new TransferSettlementOptimisticLockException(
                    documentId, expectedRevision, operationalRevision);
        }
        if (settlementState != TransferSettlementState.AWAITING_RECEIPT) {
            throw new InvalidWarehouseStateException(
                    "Settlement accept requires AWAITING_RECEIPT: documentId="
                            + documentId
                            + ", state="
                            + settlementState);
        }
        if (decision != null) {
            throw new InvalidWarehouseStateException(
                    "Settlement already decided: documentId=" + documentId + ", decision=" + decision);
        }
        return of(
                documentId,
                targetState,
                operationalRevision + 1,
                TransferSettlementDecision.ACCEPTED,
                null,
                null,
                null,
                createdAt,
                updatedAt);
    }

    private static void validateRejectionMetadata(
            TransferSettlementDecision decision,
            String rejectionReason,
            Instant rejectedAt,
            UUID rejectedBy) {
        if (decision == TransferSettlementDecision.REJECTED) {
            if (rejectionReason == null || rejectionReason.isBlank()) {
                throw new InvalidWarehouseStateException(
                        "REJECTED settlement requires non-blank rejectionReason");
            }
            if (rejectedAt == null || rejectedBy == null) {
                throw new InvalidWarehouseStateException(
                        "REJECTED settlement requires rejectedAt and rejectedBy");
            }
            return;
        }
        if (rejectionReason != null || rejectedAt != null || rejectedBy != null) {
            throw new InvalidWarehouseStateException(
                    "Rejection metadata is allowed only for REJECTED decision");
        }
    }

    public UUID documentId() {
        return documentId;
    }

    public TransferSettlementState settlementState() {
        return settlementState;
    }

    public long operationalRevision() {
        return operationalRevision;
    }

    public Optional<TransferSettlementDecision> decision() {
        return Optional.ofNullable(decision);
    }

    public Optional<String> rejectionReason() {
        return Optional.ofNullable(rejectionReason);
    }

    public Optional<Instant> rejectedAt() {
        return Optional.ofNullable(rejectedAt);
    }

    public Optional<UUID> rejectedBy() {
        return Optional.ofNullable(rejectedBy);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
