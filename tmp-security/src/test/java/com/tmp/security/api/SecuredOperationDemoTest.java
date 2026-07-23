package com.tmp.security.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecuredOperationDemoTest {

    private static final PermissionId VIEW = PermissionId.of("security.users.view");

    @Test
    void deniesWhenPermissionMissing() {
        SecuredOperationDemo demo = new SecuredOperationDemo(new FakeAuthz(Set.of()));
        assertThrows(AccessDeniedException.class, () -> demo.performSecuredOperation(VIEW));
    }

    @Test
    void allowsWhenPermissionGranted() {
        SecuredOperationDemo demo = new SecuredOperationDemo(new FakeAuthz(Set.of(VIEW)));
        assertEquals("OK", demo.performSecuredOperation(VIEW));
    }

    private static final class FakeAuthz implements AuthorizationService {
        private final Set<PermissionId> granted;

        private FakeAuthz(Set<PermissionId> granted) {
            this.granted = new HashSet<>(granted);
        }

        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return granted.contains(permissionId);
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
            if (!hasPermission(permissionId)) {
                throw new AccessDeniedException("Access denied for permission: " + permissionId.value());
            }
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.copyOf(granted);
        }
    }
}
