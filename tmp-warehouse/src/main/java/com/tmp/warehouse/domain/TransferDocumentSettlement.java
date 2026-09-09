package com.tmp.warehouse.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Warehouse-owned post-send settlement header for a Transfer Document (Stage 3.5.8).
 *
 * <p>{@code operationalRevision} is the concurrency token for receive/reject/return decisions.
 * Stage 3.5.8.3 adds full reject ({@link TransferSettlementDecision#REJECTED} + {@link
 * TransferSettlementState#RETURN_PENDING}) and physical return ({@link
 * TransferSettlementState#SETTLED} preserving decision/rejection metadata).
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

    /**
     * Full reject: {@code AWAITING_RECEIPT → RETURN_PENDING} with {@code REJECTED}. Physical stock
     * is unchanged; no continuation is created.
     */
    public TransferDocumentSettlement markRejectedAndReturnPending(
            long expectedRevision,
            String rejectionReason,
            Instant rejectedAt,
            UUID rejectedBy) {
        Objects.requireNonNull(rejectedAt, "rejectedAt");
        Objects.requireNonNull(rejectedBy, "rejectedBy");
        if (expectedRevision != operationalRevision) {
            throw new TransferSettlementOptimisticLockException(
                    documentId, expectedRevision, operationalRevision);
        }
        if (settlementState != TransferSettlementState.AWAITING_RECEIPT) {
            throw new InvalidWarehouseStateException(
                    "Settlement reject requires AWAITING_RECEIPT: documentId="
                            + documentId
                            + ", state="
                            + settlementState);
        }
        if (decision != null) {
            throw new InvalidWarehouseStateException(
                    "Settlement already decided: documentId=" + documentId + ", decision=" + decision);
        }
        String normalizedReason = requireNormalizedRejectionReason(rejectionReason);
        return of(
                documentId,
                TransferSettlementState.RETURN_PENDING,
                operationalRevision + 1,
                TransferSettlementDecision.REJECTED,
                normalizedReason,
                rejectedAt,
                rejectedBy,
                createdAt,
                rejectedAt);
    }

    /**
     * Physical return complete: {@code RETURN_PENDING → SETTLED}. Preserves {@code ACCEPTED} or
     * {@code REJECTED} decision and rejection metadata.
     */
    public TransferDocumentSettlement markReturnedAndSettled(
            long expectedRevision, Instant updatedAt) {
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (expectedRevision != operationalRevision) {
            throw new TransferSettlementOptimisticLockException(
                    documentId, expectedRevision, operationalRevision);
        }
        if (settlementState != TransferSettlementState.RETURN_PENDING) {
            throw new InvalidWarehouseStateException(
                    "Settlement return requires RETURN_PENDING: documentId="
                            + documentId
                            + ", state="
                            + settlementState);
        }
        if (decision != TransferSettlementDecision.ACCEPTED
                && decision != TransferSettlementDecision.REJECTED) {
            throw new InvalidWarehouseStateException(
                    "Settlement return requires ACCEPTED or REJECTED decision: documentId="
                            + documentId
                            + ", decision="
                            + decision);
        }
        return of(
                documentId,
                TransferSettlementState.SETTLED,
                operationalRevision + 1,
                decision,
                rejectionReason,
                rejectedAt,
                rejectedBy,
                createdAt,
                updatedAt);
    }

    public static String requireNormalizedRejectionReason(String rejectionReason) {
        if (rejectionReason == null) {
            throw new InvalidWarehouseStateException("Rejection reason is required");
        }
        String trimmed = rejectionReason.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidWarehouseStateException("Rejection reason must not be blank");
        }
        if (trimmed.length() > 500) {
            throw new InvalidWarehouseStateException(
                    "Rejection reason must be at most 500 characters");
        }
        return trimmed;
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
