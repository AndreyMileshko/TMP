package com.tmp.security.api;

import java.util.Set;

/**
 * Public authorization API. Final check before any protected operation.
 */
public interface AuthorizationService {

    boolean hasPermission(PermissionId permissionId);

    void requirePermission(PermissionId permissionId);

    Set<PermissionId> effectivePermissions();
}
