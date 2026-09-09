package com.tmp.production.domain.repository;

import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementId;
import com.tmp.production.domain.MaterialRequirementOptimisticLockException;
import java.util.Optional;

/** Production-owned persistence port for editable Material Requirements. */
public interface MaterialRequirementRepository {

    /**
     * Inserts or updates the requirement. On update, uses optimistic {@code version} and throws
     * {@link MaterialRequirementOptimisticLockException} on conflict.
     */
    MaterialRequirement save(MaterialRequirement requirement);

    Optional<MaterialRequirement> findById(MaterialRequirementId id);

    /**
     * Loads the requirement with a {@code SELECT … FOR UPDATE} header row lock so a concurrent
     * Submit is serialized (Stage 3.5.10). Must be called inside the caller's transaction; the lock
     * is held until that outer transaction commits.
     */
    Optional<MaterialRequirement> findByIdForUpdate(MaterialRequirementId id);

    /**
     * Persists a {@code DRAFT → SUBMITTED} transition (header only: status, submission metadata,
     * version increment) using optimistic {@code version}. Lines are unchanged. Must run inside the
     * caller's transaction.
     *
     * @throws MaterialRequirementOptimisticLockException on version conflict
     */
    MaterialRequirement markSubmitted(MaterialRequirement requirement);
}
