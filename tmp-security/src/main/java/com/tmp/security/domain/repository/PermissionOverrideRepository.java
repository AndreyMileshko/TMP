package com.tmp.security.domain.repository;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.IndividualPermissionOverride;
import com.tmp.security.domain.OptimisticLockConflictException;
import java.util.List;
import java.util.Optional;

/**
 * Domain port for individual permission overrides.
 */
public interface PermissionOverrideRepository {

    /**
     * Inserts or updates by natural key. Throws {@link OptimisticLockConflictException} on conflict.
     */
    IndividualPermissionOverride save(IndividualPermissionOverride override);

    void remove(UserId userId, PermissionId permissionId);

    List<IndividualPermissionOverride> findByUser(UserId userId);

    Optional<IndividualPermissionOverride> findByUserAndPermission(UserId userId, PermissionId permissionId);
}
