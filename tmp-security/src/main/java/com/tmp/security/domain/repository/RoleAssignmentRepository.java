package com.tmp.security.domain.repository;

import com.tmp.security.api.RoleId;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.RoleAssignment;
import java.util.List;
import java.util.Set;

/**
 * Domain port for user↔role assignments.
 */
public interface RoleAssignmentRepository {

    /** Idempotent: no-op if the pairing already exists. */
    void assign(RoleAssignment assignment);

    void revoke(UserId userId, RoleId roleId);

    Set<RoleId> findRoleIdsForUser(UserId userId);

    List<UserId> findUserIdsForRole(RoleId roleId);

    long countUsersForRole(RoleId roleId);
}
