package com.tmp.bootstrap;

import com.tmp.core.api.EventBus;
import com.tmp.core.api.event.platform.PlatformStartedEvent;
import com.tmp.production.security.ProductionPermissions;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SystemRolePermissionEnsureService;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ensures Security Administrator receives all Production permissions after capability sync.
 *
 * <p>Production navigation is gated by {@code production.order.view}. Without role assignment the
 * system administrator cannot open Производство. Mirrors {@link WarehouseAdminNavigationAccessEnsure}.
 *
 * <p>Uses only Security public API and the existing Production permission catalogue. Does not create
 * permissions, change RBAC calculation, or alter Production domain logic.
 */
@Component
public final class ProductionAdminNavigationAccessEnsure {

    static final String SECURITY_ADMINISTRATOR_ROLE_NAME = "Security Administrator";

    private final EventBus eventBus;
    private final SystemRolePermissionEnsureService systemRolePermissionEnsureService;
    private final TransactionTemplate transactionTemplate;

    public ProductionAdminNavigationAccessEnsure(
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
                                status -> ensureProductionNavigationAccess()));
    }

    void ensureProductionNavigationAccess() {
        Set<PermissionId> permissions = new LinkedHashSet<>(ProductionPermissions.all());
        systemRolePermissionEnsureService.ensurePermissions(
                SECURITY_ADMINISTRATOR_ROLE_NAME, permissions);
    }
}
