package com.tmp.bootstrap;

import com.tmp.core.api.EventBus;
import com.tmp.core.api.event.platform.PlatformStartedEvent;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SystemRolePermissionEnsureService;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ensures Security Administrator receives all Reference Data permissions after capability sync.
 *
 * <p>Reference Data permissions ({@code reference.color.*}, {@code reference.color-mapping.edit},
 * {@code reference.color-setting.edit}) are contributed by Capability {@code reference}. Without role
 * assignment the system administrator cannot manage «Справочники → Цвета».
 *
 * <p>Mirrors {@link WarehouseAdminNavigationAccessEnsure}: uses only Security public API and stable
 * permission code strings (no compile-time dependency on {@code com.tmp.reference.security}). Does not
 * create roles, change RBAC calculation, or alter Reference Data domain logic.
 */
@Component
public final class ReferenceDataAdminNavigationAccessEnsure {

    static final String SECURITY_ADMINISTRATOR_ROLE_NAME = "Security Administrator";

    static final List<String> REFERENCE_DATA_PERMISSION_CODES =
            List.of(
                    "reference.color.view",
                    "reference.color.edit",
                    "reference.color-mapping.edit",
                    "reference.color-setting.edit");

    private final EventBus eventBus;
    private final SystemRolePermissionEnsureService systemRolePermissionEnsureService;
    private final TransactionTemplate transactionTemplate;

    public ReferenceDataAdminNavigationAccessEnsure(
            EventBus eventBus,
            SystemRolePermissionEnsureService systemRolePermissionEnsureService,
            PlatformTransactionManager transactionManager) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.systemRolePermissionEnsureService =
                Objects.requireNonNull(
                        systemRolePermissionEnsureService, "systemRolePermissionEnsureService");
        this.transactionTemplate =
                new TransactionTemplate(
                        Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    @PostConstruct
    void subscribe() {
        eventBus.subscribePlatform(
                PlatformStartedEvent.class,
                event ->
                        transactionTemplate.executeWithoutResult(
                                status -> ensureReferenceDataAccess()));
    }

    void ensureReferenceDataAccess() {
        Set<PermissionId> permissions = new LinkedHashSet<>();
        for (String code : REFERENCE_DATA_PERMISSION_CODES) {
            permissions.add(PermissionId.of(code));
        }
        systemRolePermissionEnsureService.ensurePermissions(
                SECURITY_ADMINISTRATOR_ROLE_NAME, permissions);
    }
}