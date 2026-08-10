package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.EventBus;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SystemRolePermissionEnsureService;
import com.tmp.ui.shell.UiShellScreens;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;

class WarehouseAdminNavigationAccessEnsureTest {

    @Test
    void ensuresWarehousePermissionsOnSecurityAdministratorViaPublicApi() {
        CapturingEnsure ensureService = new CapturingEnsure();
        WarehouseAdminNavigationAccessEnsure ensure =
                new WarehouseAdminNavigationAccessEnsure(
                        Mockito.mock(EventBus.class),
                        ensureService,
                        new PassthroughTransactionManager());

        ensure.ensureWarehouseNavigationAccess();

        assertEquals(
                WarehouseAdminNavigationAccessEnsure.SECURITY_ADMINISTRATOR_ROLE_NAME,
                ensureService.roleName);
        assertTrue(ensureService.permissions.contains(
                PermissionId.of(UiShellScreens.WAREHOUSE_VIEW_PERMISSION)));
        assertTrue(ensureService.permissions.contains(PermissionId.of("warehouse.inventory.create")));
        assertEquals(8, ensureService.permissions.size());
    }

    private static final class CapturingEnsure implements SystemRolePermissionEnsureService {
        private String roleName;
        private Set<PermissionId> permissions = Set.of();

        @Override
        public void ensurePermissions(String roleName, Set<PermissionId> permissionIds) {
            this.roleName = roleName;
            this.permissions = new LinkedHashSet<>(permissionIds);
        }
    }

    private static final class PassthroughTransactionManager implements PlatformTransactionManager {
        @Override
        public org.springframework.transaction.TransactionStatus getTransaction(
                TransactionDefinition definition) throws TransactionException {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(org.springframework.transaction.TransactionStatus status)
                throws TransactionException {}

        @Override
        public void rollback(org.springframework.transaction.TransactionStatus status)
                throws TransactionException {}
    }
}
