package com.tmp.ui.shell.screen.orderlist;

import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import java.util.HashSet;
import java.util.Set;

public final class FakeAuthorization implements AuthorizationService {

    private final Set<PermissionId> granted;

    public FakeAuthorization(PermissionId... granted) {
        this.granted = new HashSet<>();
        for (PermissionId id : granted) {
            this.granted.add(id);
        }
    }

    public FakeAuthorization(Set<PermissionId> granted) {
        this.granted = new HashSet<>(granted);
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
