package com.tmp.security;

import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.component.ComponentType;
import com.tmp.core.api.component.PlatformComponent;
import com.tmp.core.api.component.PlatformComponentMetadata;
import com.tmp.security.application.BootstrapAdministratorApplicationService;
import com.tmp.security.application.PermissionSynchronizationApplicationService;

/**
 * Security Platform Component. On initialize: permission sync, then bootstrap administrator.
 */
final class SecurityPlatformComponent implements PlatformComponent {

    private static final PlatformComponentMetadata METADATA = new PlatformComponentMetadata(
            "security", "Security", "0.1.0-SNAPSHOT", ComponentType.SERVICE);

    private final PermissionSynchronizationApplicationService permissionSynchronization;
    private final BootstrapAdministratorApplicationService bootstrapAdministrator;

    SecurityPlatformComponent(
            PermissionSynchronizationApplicationService permissionSynchronization,
            BootstrapAdministratorApplicationService bootstrapAdministrator) {
        this.permissionSynchronization = permissionSynchronization;
        this.bootstrapAdministrator = bootstrapAdministrator;
    }

    @Override
    public PlatformComponentMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(PlatformCore platformCore) {
        permissionSynchronization.synchronize();
        bootstrapAdministrator.ensureBootstrapAdministrator();
    }

    @Override
    public void start() {
        // no-op
    }

    @Override
    public void stop() {
        // session cleanup is owned by UI/bootstrap shutdown hooks
    }
}
