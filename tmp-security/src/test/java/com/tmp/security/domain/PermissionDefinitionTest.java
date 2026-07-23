package com.tmp.security.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.PermissionId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PermissionDefinitionTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);
    private static final PermissionId ID = PermissionId.of("security.users.view");

    @Test
    void registerStartsActive() {
        PermissionDefinition def = PermissionDefinition.register(ID, "test.capability", "View users", "desc", CLOCK);
        assertTrue(def.active());
        assertEquals(ID, def.permissionId());
    }

    @Test
    void activateDeactivateIdempotent() {
        PermissionDefinition active = PermissionDefinition.register(ID, "test.capability", "View", "", CLOCK);
        PermissionDefinition inactive = active.deactivated();
        assertFalse(inactive.active());
        assertEquals(ID, inactive.permissionId());
        assertSame(inactive, inactive.deactivated());
        assertSame(active, active.activated());
        assertTrue(inactive.activated().active());
    }

    @Test
    void claimLegacyOwnershipTransfersOwnerOnce() {
        PermissionDefinition legacy = PermissionDefinition.rehydrate(
                ID,
                PermissionDefinition.LEGACY_UNASSIGNED_OWNER,
                "View",
                "",
                true,
                CLOCK.instant(),
                0L);
        PermissionDefinition claimed = legacy.claimLegacyOwnership("security-administration");
        assertEquals("security-administration", claimed.ownerCapabilityId());
        assertEquals(ID, claimed.permissionId());
    }

    @Test
    void claimLegacyOwnershipRejectedWhenAlreadyOwned() {
        PermissionDefinition owned = PermissionDefinition.register(ID, "security-administration", "View", "", CLOCK);
        assertThrows(
                PermissionOwnershipConflictException.class,
                () -> owned.claimLegacyOwnership("other.capability"));
    }

    @Test
    void metadataChangeKeepsPermissionId() {
        PermissionDefinition original = PermissionDefinition.register(ID, "test.capability", "View", "a", CLOCK);
        PermissionDefinition renamed = original.withDisplayName("View users").withDescription("b");
        assertEquals(ID, renamed.permissionId());
        assertEquals("View users", renamed.displayName());
        assertEquals("b", renamed.description());
        assertEquals("View", original.displayName());
    }
}
