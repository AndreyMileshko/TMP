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
}
