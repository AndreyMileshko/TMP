package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.TransferTaskAssignment;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Warehouse-internal persistence for informational Transfer preparation task workers.
 *
 * <p>Not exposed outside Warehouse. Take-in-work is an UPSERT (takeover allowed).
 */
public interface TransferTaskStateRepository {

    Optional<TransferTaskAssignment> findByDocumentId(UUID documentId);

    /** Batch read keyed by document id. Missing keys mean NEW (no worker). */
    Map<UUID, TransferTaskAssignment> findByDocumentIds(Collection<UUID> documentIds);

    /**
     * UPSERT current worker. Another responsible user may overwrite without conflict.
     *
     * @return persisted assignment after upsert
     */
    TransferTaskAssignment takeInWork(UUID documentId, UUID workingUserId, Instant workingSince);

    void clear(UUID documentId);
}
