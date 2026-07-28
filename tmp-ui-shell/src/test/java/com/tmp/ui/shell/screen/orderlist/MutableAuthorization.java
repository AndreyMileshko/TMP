package com.tmp.ui.shell.screen.orderlist;

import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import java.util.HashSet;
import java.util.Set;

final class MutableAuthorization implements AuthorizationService {

    private final Set<PermissionId> granted = new HashSet<>();

    void grant(PermissionId... permissions) {
        for (PermissionId permission : permissions) {
            granted.add(permission);
        }
    }

    @Override
    public boolean hasPermission(PermissionId permissionId) {
        return granted.contains(permissionId);
    }

    @Override
    public void requirePermission(PermissionId permissionId) {
        if (!hasPermission(permissionId)) {
            throw new com.tmp.security.api.AccessDeniedException(
                    "Access denied for permission: " + permissionId.value());
        }
    }

    @Override
    public Set<PermissionId> effectivePermissions() {
        return Set.copyOf(granted);
    }
}
