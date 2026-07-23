package com.tmp.security.domain.repository;

import com.tmp.security.api.PermissionId;
import com.tmp.security.domain.OptimisticLockConflictException;
import com.tmp.security.domain.PermissionDefinition;
import java.util.List;
import java.util.Optional;

/**
 * Domain port for registered permission definitions.
 */
public interface PermissionDefinitionRepository {

    /**
     * Inserts or updates. Throws {@link OptimisticLockConflictException} on version conflict.
     */
    PermissionDefinition save(PermissionDefinition definition);

    Optional<PermissionDefinition> findById(PermissionId permissionId);

    List<PermissionDefinition> findAll();
}
