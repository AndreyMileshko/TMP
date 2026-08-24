package com.tmp.production.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.PermissionId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductionPermissionsTest {

    private static final Set<String> CANONICAL_IDS =
            Set.of(
                    "production.order.view",
                    "production.order.accept",
                    "production.materials.check",
                    "production.transfer.create",
                    "production.receipt.confirm",
                    "production.release.create",
                    "production.cancellation.create");

    @Test
    void registersExactlySevenProductionOwnedPermissionsWithoutDuplicates() {
        List<PermissionId> permissions = ProductionPermissions.all();
        assertEquals(7, permissions.size());
        assertEquals(7, new HashSet<>(permissions).size());
        assertEquals(CANONICAL_IDS, new HashSet<>(permissions.stream().map(PermissionId::value).toList()));
    }

    @Test
    void allPermissionIdsAreValidThreeSegmentProductionCodes() {
        for (PermissionId permissionId : ProductionPermissions.all()) {
            assertEquals(permissionId, PermissionId.of(permissionId.value()));
            String[] segments = permissionId.value().split("\\.");
            assertEquals(3, segments.length);
            assertEquals("production", segments[0]);
            assertFalse(permissionId.value().contains("_"));
        }
    }

    @Test
    void constantsMapToCanonicalRuntimePermissionIds() {
        assertEquals("production.order.view", ProductionPermissions.PRODUCTION_VIEW.value());
        assertEquals("production.order.accept", ProductionPermissions.PRODUCTION_ACCEPT.value());
        assertEquals("production.materials.check", ProductionPermissions.PRODUCTION_CHECK_MATERIALS.value());
        assertEquals("production.transfer.create", ProductionPermissions.PRODUCTION_CREATE_TRANSFER.value());
        assertEquals("production.receipt.confirm", ProductionPermissions.PRODUCTION_CONFIRM_RECEIPT.value());
        assertEquals("production.release.create", ProductionPermissions.PRODUCTION_RELEASE.value());
        assertEquals("production.cancellation.create", ProductionPermissions.PRODUCTION_CANCEL.value());
    }

    @Test
    void legacyTwoSegmentShorthandIsNotRegistered() {
        assertThrows(IllegalArgumentException.class, () -> PermissionId.of("production.view"));
        assertThrows(IllegalArgumentException.class, () -> PermissionId.of("production.accept"));
        assertThrows(IllegalArgumentException.class, () -> PermissionId.of("production.release"));
        assertFalse(ProductionPermissionCatalog.containsCode("production.view"));
        assertFalse(ProductionPermissionCatalog.containsCode("production.accept"));
        assertFalse(ProductionPermissionCatalog.containsCode("production.release"));
    }
}
