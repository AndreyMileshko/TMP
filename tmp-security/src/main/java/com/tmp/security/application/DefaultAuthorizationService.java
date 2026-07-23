package com.tmp.security.application;

import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import java.util.Objects;
import java.util.Set;

public final class DefaultAuthorizationService implements AuthorizationService {

    private final AuthorizationApplicationService delegate;

    public DefaultAuthorizationService(AuthorizationApplicationService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public boolean hasPermission(PermissionId permissionId) {
        return delegate.hasPermission(permissionId);
    }

    @Override
    public void requirePermission(PermissionId permissionId) {
        delegate.requirePermission(permissionId);
    }

    @Override
    public Set<PermissionId> effectivePermissions() {
        return delegate.effectivePermissions();
    }
}
