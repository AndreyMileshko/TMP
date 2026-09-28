package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.EventBus;
import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SystemRolePermissionEnsureService;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;

class OrderAdminNavigationAccessEnsureTest {

    @Test
    void ensuresAllOrderPermissionsOnSecurityAdministratorViaPublicApi() {
        CapturingEnsure ensureService = new CapturingEnsure();
        OrderAdminNavigationAccessEnsure ensure =
                new OrderAdminNavigationAccessEnsure(
                        Mockito.mock(EventBus.class),
                        ensureService,
                        new PassthroughTransactionManager());

        ensure.ensureOrderNavigationAccess();

        assertEquals(
                OrderAdminNavigationAccessEnsure.SECURITY_ADMINISTRATOR_ROLE_NAME,
                ensureService.roleName);
        assertTrue(ensureService.permissions.contains(OrderManagementPermissions.ORDER_VIEW));
        assertTrue(ensureService.permissions.contains(OrderManagementPermissions.ORDER_CREATE));
        assertTrue(ensureService.permissions.contains(OrderManagementPermissions.ITEM_APPROVE));
        assertTrue(ensureService.permissions.contains(OrderManagementPermissions.REVISION_EDIT));
        assertEquals(OrderManagementPermissions.all().size(), ensureService.permissions.size());
        assertTrue(ensureService.permissions.containsAll(OrderManagementPermissions.all()));
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
