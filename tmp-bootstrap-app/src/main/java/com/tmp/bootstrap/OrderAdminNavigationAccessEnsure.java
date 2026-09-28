package com.tmp.bootstrap;

import com.tmp.core.api.EventBus;
import com.tmp.core.api.event.platform.PlatformStartedEvent;
import com.tmp.order.capability.OrderManagementPermissions;
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
 * Ensures Security Administrator receives all Order Management permissions after capability sync.
 *
 * <p>Order navigation and screens are gated by Order catalogue permissions. Without role assignment
 * the system administrator cannot open Заказы. Mirrors {@link WarehouseAdminNavigationAccessEnsure}.
 *
 * <p>Uses only Security public API and the existing Order permission catalogue. Does not create
 * permissions, change RBAC calculation, or alter Order domain logic.
 */
@Component
public final class OrderAdminNavigationAccessEnsure {

    static final String SECURITY_ADMINISTRATOR_ROLE_NAME = "Security Administrator";

    private final EventBus eventBus;
    private final SystemRolePermissionEnsureService systemRolePermissionEnsureService;
    private final TransactionTemplate transactionTemplate;

    public OrderAdminNavigationAccessEnsure(
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
                                status -> ensureOrderNavigationAccess()));
    }

    void ensureOrderNavigationAccess() {
        Set<PermissionId> permissions = new LinkedHashSet<>(OrderManagementPermissions.all());
        systemRolePermissionEnsureService.ensurePermissions(
                SECURITY_ADMINISTRATOR_ROLE_NAME, permissions);
    }
}
