package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.DemandTaskAssignment;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Warehouse-internal persistence for informational Demand supply-task workers (B3B-3C1).
 *
 * <p>Not exposed outside Warehouse. Take-in-work is an UPSERT (takeover allowed).
 */
public interface DemandTaskStateRepository {

    Optional<DemandTaskAssignment> findByDemandId(UUID demandId);

    /** Batch read keyed by demand id. Missing keys mean NEW (no worker). */
    Map<UUID, DemandTaskAssignment> findByDemandIds(Collection<UUID> demandIds);

    /**
     * UPSERT current worker. Another authorized user may overwrite without conflict.
     *
     * @return persisted assignment after upsert
     */
    DemandTaskAssignment takeInWork(UUID demandId, UUID workingUserId, Instant workingSince);

    void clear(UUID demandId);
}
