package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.EventBus;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.api.SystemRolePermissionEnsureService;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class SecurityAdminNavigationAccessEnsureTest {

    @Test
    void ensuresAllSecurityAdministrationPermissions() {
        CapturingEnsure capture = new CapturingEnsure();
        SecurityAdminNavigationAccessEnsure ensure =
                new SecurityAdminNavigationAccessEnsure(
                        Mockito.mock(EventBus.class),
                        capture,
                        new PassthroughTransactionManager());
        ensure.ensureSecurityAdministrationPermissions();
        assertEquals(
                SecurityAdminNavigationAccessEnsure.SECURITY_ADMINISTRATOR_ROLE_NAME,
                capture.roleName);
        assertTrue(capture.permissionIds.containsAll(Set.of(
                SecurityPermissions.USERS_VIEW,
                SecurityPermissions.USERS_CREATE,
                SecurityPermissions.USERS_UPDATE,
                SecurityPermissions.USERS_DELETE,
                SecurityPermissions.USERS_RESET_PASSWORD,
                SecurityPermissions.ROLES_VIEW,
                SecurityPermissions.ROLES_CREATE,
                SecurityPermissions.ROLES_UPDATE,
                SecurityPermissions.ROLES_DELETE,
                SecurityPermissions.ROLES_ASSIGN,
                SecurityPermissions.PERMISSIONS_ASSIGN,
                SecurityPermissions.AUDIT_VIEW)));
        assertEquals(12, capture.permissionIds.size());
    }

    private static final class CapturingEnsure implements SystemRolePermissionEnsureService {

        private String roleName;
        private Set<PermissionId> permissionIds = Set.of();

        @Override
        public void ensurePermissions(String roleName, Set<PermissionId> permissionIds) {
            this.roleName = roleName;
            this.permissionIds = permissionIds.stream().collect(Collectors.toUnmodifiableSet());
        }
    }

    private static final class PassthroughTransactionManager implements PlatformTransactionManager {
        @Override
        public org.springframework.transaction.TransactionStatus getTransaction(
                org.springframework.transaction.TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(org.springframework.transaction.TransactionStatus status) {}

        @Override
        public void rollback(org.springframework.transaction.TransactionStatus status) {}
    }
}
