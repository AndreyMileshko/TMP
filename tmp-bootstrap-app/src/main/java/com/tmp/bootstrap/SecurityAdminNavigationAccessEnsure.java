package com.tmp.bootstrap;

import com.tmp.core.api.EventBus;
import com.tmp.core.api.event.platform.PlatformStartedEvent;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.api.SystemRolePermissionEnsureService;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ensures Security Administrator retains all Security Administration permissions after catalogue
 * upgrades on existing databases.
 */
@Component
public final class SecurityAdminNavigationAccessEnsure {

    static final String SECURITY_ADMINISTRATOR_ROLE_NAME = "Security Administrator";

    private final EventBus eventBus;
    private final SystemRolePermissionEnsureService systemRolePermissionEnsureService;
    private final TransactionTemplate transactionTemplate;

    public SecurityAdminNavigationAccessEnsure(
            EventBus eventBus,
            SystemRolePermissionEnsureService systemRolePermissionEnsureService,
            PlatformTransactionManager transactionManager) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.systemRolePermissionEnsureService =
                Objects.requireNonNull(
                        systemRolePermissionEnsureService, "systemRolePermissionEnsureService");
        this.transactionTemplate =
                new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    @PostConstruct
    void subscribe() {
        eventBus.subscribePlatform(
                PlatformStartedEvent.class,
                event ->
                        transactionTemplate.executeWithoutResult(
                                status -> ensureSecurityAdministrationPermissions()));
    }

    void ensureSecurityAdministrationPermissions() {
        Set<PermissionId> permissions = new LinkedHashSet<>();
        permissions.add(SecurityPermissions.USERS_VIEW);
        permissions.add(SecurityPermissions.USERS_CREATE);
        permissions.add(SecurityPermissions.USERS_UPDATE);
        permissions.add(SecurityPermissions.USERS_DELETE);
        permissions.add(SecurityPermissions.USERS_RESET_PASSWORD);
        permissions.add(SecurityPermissions.ROLES_VIEW);
        permissions.add(SecurityPermissions.ROLES_CREATE);
        permissions.add(SecurityPermissions.ROLES_UPDATE);
        permissions.add(SecurityPermissions.ROLES_DELETE);
        permissions.add(SecurityPermissions.ROLES_ASSIGN);
        permissions.add(SecurityPermissions.PERMISSIONS_ASSIGN);
        permissions.add(SecurityPermissions.AUDIT_VIEW);
        systemRolePermissionEnsureService.ensurePermissions(
                SECURITY_ADMINISTRATOR_ROLE_NAME, permissions);
    }
}
