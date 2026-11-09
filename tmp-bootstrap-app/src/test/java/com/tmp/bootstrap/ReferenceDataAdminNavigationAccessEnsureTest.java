package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.EventBus;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SystemRolePermissionEnsureService;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ReferenceDataAdminNavigationAccessEnsureTest {

    @Test
    void ensuresAllReferenceDataPermissionsOnSecurityAdministratorViaPublicApi() {
        CapturingEnsure ensureService = new CapturingEnsure();
        ReferenceDataAdminNavigationAccessEnsure ensure =
                new ReferenceDataAdminNavigationAccessEnsure(
                        Mockito.mock(EventBus.class),
                        ensureService,
                        new PassthroughTransactionManager());

        ensure.ensureReferenceDataAccess();

        assertEquals(
                ReferenceDataAdminNavigationAccessEnsure.SECURITY_ADMINISTRATOR_ROLE_NAME,
                ensureService.roleName);
        Set<String> codes =
                ensureService.permissions.stream()
                        .map(PermissionId::value)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(
                new LinkedHashSet<>(
                        ReferenceDataAdminNavigationAccessEnsure.REFERENCE_DATA_PERMISSION_CODES),
                codes);
        assertTrue(codes.contains("reference.color.view"));
        assertTrue(codes.contains("reference.color.edit"));
        assertTrue(codes.contains("reference.color-mapping.edit"));
        assertTrue(codes.contains("reference.color-setting.edit"));
        assertEquals(4, codes.size());
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