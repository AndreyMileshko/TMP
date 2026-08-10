package com.tmp.bootstrap;

import com.tmp.core.api.EventBus;
import com.tmp.core.api.event.platform.PlatformStartedEvent;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SystemRolePermissionEnsureService;
import com.tmp.ui.shell.UiShellScreens;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ensures Security Administrator can see Warehouse navigation after Stage 6 capability sync.
 *
 * <p>Warehouse navigation ({@code warehouse.nav.workbench}) is contributed by WarehouseCapability and
 * gated by {@code warehouse.stock.view}. Screen registration and Capability navigation metadata are
 * present; without role assignment the main menu hides Warehouse.
 *
 * <p>Subscribes to {@link PlatformStartedEvent} so Security permission sync and bootstrap have already
 * completed. Uses only Security public API. Does not change Warehouse domain/API or schema.
 */
@Component
public final class WarehouseAdminNavigationAccessEnsure {

    static final String SECURITY_ADMINISTRATOR_ROLE_NAME = "Security Administrator";

    private static final List<String> WAREHOUSE_PERMISSION_CODES =
            List.of(
                    UiShellScreens.WAREHOUSE_VIEW_PERMISSION,
                    UiShellScreens.WAREHOUSE_RECEIPT_PERMISSION,
                    UiShellScreens.WAREHOUSE_MOVE_PERMISSION,
                    UiShellScreens.WAREHOUSE_TRANSFER_PERMISSION,
                    UiShellScreens.WAREHOUSE_RESERVATION_PERMISSION,
                    UiShellScreens.WAREHOUSE_CONSUMPTION_PERMISSION,
                    UiShellScreens.WAREHOUSE_ADJUSTMENT_PERMISSION,
                    "warehouse.inventory.create",
                    UiShellScreens.WAREHOUSE_STRUCTURE_VIEW_PERMISSION,
                    UiShellScreens.WAREHOUSE_STRUCTURE_CREATE_PERMISSION,
                    UiShellScreens.WAREHOUSE_STRUCTURE_UPDATE_PERMISSION,
                    UiShellScreens.WAREHOUSE_STRUCTURE_DELETE_PERMISSION,
                    UiShellScreens.WAREHOUSE_STORAGE_CELL_VIEW_PERMISSION,
                    UiShellScreens.WAREHOUSE_STORAGE_CELL_CREATE_PERMISSION,
                    UiShellScreens.WAREHOUSE_STORAGE_CELL_UPDATE_PERMISSION,
                    UiShellScreens.WAREHOUSE_STORAGE_CELL_DELETE_PERMISSION);

    private final EventBus eventBus;
    private final SystemRolePermissionEnsureService systemRolePermissionEnsureService;
    private final TransactionTemplate transactionTemplate;

    public WarehouseAdminNavigationAccessEnsure(
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
                                status -> ensureWarehouseNavigationAccess()));
    }

    void ensureWarehouseNavigationAccess() {
        Set<PermissionId> permissions = new LinkedHashSet<>();
        for (String code : WAREHOUSE_PERMISSION_CODES) {
            permissions.add(PermissionId.of(code));
        }
        systemRolePermissionEnsureService.ensurePermissions(
                SECURITY_ADMINISTRATOR_ROLE_NAME, permissions);
    }
}
