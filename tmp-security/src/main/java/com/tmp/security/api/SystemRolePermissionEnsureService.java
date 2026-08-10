package com.tmp.security.api;

import java.util.Set;

/**
 * System-level ensure of role permission assignments (no interactive authorization).
 *
 * <p>Intended for composition-root / platform startup so newly contributed Capability navigation
 * permissions can be granted to the Security Administrator role template without going through the
 * interactive {@link RoleAdministrationService} authorization path.
 */
public interface SystemRolePermissionEnsureService {

    /**
     * Grants any missing permissions from {@code permissionIds} to the role with the given name.
     * No-op when the role does not exist. Idempotent.
     */
    void ensurePermissions(String roleName, Set<PermissionId> permissionIds);
}
