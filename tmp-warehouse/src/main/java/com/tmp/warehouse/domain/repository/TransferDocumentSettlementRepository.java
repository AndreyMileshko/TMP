package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.TransferDocumentSettlement;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Warehouse-internal persistence for Transfer Document post-send settlement (Stage 3.5.8.1).
 */
public interface TransferDocumentSettlementRepository {

    void insertAwaitingReceipt(TransferDocumentSettlement settlement);

    Optional<TransferDocumentSettlement> findByDocumentId(UUID documentId);

    /** Batch load keyed by document id. Missing keys omitted. */
    Map<UUID, TransferDocumentSettlement> findByDocumentIds(Collection<UUID> documentIds);

    /** {@code SELECT … FOR UPDATE} — serializes receive/reject/return decisions. */
    Optional<TransferDocumentSettlement> lockByDocumentId(UUID documentId);

    /**
     * Persists {@code AWAITING_RECEIPT → SETTLED} with {@code ACCEPTED} when {@code
     * expectedOperationalRevision} matches. Increments revision exactly once.
     */
    void markAcceptedAndSettled(
            UUID documentId, long expectedOperationalRevision, TransferDocumentSettlement updated);

    /**
     * Persists {@code AWAITING_RECEIPT → RETURN_PENDING} with {@code ACCEPTED} when {@code
     * expectedOperationalRevision} matches. Increments revision exactly once.
     */
    void markAcceptedAndReturnPending(
            UUID documentId, long expectedOperationalRevision, TransferDocumentSettlement updated);

    /**
     * Persists {@code AWAITING_RECEIPT → RETURN_PENDING} with {@code REJECTED} and rejection
     * metadata when revision/state match. Increments revision exactly once.
     */
    void markRejectedAndReturnPending(
            UUID documentId, long expectedOperationalRevision, TransferDocumentSettlement updated);

    /**
     * Persists {@code RETURN_PENDING → SETTLED} preserving decision/rejection metadata when revision
     * matches. Increments revision exactly once.
     */
    void markReturnedAndSettled(
            UUID documentId, long expectedOperationalRevision, TransferDocumentSettlement updated);
}
