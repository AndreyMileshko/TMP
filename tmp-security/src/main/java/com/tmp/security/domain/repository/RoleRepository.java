package com.tmp.security.domain.repository;

import com.tmp.security.api.RoleId;
import com.tmp.security.domain.OptimisticLockConflictException;
import com.tmp.security.domain.Role;
import java.util.List;
import java.util.Optional;

/**
 * Domain port for Security role persistence.
 */
public interface RoleRepository {

    /**
     * Inserts or updates the role. Throws {@link OptimisticLockConflictException} on version conflict.
     */
    Role save(Role role);

    Optional<Role> findById(RoleId id);

    List<Role> findAll();

    void deleteById(RoleId id);
}
